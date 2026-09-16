package com.zayedmd.anteroom.auth

import com.zayedmd.anteroom.model.AppUser
import com.zayedmd.anteroom.firebase.FirebaseService
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthServiceImpl(private val tokenStorage: TokenStorage = TokenStorage()) : AuthService {
    private val _user = MutableStateFlow<AppUser?>(null)
    override val user: StateFlow<AppUser?> = _user.asStateFlow()

    private val _status = MutableStateFlow<AuthStatus>(AuthStatus.Loading)
    override val status: StateFlow<AuthStatus> = _status.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        scope.launch {
            try {
                FirebaseService.auth.authStateChanged.collect { firebaseUser ->
                    if (firebaseUser != null) {
                        try {
                            loadUserProfile(firebaseUser)
                        } catch (e: Exception) {
                            _user.value = null
                            _status.value = AuthStatus.Unauthenticated
                        }
                    } else {
                        _user.value = null
                        _status.value = AuthStatus.Unauthenticated
                    }
                }
            } catch (e: Exception) {
                _status.value = AuthStatus.Unauthenticated
            }
        }
    }

    private suspend fun loadUserProfile(firebaseUser: FirebaseUser) {
        val doc = FirebaseService.firestore.collection("users").document(firebaseUser.uid).get()
        val appUser = if (doc.exists) {
            try {
                doc.data<AppUser>()
            } catch (e: Exception) {
                AppUser(
                    user_id = firebaseUser.uid,
                    email = firebaseUser.email ?: "",
                    name = firebaseUser.displayName,
                    picture = firebaseUser.photoURL,
                    profile_completed = false,
                    provider = "email"
                )
            }
        } else {
            val newUser = AppUser(
                user_id = firebaseUser.uid,
                email = firebaseUser.email ?: "",
                name = firebaseUser.displayName,
                picture = firebaseUser.photoURL,
                profile_completed = false,
                provider = "email"
            )
            FirebaseService.firestore.collection("users").document(firebaseUser.uid).set(newUser)
            newUser
        }
        _user.value = appUser
        _status.value = AuthStatus.Authenticated
    }

    override suspend fun signInEmail(email: String, password: String) {
        val result = FirebaseService.auth.signInWithEmailAndPassword(email, password)
        val fbUser = result.user ?: throw IllegalStateException("Sign in failed")
        loadUserProfile(fbUser)
    }

    override suspend fun signUpEmail(email: String, password: String) {
        val result = FirebaseService.auth.createUserWithEmailAndPassword(email, password)
        val fbUser = result.user ?: throw IllegalStateException("Sign up failed")
        val newUser = AppUser(
            user_id = fbUser.uid,
            email = fbUser.email ?: email,
            profile_completed = false,
            provider = "email"
        )
        FirebaseService.firestore.collection("users").document(fbUser.uid).set(newUser)
        _user.value = newUser
        _status.value = AuthStatus.Authenticated
    }

    override suspend fun signInWithGoogle(idToken: String?, accessToken: String?) {
        if (idToken != null) {
            val credential = GoogleAuthProvider.credential(idToken, accessToken)
            val result = FirebaseService.auth.signInWithCredential(credential)
            val fbUser = result.user ?: throw IllegalStateException("Google sign in failed")
            loadUserProfile(fbUser)
        } else {
            throw IllegalArgumentException("Google ID token required for sign-in")
        }
    }

    override suspend fun updateProfile(name: String, dob: String, language: String, country: String) {
        val currentUser = _user.value ?: throw IllegalStateException("No authenticated user")
        val updated = currentUser.copy(
            name = name,
            dob = dob,
            language = language,
            country = country,
            profile_completed = true
        )
        FirebaseService.firestore.collection("users").document(currentUser.user_id).set(updated)
        _user.value = updated
    }

    override suspend fun signOut() {
        try {
            FirebaseService.auth.signOut()
        } catch (_: Exception) {}
        tokenStorage.clearToken()
        _user.value = null
        _status.value = AuthStatus.Unauthenticated
    }
}
