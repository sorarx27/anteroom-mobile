package com.zayedmd.anteroom.auth

import com.zayedmd.anteroom.model.AppUser
import kotlinx.coroutines.flow.StateFlow

enum class AuthStatus {
    Loading, Authenticated, Unauthenticated
}

interface AuthService {
    val user: StateFlow<AppUser?>
    val status: StateFlow<AuthStatus>

    /**
     * False when the signed-in user's `users/{uid}` document could not be
     * read and the session is running on the identity in the token alone.
     *
     * The fallback user carries `profile_completed = false` because there is
     * no third value for "unknown" -- and the navigation gate routes on that
     * field. Without this flag an established user whose profile read failed
     * (offline cold start, most often) is sent to profile setup instead of
     * their dashboard, and the setup form's write then never completes.
     */
    val profileStatusKnown: StateFlow<Boolean>

    suspend fun signInEmail(email: String, password: String)

    /** Sends Firebase's password-reset email. The only recovery path there is. */
    suspend fun sendPasswordReset(email: String)
    suspend fun signUpEmail(email: String, password: String)
    suspend fun updateProfile(name: String, dob: String, language: String, country: String)
    suspend fun signOut()

    /**
     * Permanently deletes the account and every brief, page image, profile and
     * billing record attached to it, then signs out.
     *
     * Required in the app by App Store Review Guideline 5.1.1(v). The work
     * happens in the `deleteAccount` Cloud Function because a client is denied
     * write access to the entitlement and usage documents it would need to
     * remove -- the same rules that make the server-side Pro check meaningful
     * also mean the client cannot clean up after itself.
     *
     * Throws if the purge fails, and in that case the account is still intact
     * and still usable. Only a successful return means the data is gone.
     */
    suspend fun deleteAccount()
}
