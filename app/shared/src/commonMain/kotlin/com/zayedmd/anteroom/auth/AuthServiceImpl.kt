package com.zayedmd.anteroom.auth

import com.zayedmd.anteroom.model.AppUser
import com.zayedmd.anteroom.data.ProfilesRepository
import com.zayedmd.anteroom.data.ProfilesRepositoryImpl
import com.zayedmd.anteroom.data.requireUid
import com.zayedmd.anteroom.firebase.FirebaseService
import dev.gitlive.firebase.auth.FirebaseUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.Serializable

class AuthServiceImpl(
    private val tokenStorage: TokenStorage = TokenStorage(),
    private val profilesRepository: ProfilesRepository = ProfilesRepositoryImpl()
) : AuthService {
    private val _user = MutableStateFlow<AppUser?>(null)
    override val user: StateFlow<AppUser?> = _user.asStateFlow()

    private val _status = MutableStateFlow<AuthStatus>(AuthStatus.Loading)
    override val status: StateFlow<AuthStatus> = _status.asStateFlow()

    private val _profileStatusKnown = MutableStateFlow(true)
    override val profileStatusKnown: StateFlow<Boolean> = _profileStatusKnown.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        observeFirebaseAuth()
    }

    private fun observeFirebaseAuth() {
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
        val fallback = AppUser(
            user_id = firebaseUser.uid,
            email = firebaseUser.email ?: "",
            name = firebaseUser.displayName,
            picture = firebaseUser.photoURL,
            profile_completed = false,
            provider = "email"
        )
        val userDoc = FirebaseService.firestore.collection("users").document(firebaseUser.uid)
        val doc = runCatching { userDoc.get() }.getOrNull()
        val appUser = when {
            // The read itself failed, which in practice means the network did.
            // Firebase Auth still holds a valid session, so keep it and carry on
            // with what the token already tells us. Dropping to Unauthenticated
            // here is what makes a signed-in user look signed out on a bad
            // connection; the profile reloads on the next auth state emission.
            doc == null -> {
                _profileStatusKnown.value = false
                fallback
            }
            doc.exists -> {
                _profileStatusKnown.value = true
                runCatching { doc.data<AppUser>() }.getOrDefault(fallback)
            }
            else -> {
                _profileStatusKnown.value = true
                runCatching { userDoc.set(fallback) }
                fallback
            }
        }
        bootstrapSelfProfile(appUser)
        _user.value = appUser
        _status.value = AuthStatus.Authenticated
    }

    /**
     * Creates `users/{uid}/profiles/self` if it isn't there yet.
     *
     * Every brief points at a profile, and the Firestore rule for creating one
     * checks that the referenced profile document exists — so without this an
     * account can sign in and then be unable to make a brief at all. The call
     * is idempotent, so running it on every sign-in costs one read.
     *
     * A failure here does not revoke the session. This runs on the silent
     * restore path as well as on an explicit sign-in, and logging someone out
     * because a profile write timed out would be a worse bug than the one it
     * guards against. `onSnapClick` calls the same method at the point where
     * the profile is actually needed and can report the failure there.
     */
    private suspend fun bootstrapSelfProfile(appUser: AppUser) {
        runCatching {
            profilesRepository.ensureSelfProfile(
                userId = appUser.user_id,
                displayName = appUser.name ?: ""
            )
        }
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
        bootstrapSelfProfile(newUser)
        _user.value = newUser
        _status.value = AuthStatus.Authenticated
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

    override suspend fun deleteAccount() {
        requireUid()
        // 300s to match the function: an account with six-page briefs can be a
        // few hundred Storage objects, and the default 70s callable timeout
        // would report failure over a purge that is still running.
        FirebaseService.functions
            .httpsCallable("deleteAccount", timeout = 300.seconds)
            .invoke(DeleteAccountRequest.serializer(), DeleteAccountRequest())

        // Only after the server confirms. Signing out first would drop the ID
        // token the callable authenticates with.
        signOut()
    }

    override suspend fun sendPasswordReset(email: String) {
        FirebaseService.auth.sendPasswordResetEmail(email)
    }

    override suspend fun signOut() {
        try {
            FirebaseService.auth.signOut()
        } catch (_: Exception) {}
        tokenStorage.clearToken()
        _profileStatusKnown.value = true
        _user.value = null
        _status.value = AuthStatus.Unauthenticated
    }
}

/**
 * The `deleteAccount` callable takes no arguments — the uid comes from the ID
 * token, so there is nothing for a caller to supply or to get wrong. This
 * exists only because the payload still has to serialise to *something*.
 */
@Serializable
private class DeleteAccountRequest
