package com.zayedmd.anteroom.firebase

import com.zayedmd.anteroom.data.BriefsRepositoryImpl
import com.zayedmd.anteroom.data.ProfilesRepositoryImpl
import com.zayedmd.anteroom.storage.StoragePaths
import com.zayedmd.anteroom.storage.uploadPage
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

        // GitLive has no JVM Storage -- every member is TODO() -- which is
        // why the probe must report it rather than throwing
        // NotImplementedError into a screen, and why uploads take the REST
        // path on this target.
        println("storage supported on desktop: ${FirebaseService.storageSupported}")
        assertTrue(!FirebaseService.storageSupported, "expected GitLive Storage to be absent on JVM")

        // The REST upload is the thing that makes Desktop usable at all, so
        // it is exercised for real: same bucket, same rules, same filename
        // pattern the phone has to satisfy.
        val jpeg = javaClass.classLoader.getResourceAsStream("desktop_upload_probe.jpg")?.readBytes()
            ?: minimalJpeg()
        val path = StoragePaths.pagePath(uid, "desktopprobe", "desktopprobe1", "image/jpeg")
        val url = uploadPage(path, jpeg, "image/jpeg")
        println("desktop uploaded ${jpeg.size} bytes -> ${url.substringBefore('?')}")
        assertTrue(url.startsWith("https://"), "upload returned no URL")

        // And the rules must still bite on this path: a filename the pattern
        // rejects has to fail, or Desktop would be a way around them.
        val bad = runCatching {
            uploadPage("users/$uid/briefs/desktopprobe/pages/not-allowed.jpg", jpeg, "image/jpeg")
        }
        assertTrue(bad.isFailure, "storage.rules did not reject a bad filename over REST")
        println("desktop rejected bad filename: ${bad.exceptionOrNull()?.message}")
    }

    /** A 1x1 JPEG, so the test does not depend on a checked-in fixture. */
    private fun minimalJpeg(): ByteArray = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
        0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01,
        0x00, 0x00, 0xFF.toByte(), 0xDB.toByte(), 0x00, 0x43, 0x00,
    ) + ByteArray(64) { 0x10 } + byteArrayOf(
        0xFF.toByte(), 0xC9.toByte(), 0x00, 0x0B, 0x08, 0x00, 0x01, 0x00, 0x01,
        0x01, 0x01, 0x11, 0x00, 0xFF.toByte(), 0xCC.toByte(), 0x00, 0x06, 0x00,
        0x10, 0x10, 0x05, 0xFF.toByte(), 0xDA.toByte(), 0x00, 0x08, 0x01, 0x01,
        0x00, 0x00, 0x3F, 0x00, 0xD2.toByte(), 0xCF.toByte(), 0x20,
        0xFF.toByte(), 0xD9.toByte()
    )
}
