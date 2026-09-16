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
    suspend fun signInWithGoogle(idToken: String? = null, accessToken: String? = null)
    suspend fun updateProfile(name: String, dob: String, language: String, country: String)
    suspend fun signOut()
}
