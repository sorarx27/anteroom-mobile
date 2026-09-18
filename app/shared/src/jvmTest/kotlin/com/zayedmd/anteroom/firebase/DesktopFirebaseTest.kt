package com.zayedmd.anteroom.firebase

import com.zayedmd.anteroom.data.BriefsRepositoryImpl
import com.zayedmd.anteroom.data.ProfilesRepositoryImpl
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves Firebase actually works on Desktop, against the live project.
 *
 * Desktop had no `google-services.json` hook and no `FirebaseApp.configure()`,
 * so nothing configured Firebase at all -- every call failed and the old
 * blanket `catch { }` presented that as an empty account. A compile-only check
 * would not have caught it, so this signs in for real and reads real data.
 *
 * Network-dependent by design. It is skipped rather than failed when the
 * credentials are absent, so a machine without them can still run `check`:
 *
 *     ANTEROOM_TEST_EMAIL=... ANTEROOM_TEST_PASSWORD=... ./gradlew :app:shared:jvmTest
 */
class DesktopFirebaseTest {

    private val email: String? = System.getenv("ANTEROOM_TEST_EMAIL")
    private val password: String? = System.getenv("ANTEROOM_TEST_PASSWORD")

    @Test
    fun signsInAndReadsFirestoreFromDesktop() = runBlocking {
        if (email.isNullOrBlank() || password.isNullOrBlank()) {
            println("SKIP: ANTEROOM_TEST_EMAIL / ANTEROOM_TEST_PASSWORD not set")
            return@runBlocking
        }

        initializeFirebaseIfNeeded()

        val result = FirebaseService.auth.signInWithEmailAndPassword(email, password)
        val uid = result.user?.uid
        assertTrue(!uid.isNullOrBlank(), "sign-in returned no uid")
        println("desktop signed in as $uid")

        // ensureSelfProfile is the first write every account makes.
        val self = ProfilesRepositoryImpl().ensureSelfProfile(uid!!, "Desktop Test")
        assertTrue(self.is_self, "self profile was not marked is_self")

        // And a read that the security rules have to allow by the user_id
        // predicate -- if the rules or the query were wrong this is a
        // PERMISSION_DENIED rather than a wrong answer.
        val briefs = BriefsRepositoryImpl().getBriefs(null)
        println("desktop read ${briefs.size} brief(s)")

        // Storage genuinely has no JVM implementation in GitLive 2.1.0. The
        // probe must say so rather than throwing NotImplementedError into a
        // screen, which is the whole reason FirebaseService is lazy.
        println("storage supported on desktop: ${FirebaseService.storageSupported}")
        assertTrue(!FirebaseService.storageSupported, "expected Storage to be unavailable on JVM")
    }
}
