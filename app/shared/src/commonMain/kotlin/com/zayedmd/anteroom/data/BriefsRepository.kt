package com.zayedmd.anteroom.data

import com.zayedmd.anteroom.firebase.FirebaseService
import com.zayedmd.anteroom.model.*
import kotlinx.coroutines.flow.MutableStateFlow

interface BriefsRepository {
    suspend fun getBriefs(profileId: String?): List<Brief>
    suspend fun getBrief(briefId: String): Brief?
    suspend fun createBrief(userId: String, profileId: String, docType: DocType): Brief
    suspend fun updateBriefDocType(briefId: String, docType: DocType): Brief?
    suspend fun deleteBrief(briefId: String)
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

    override suspend fun deleteBrief(briefId: String) {
        memoryBriefs.value = memoryBriefs.value.filter { it.brief_id != briefId }
    }
}
