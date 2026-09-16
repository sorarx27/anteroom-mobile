package com.zayedmd.anteroom.data

import com.zayedmd.anteroom.firebase.FirebaseService
import com.zayedmd.anteroom.model.BiologicalSex
import com.zayedmd.anteroom.model.Profile
import com.zayedmd.anteroom.model.Relationship
import dev.gitlive.firebase.firestore.Direction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ProfilesRepository {
    suspend fun getProfiles(userId: String): List<Profile>
    suspend fun createProfile(userId: String, name: String, relationship: Relationship, dob: String?, sex: BiologicalSex?): Profile
    suspend fun updateProfile(profileId: String, name: String, relationship: Relationship, dob: String?, sex: BiologicalSex?): Profile
    suspend fun deleteProfile(profileId: String)
}

class ProfilesRepositoryImpl : ProfilesRepository {
    private val memoryProfiles = MutableStateFlow<List<Profile>>(MockData.sampleProfiles)

    override suspend fun getProfiles(userId: String): List<Profile> {
        return try {
            val snapshot = FirebaseService.firestore
                .collection("users")
                .document(userId)
                .collection("profiles")
                .orderBy("created_at", Direction.ASCENDING)
                .get()

            if (snapshot.documents.isNotEmpty()) {
                snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.data<Profile>()
                    } catch (e: Exception) {
                        null
                    }
                }
            } else {
                // If remote is empty, return memory/sample profiles for this user
                memoryProfiles.value
            }
        } catch (e: Exception) {
            // Fallback gracefully to local mock profiles if offline or unauthenticated
            memoryProfiles.value
        }
    }

    override suspend fun createProfile(
        userId: String,
        name: String,
        relationship: Relationship,
        dob: String?,
        sex: BiologicalSex?
    ): Profile {
        val newId = "prof_${kotlin.random.Random.nextInt(100000, 999999)}"
        val newProfile = Profile(
            profile_id = newId,
            user_id = userId,
            name = name,
            relationship = relationship,
            dob = dob,
            sex = sex,
            is_self = false,
            created_at = "2026-09-16T12:00:00Z",
            updated_at = "2026-09-16T12:00:00Z"
        )
        try {
            FirebaseService.firestore
                .collection("users")
                .document(userId)
                .collection("profiles")
                .document(newId)
                .set(newProfile)
        } catch (_: Exception) {
            // Keep in memory if firestore call fails
        }
        memoryProfiles.value = memoryProfiles.value + newProfile
        return newProfile
    }

    override suspend fun updateProfile(
        profileId: String,
        name: String,
        relationship: Relationship,
        dob: String?,
        sex: BiologicalSex?
    ): Profile {
        val existing = memoryProfiles.value.find { it.profile_id == profileId }
        val updated = existing?.copy(
            name = name,
            relationship = if (existing.is_self) Relationship.self else relationship,
            dob = dob,
            sex = sex
        ) ?: Profile(
            profile_id = profileId,
            user_id = "",
            name = name,
            relationship = relationship,
            dob = dob,
            sex = sex
        )
        if (existing != null && existing.user_id.isNotEmpty()) {
            try {
                FirebaseService.firestore
                    .collection("users")
                    .document(existing.user_id)
                    .collection("profiles")
                    .document(profileId)
                    .update(
                        mapOf(
                            "name" to name,
                            "relationship" to (if (existing.is_self) "self" else relationship.name),
                            "dob" to dob,
                            "sex" to sex?.name
                        )
                    )
            } catch (_: Exception) {
            }
        }
        memoryProfiles.value = memoryProfiles.value.map {
            if (it.profile_id == profileId) updated else it
        }
        return updated
    }

    override suspend fun deleteProfile(profileId: String) {
        val existing = memoryProfiles.value.find { it.profile_id == profileId }
        if (existing != null && existing.user_id.isNotEmpty()) {
            try {
                FirebaseService.firestore
                    .collection("users")
                    .document(existing.user_id)
                    .collection("profiles")
                    .document(profileId)
                    .delete()
            } catch (_: Exception) {
            }
        }
        memoryProfiles.value = memoryProfiles.value.filter { it.profile_id != profileId }
    }
}
