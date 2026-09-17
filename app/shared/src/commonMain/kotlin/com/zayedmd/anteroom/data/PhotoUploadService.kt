package com.zayedmd.anteroom.data

import com.zayedmd.anteroom.firebase.FirebaseService
import com.zayedmd.anteroom.media.CapturedPhoto
import com.zayedmd.anteroom.model.Brief
import com.zayedmd.anteroom.model.BriefPhoto
import com.zayedmd.anteroom.storage.StoragePaths
import com.zayedmd.anteroom.storage.storageData
import dev.gitlive.firebase.storage.FirebaseStorageMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Uploads captured pages to Firebase Storage and records them on the brief.
 *
 * This used to write `url = photo.uri ?: ""` — a `content://` or `file://`
 * path that is meaningless on any other device and, on the targets where the
 * picker returns bytes rather than a URI, an empty string. No image ever left
 * the phone. `generateBrief` reads pages out of Storage with the Admin SDK, so
 * the upload here is what makes the whole pipeline possible.
 *
 * Order matters: bytes reach Storage *before* the photo is appended to the
 * Firestore document. A brief therefore never references an object that isn't
 * there, which is the failure the Cloud Function cannot recover from.
 */
class PhotoUploadService(
    private val briefsRepository: BriefsRepository
) {
    private val _uploadingCount = MutableStateFlow(0)
    val uploadingCount: StateFlow<Int> = _uploadingCount.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    suspend fun uploadPhotos(briefId: String, photos: List<CapturedPhoto>): Brief? {
        if (photos.isEmpty()) return briefsRepository.getBrief(briefId)
        _lastError.value = null

        if (!FirebaseService.storageSupported) {
            _lastError.value =
                "Photo upload isn't available on this platform yet. Use the Android or iOS app."
            return briefsRepository.getBrief(briefId)
        }

        val uid = try {
            requireUid()
        } catch (e: NotSignedInException) {
            _lastError.value = e.message
            return null
        }

        var current = briefsRepository.getBrief(briefId)
        if (current == null) {
            _lastError.value = "That draft no longer exists."
            return null
        }

        for (photo in photos) {
            // Checked per iteration rather than once up front, so a batch that
            // straddles the cap uploads the pages that fit instead of failing
            // whole. The model call is priced per page and the prompt is tuned
            // for a handful of pages, so this is a real limit, not a guess.
            if (current!!.photos.size >= StoragePaths.MAX_PAGES) {
                _lastError.value =
                    "A brief can hold up to ${StoragePaths.MAX_PAGES} pages. The rest weren't added."
                break
            }

            val bytes = photo.bytes
            if (bytes == null || bytes.isEmpty()) {
                _lastError.value = "Couldn't read ${photo.name.ifBlank { "that photo" }}."
                continue
            }
            if (bytes.size > StoragePaths.MAX_PHOTO_BYTES) {
                _lastError.value = "One of the photos is over 8 MB. Try a lower-resolution capture."
                continue
            }
            val contentType = StoragePaths.contentTypeOrNull(photo.mimeType)
            if (contentType == null) {
                _lastError.value = "Only JPEG, PNG and WebP images can be added."
                continue
            }

            _uploadingCount.value += 1
            try {
                val path = StoragePaths.pagePath(uid, briefId, photo.id, contentType)
                val ref = FirebaseService.storage.reference(path)

                // The content type has to be set explicitly. Without it the
                // object lands as application/octet-stream and the storage
                // rule's contentType check rejects the write with a bare
                // PERMISSION_DENIED that names nothing.
                ref.putData(
                    storageData(bytes),
                    FirebaseStorageMetadata(contentType = contentType)
                )

                current = briefsRepository.addPhotoToBrief(
                    briefId,
                    BriefPhoto(
                        photo_id = photo.id,
                        filename = photo.name,
                        content_type = contentType,
                        size = bytes.size.toLong(),
                        storage_path = path,
                        url = ref.getDownloadUrl()
                    )
                ) ?: current
            } catch (e: Exception) {
                _lastError.value = e.message ?: "Couldn't upload that photo."
            } finally {
                _uploadingCount.value = maxOf(0, _uploadingCount.value - 1)
            }
        }

        return current ?: briefsRepository.getBrief(briefId)
    }

    /**
     * Removes a page from the brief and deletes its object.
     *
     * Firestore first: if the delete half fails the user still sees the page
     * gone, and an orphaned object costs a fraction of a cent. The reverse
     * order would leave the brief pointing at bytes that no longer exist,
     * which breaks `generateBrief`.
     */
    suspend fun removePhoto(briefId: String, photo: BriefPhoto): Brief? {
        _lastError.value = null
        val updated = try {
            briefsRepository.removePhotoFromBrief(briefId, photo.photo_id)
        } catch (e: Exception) {
            _lastError.value = e.message ?: "Couldn't remove that page."
            return null
        }

        if (photo.storage_path.isNotBlank() && FirebaseService.storageSupported) {
            runCatching { FirebaseService.storage.reference(photo.storage_path).delete() }
        }
        return updated
    }

    fun clearError() {
        _lastError.value = null
    }
}
