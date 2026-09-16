package com.zayedmd.anteroom.data

import com.zayedmd.anteroom.firebase.FirebaseService
import com.zayedmd.anteroom.model.*
import kotlinx.coroutines.flow.MutableStateFlow

interface BriefsRepository {
    suspend fun getBriefs(profileId: String?): List<Brief>
    suspend fun getBrief(briefId: String): Brief?
    suspend fun createBrief(userId: String, profileId: String, docType: DocType = DocType.other): Brief
    suspend fun updateBriefDocType(briefId: String, docType: DocType): Brief?
    suspend fun addPhotoToBrief(briefId: String, photo: BriefPhoto): Brief?
    suspend fun removePhotoFromBrief(briefId: String, photoId: String): Brief?
    suspend fun deleteBrief(briefId: String)
    suspend fun detectDocType(briefId: String): DocType
    suspend fun generateBrief(briefId: String, outputLanguage: BriefLanguage): Brief
    suspend fun translateBrief(briefId: String, targetLanguage: BriefLanguage): Brief
}

class BriefsRepositoryImpl : BriefsRepository {
    private val memoryBriefs = MutableStateFlow<List<Brief>>(MockData.sampleBriefs)

    override suspend fun getBriefs(profileId: String?): List<Brief> {
        return try {
            val query = if (profileId != null) {
                FirebaseService.firestore
                    .collection("briefs")
                    .where { "profile_id" equalTo profileId }
            } else {
                FirebaseService.firestore.collection("briefs")
            }
            val snapshot = query.get()
            if (snapshot.documents.isNotEmpty()) {
                snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.data<Brief>()
                    } catch (e: Exception) {
                        null
                    }
                }
            } else {
                filterMemoryBriefs(profileId)
            }
        } catch (e: Exception) {
            filterMemoryBriefs(profileId)
        }
    }

    private fun filterMemoryBriefs(profileId: String?): List<Brief> {
        return if (profileId != null) {
            memoryBriefs.value.filter { it.profile_id == profileId }
        } else {
            memoryBriefs.value
        }
    }

    override suspend fun getBrief(briefId: String): Brief? {
        return memoryBriefs.value.find { it.brief_id == briefId }
    }

    override suspend fun createBrief(userId: String, profileId: String, docType: DocType): Brief {
        val newId = "br_${kotlin.random.Random.nextInt(100000, 999999)}"
        val newBrief = Brief(
            brief_id = newId,
            user_id = userId,
            profile_id = profileId,
            doc_type = docType,
            status = BriefStatus.draft,
            created_at = "2026-09-16T12:00:00Z",
            updated_at = "2026-09-16T12:00:00Z"
        )
        memoryBriefs.value = listOf(newBrief) + memoryBriefs.value
        return newBrief
    }

    override suspend fun updateBriefDocType(briefId: String, docType: DocType): Brief? {
        val existing = memoryBriefs.value.find { it.brief_id == briefId } ?: return null
        val updated = existing.copy(doc_type = docType, doc_type_manual_override = true)
        memoryBriefs.value = memoryBriefs.value.map {
            if (it.brief_id == briefId) updated else it
        }
        return updated
    }

    override suspend fun addPhotoToBrief(briefId: String, photo: BriefPhoto): Brief? {
        val existing = memoryBriefs.value.find { it.brief_id == briefId } ?: return null
        val updatedPhotos = existing.photos + photo
        val updated = existing.copy(photos = updatedPhotos)
        memoryBriefs.value = memoryBriefs.value.map {
            if (it.brief_id == briefId) updated else it
        }
        try {
            FirebaseService.firestore.collection("briefs").document(briefId).update(
                mapOf("photos" to updatedPhotos)
            )
        } catch (_: Exception) {
        }
        return updated
    }

    override suspend fun removePhotoFromBrief(briefId: String, photoId: String): Brief? {
        val existing = memoryBriefs.value.find { it.brief_id == briefId } ?: return null
        val updatedPhotos = existing.photos.filter { it.photo_id != photoId }
        val updated = existing.copy(photos = updatedPhotos)
        memoryBriefs.value = memoryBriefs.value.map {
            if (it.brief_id == briefId) updated else it
        }
        try {
            FirebaseService.firestore.collection("briefs").document(briefId).update(
                mapOf("photos" to updatedPhotos)
            )
        } catch (_: Exception) {
        }
        return updated
    }

    override suspend fun deleteBrief(briefId: String) {
        memoryBriefs.value = memoryBriefs.value.filter { it.brief_id != briefId }
        try {
            FirebaseService.firestore.collection("briefs").document(briefId).delete()
        } catch (_: Exception) {
        }
    }

    override suspend fun detectDocType(briefId: String): DocType {
        kotlinx.coroutines.delay(1200)
        val existing = memoryBriefs.value.find { it.brief_id == briefId }
        val detected = when {
            existing?.photos?.size ?: 0 > 2 -> DocType.referral
            existing?.photos?.size == 2 -> DocType.lab_result
            else -> DocType.med_list
        }
        if (existing != null) {
            val updated = existing.copy(
                detected_doc_type = detected,
                detected_confidence = BriefConfidence.high,
                doc_type = if (existing.doc_type_manual_override) existing.doc_type else detected
            )
            memoryBriefs.value = memoryBriefs.value.map {
                if (it.brief_id == briefId) updated else it
            }
        }
        return detected
    }

    override suspend fun generateBrief(briefId: String, outputLanguage: BriefLanguage): Brief {
        kotlinx.coroutines.delay(2000)
        val existing = memoryBriefs.value.find { it.brief_id == briefId }
        val sampleBrief = MockData.sampleBriefs.first()

        val generated = (existing ?: sampleBrief).copy(
            status = BriefStatus.complete,
            content = sampleBrief.content,
            share_url_path = "/s/$briefId",
            updated_at = "2026-09-16T15:30:00Z"
        )

        memoryBriefs.value = memoryBriefs.value.map {
            if (it.brief_id == briefId) generated else it
        }

        try {
            FirebaseService.firestore.collection("briefs").document(briefId).update(
                mapOf(
                    "status" to "complete",
                    "updated_at" to "2026-09-16T15:30:00Z"
                )
            )
        } catch (_: Exception) {
        }

        return generated
    }

    override suspend fun translateBrief(briefId: String, targetLanguage: BriefLanguage): Brief {
        kotlinx.coroutines.delay(1500)
        val existing = memoryBriefs.value.find { it.brief_id == briefId }
            ?: throw IllegalStateException("Brief not found")

        val currentContent = existing.content
        val translatedContent = if (currentContent != null) {
            when (targetLanguage) {
                BriefLanguage.es -> BriefContent(
                    patient = currentContent.patient,
                    referral_reason = "Consulta de cardiología para evaluación de palpitaciones intermitentes e hipertensión limítrofe observada en clínica general.",
                    medications = currentContent.medications,
                    allergies = currentContent.allergies.map {
                        it.copy(reaction = if (it.reaction?.contains("hives", ignoreCase = true) == true) "Urticaria" else it.reaction)
                    },
                    flagged_items = listOf(
                        "Dosis de Lisinopril documentada como 20mg una vez al día, pero el frasco del paciente muestra 10mg.",
                        "Alergia a la penicilina notada en el historial clínico."
                    )
                )
                BriefLanguage.en -> currentContent
            }
        } else null

        val currentTranslations = existing.content_translations ?: emptyMap()
        val updatedTranslations = if (translatedContent != null) {
            currentTranslations + (targetLanguage.key to translatedContent)
        } else {
            currentTranslations
        }

        val updated = existing.copy(
            content_translations = updatedTranslations,
            available_languages = (existing.available_languages + targetLanguage).distinct()
        )

        memoryBriefs.value = memoryBriefs.value.map {
            if (it.brief_id == briefId) updated else it
        }

        return updated
    }
}
