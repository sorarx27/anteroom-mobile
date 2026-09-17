package com.zayedmd.anteroom.data

import com.zayedmd.anteroom.firebase.FirebaseService
import com.zayedmd.anteroom.model.Brief
import com.zayedmd.anteroom.model.BriefLanguage
import com.zayedmd.anteroom.model.BriefPhoto
import com.zayedmd.anteroom.model.BriefStatus
import com.zayedmd.anteroom.model.DocType
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.Query
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable

interface BriefsRepository {
    suspend fun getBriefs(profileId: String?): List<Brief>
    suspend fun getBrief(briefId: String): Brief?
    suspend fun createBrief(userId: String, profileId: String, docType: DocType = DocType.other): Brief
    suspend fun updateBriefDocType(briefId: String, docType: DocType): Brief?
    suspend fun addPhotoToBrief(briefId: String, photo: BriefPhoto): Brief?
    suspend fun removePhotoFromBrief(briefId: String, photoId: String): Brief?
    suspend fun deleteBrief(briefId: String)
    suspend fun detectDocType(briefId: String): DocType
    suspend fun generateBrief(briefId: String, sourceLanguage: BriefLanguage): Brief
    suspend fun translateBrief(briefId: String, targetLanguage: BriefLanguage): Brief
}

// Callable payloads. Snake_case to match the Python side exactly; the Kotlin
// models are snake_case too, so no @SerialName indirection is needed.
@Serializable
private data class ClassifyRequest(val brief_id: String)

@Serializable
private data class GenerateRequest(val brief_id: String, val source_language: String)

@Serializable
private data class TranslateRequest(val brief_id: String, val target_language: String)

/**
 * Firestore-backed briefs, stored flat at `briefs/{briefId}` with `user_id`
 * and `profile_id` fields.
 *
 * Two structural rules govern this class.
 *
 * Clinical fields — `content`, `status`, `generated_at`, the share token —
 * are never written here. Security rules reject them from any client, and
 * Cloud Functions own them. That is what makes "nothing invented" a property
 * of the system rather than a promise about the UI.
 *
 * And nothing is silently swallowed. The previous implementation wrapped
 * every Firestore call in `catch { }` and fell back to `MockData`, so a
 * permission error, an offline device and an empty account were
 * indistinguishable from success. Failures now propagate.
 */
class BriefsRepositoryImpl : BriefsRepository {

    private val briefs get() = FirebaseService.firestore.collection("briefs")

    // Extraction over several page images routinely outruns the 70s default.
    private val generateFn by lazy {
        FirebaseService.functions.httpsCallable("generateBrief", timeout = 300.seconds)
    }
    private val translateFn by lazy {
        FirebaseService.functions.httpsCallable("translateBrief", timeout = 120.seconds)
    }
    private val classifyFn by lazy {
        FirebaseService.functions.httpsCallable("classifyDocType", timeout = 90.seconds)
    }

    override suspend fun getBriefs(profileId: String?): List<Brief> {
        val uid = requireUid()
        // The user_id predicate is not optional. Security rules reject any
        // query they cannot prove is owner-scoped, and without it this read
        // returned every user's briefs for a colliding profile_id — which,
        // now that the self profile is the literal id "self" for everybody,
        // would have been every user's briefs to every user.
        var query: Query = briefs.where { "user_id" equalTo uid }
        if (profileId != null) {
            query = query.where { "profile_id" equalTo profileId }
        }
        return query
            .orderBy("created_at", Direction.DESCENDING)
            .get()
            .documents
            .map { it.data<Brief>() }
    }

    override suspend fun getBrief(briefId: String): Brief? {
        val doc = briefs.document(briefId).get()
        return if (doc.exists) doc.data<Brief>() else null
    }

    override suspend fun createBrief(userId: String, profileId: String, docType: DocType): Brief {
        // Firestore mints the id. The old client-side random over a 900k
        // space was a genuine collision surface, and because that document
        // was never actually written, every later update() threw into a
        // swallowed catch and no photo ever persisted.
        val ref = briefs.document
        val now = nowIso()
        val brief = Brief(
            brief_id = ref.id,
            user_id = userId,
            profile_id = profileId,
            doc_type = docType,
            status = BriefStatus.draft,
            created_at = now,
            updated_at = now
        )
        ref.set(brief)
        return brief
    }

    override suspend fun updateBriefDocType(briefId: String, docType: DocType): Brief? {
        val existing = getBrief(briefId) ?: return null
        val updated = existing.copy(
            doc_type = docType,
            doc_type_manual_override = true,
            updated_at = nowIso()
        )
        briefs.document(briefId).set(updated, *DOC_TYPE_FIELDS)
        return updated
    }

    override suspend fun addPhotoToBrief(briefId: String, photo: BriefPhoto): Brief? {
        val existing = getBrief(briefId) ?: return null
        val updated = existing.copy(
            photos = existing.photos + photo,
            updated_at = nowIso()
        )
        briefs.document(briefId).set(updated, *PHOTO_FIELDS)
        return updated
    }

    override suspend fun removePhotoFromBrief(briefId: String, photoId: String): Brief? {
        val existing = getBrief(briefId) ?: return null
        val updated = existing.copy(
            photos = existing.photos.filter { it.photo_id != photoId },
            updated_at = nowIso()
        )
        briefs.document(briefId).set(updated, *PHOTO_FIELDS)
        return updated
    }

    override suspend fun deleteBrief(briefId: String) {
        briefs.document(briefId).delete()
    }

    // ---------------------------------------------------------------------
    // Cloud Functions. Each one writes its results into Firestore with the
    // Admin SDK and the client re-reads, so the document schema is owned in
    // one place (Python) rather than mirrored in a Kotlin response model that
    // could drift. The extra read is noise next to a model call.
    // ---------------------------------------------------------------------

    override suspend fun detectDocType(briefId: String): DocType {
        classifyFn.invoke(ClassifyRequest.serializer(), ClassifyRequest(briefId))
        val refreshed = getBrief(briefId)
        return refreshed?.detected_doc_type ?: refreshed?.doc_type ?: DocType.other
    }

    override suspend fun generateBrief(briefId: String, sourceLanguage: BriefLanguage): Brief {
        // `sourceLanguage` records what language the documents are in and
        // which labels the PDF uses. It deliberately cannot change what the
        // model outputs: the extraction prompt forbids translating, because
        // copying dosages verbatim is the safety guarantee. Getting a Spanish
        // brief is an explicit, separate translate step.
        generateFn.invoke(
            GenerateRequest.serializer(),
            GenerateRequest(briefId, sourceLanguage.key)
        )
        return getBrief(briefId)
            ?: throw IllegalStateException("Brief disappeared during generation")
    }

    override suspend fun translateBrief(briefId: String, targetLanguage: BriefLanguage): Brief {
        translateFn.invoke(
            TranslateRequest.serializer(),
            TranslateRequest(briefId, targetLanguage.key)
        )
        return getBrief(briefId)
            ?: throw IllegalStateException("Brief disappeared during translation")
    }

    private companion object {
        /**
         * Field lists for merge writes.
         *
         * Every mutation here is a merge rather than a whole-document `set`.
         * A full set re-serialises fields the client only ever read — most of
         * all `content`, which a Cloud Function wrote — and the update rule
         * rejects any write whose diff touches a server-owned key. Round-trips
         * through the Kotlin model are not guaranteed to be byte-identical
         * (a field Python writes but the model lacks would silently vanish on
         * the way back), so the safe thing is never to send them at all.
         */
        val DOC_TYPE_FIELDS = arrayOf("doc_type", "doc_type_manual_override", "updated_at")
        val PHOTO_FIELDS = arrayOf("photos", "updated_at")
    }
}
