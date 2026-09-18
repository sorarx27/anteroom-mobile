package com.zayedmd.anteroom.storage

import com.zayedmd.anteroom.firebase.FirebaseService
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val BUCKET = "anteroom-d2e72.firebasestorage.app"
private const val HOST = "https://firebasestorage.googleapis.com"

private val json = Json { ignoreUnknownKeys = true }
private val client by lazy { HttpClient() }

/**
 * Desktop: the Storage REST API, because GitLive has no JVM implementation.
 *
 * This is not a privileged back door. The request carries the signed-in
 * user's ID token, so `storage.rules` applies exactly as it does on the
 * phone: the same owner check, the same 8 MB ceiling, the same
 * `photo_[A-Za-z0-9]+\.(jpg|png|webp)` filename pattern. A Desktop client
 * cannot reach anything a mobile one cannot.
 */
actual suspend fun uploadPage(
    path: String,
    bytes: ByteArray,
    contentType: String
): String {
    val user = FirebaseService.auth.currentUser
        ?: throw IllegalStateException("You need to be signed in to add a page.")
    val token = user.getIdToken(false)
        ?: throw IllegalStateException("Couldn't confirm your session. Sign in again.")

    val encodedPath = path.encodeURLParameter()
    val response = client.post("$HOST/v0/b/$BUCKET/o?uploadType=media&name=$encodedPath") {
        header("Authorization", "Bearer $token")
        contentType(ContentType.parse(contentType))
        setBody(bytes)
    }

    if (!response.status.isSuccess()) {
        // 403 here is the security rule doing its job, not a bug; surface it
        // as the same message the SDK path would produce.
        if (response.status == HttpStatusCode.Forbidden) {
            throw IllegalStateException("PERMISSION_DENIED uploading page")
        }
        throw IllegalStateException("Upload failed (${response.status.value})")
    }

    // The response carries a download token; the rendering URL is built from
    // it the same way the SDK builds one.
    val downloadToken = runCatching {
        json.parseToJsonElement(response.bodyAsText())
            .let { it as JsonObject }["downloadTokens"]?.jsonPrimitive?.content
    }.getOrNull()

    return buildString {
        append(HOST).append("/v0/b/").append(BUCKET).append("/o/").append(encodedPath)
        append("?alt=media")
        if (!downloadToken.isNullOrBlank()) append("&token=").append(downloadToken)
    }
}
