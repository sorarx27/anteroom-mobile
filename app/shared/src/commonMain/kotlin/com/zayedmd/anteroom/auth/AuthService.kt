package com.zayedmd.anteroom.auth

import com.zayedmd.anteroom.model.AppUser
import kotlinx.coroutines.flow.StateFlow

enum class AuthStatus {
    Loading, Authenticated, Unauthenticated
}

interface AuthService {
    val user: StateFlow<AppUser?>
    val status: StateFlow<AuthStatus>

    suspend fun signInEmail(email: String, password: String)
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
