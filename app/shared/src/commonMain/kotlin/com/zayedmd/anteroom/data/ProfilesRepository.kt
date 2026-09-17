package com.zayedmd.anteroom.data

import com.zayedmd.anteroom.firebase.FirebaseService
import com.zayedmd.anteroom.model.BiologicalSex
import com.zayedmd.anteroom.model.Profile
import com.zayedmd.anteroom.model.Relationship
import dev.gitlive.firebase.firestore.Direction

interface ProfilesRepository {
    suspend fun getProfiles(userId: String): List<Profile>
    suspend fun createProfile(
        userId: String,
        name: String,
        relationship: Relationship,
        dob: String?,
        sex: BiologicalSex?
    ): Profile

    suspend fun updateProfile(
        profileId: String,
        name: String,
        relationship: Relationship,
        dob: String?,
        sex: BiologicalSex?
    ): Profile

    suspend fun deleteProfile(profileId: String)

    /** Creates the user's own profile if it isn't there yet. Idempotent. */
    suspend fun ensureSelfProfile(userId: String, displayName: String): Profile
}

/**
 * Firestore-backed profiles, stored at `users/{uid}/profiles/{profileId}`.
 *
 * There is deliberately no in-memory fallback. The previous implementation
 * caught every Firestore failure and returned `MockData.sampleProfiles`, which
 * meant a permission error, an offline device and an empty account all looked
 * identical — and identical to success. Failures now propagate so the UI can
 * say what went wrong.
 */
class ProfilesRepositoryImpl : ProfilesRepository {

    private fun profiles(userId: String) =
        FirebaseService.firestore.collection("users").document(userId).collection("profiles")

    override suspend fun getProfiles(userId: String): List<Profile> =
        profiles(userId)
            .orderBy("created_at", Direction.ASCENDING)
            .get()
            .documents
            .map { it.data<Profile>() }

    override suspend fun ensureSelfProfile(userId: String, displayName: String): Profile {
        val ref = profiles(userId).document(SELF_PROFILE_ID)
        val existing = ref.get()
        if (existing.exists) return existing.data<Profile>()

        val now = nowIso()
        val self = Profile(
            profile_id = SELF_PROFILE_ID,
            user_id = userId,
            name = displayName.ifBlank { "You" },
            relationship = Relationship.self,
            is_self = true,
            created_at = now,
            updated_at = now
        )
        ref.set(self)
        return self
    }

    override suspend fun createProfile(
        userId: String,
        name: String,
        relationship: Relationship,
        dob: String?,
        sex: BiologicalSex?
    ): Profile {
        // Firestore generates the id. The old client-side random in a 900k
        // space was a real collision surface, and `self` is reserved.
        val ref = profiles(userId).document
        val now = nowIso()
        val profile = Profile(
            profile_id = ref.id,
            user_id = userId,
            name = name,
            // `self` is created only by ensureSelfProfile; security rules
            // reject relationship == self on any other document id.
            relationship = if (relationship == Relationship.self) Relationship.other else relationship,
            dob = dob,
            sex = sex,
            is_self = false,
            created_at = now,
            updated_at = now
        )
        ref.set(profile)
        return profile
    }

    override suspend fun updateProfile(
        profileId: String,
        name: String,
        relationship: Relationship,
        dob: String?,
        sex: BiologicalSex?
    ): Profile {
        val userId = requireUid()
        val ref = profiles(userId).document(profileId)
        val snapshot = ref.get()
        if (!snapshot.exists) throw NoSuchElementException("Profile not found")
        val existing = snapshot.data<Profile>()

        val updated = existing.copy(
            name = name,
            relationship = if (existing.is_self) Relationship.self else relationship,
            dob = dob,
            sex = sex,
            updated_at = nowIso()
        )
        ref.set(updated)
        return updated
    }

    override suspend fun deleteProfile(profileId: String) {
        if (profileId == SELF_PROFILE_ID) {
            // Would orphan every brief pointing at it; rules reject it anyway.
            throw IllegalArgumentException("You can't delete your own profile.")
        }
        profiles(requireUid()).document(profileId).delete()
    }
}
