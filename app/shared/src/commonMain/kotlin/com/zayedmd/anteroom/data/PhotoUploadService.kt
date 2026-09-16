package com.zayedmd.anteroom.data

import com.zayedmd.anteroom.media.CapturedPhoto
import com.zayedmd.anteroom.model.Brief
import com.zayedmd.anteroom.model.BriefPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

        var updatedBrief: Brief? = null

        for (photo in photos) {
            // Check 8 MB limit matching emergent-build
            if (photo.size > 8 * 1024 * 1024) {
                _lastError.value = "One of the photos exceeds the 8 MB limit."
                continue
            }

            _uploadingCount.value += 1
            try {
                val briefPhoto = BriefPhoto(
                    photo_id = photo.id,
                    filename = photo.name,
                    content_type = photo.mimeType,
                    size = photo.size,
                    url = photo.uri ?: ""
                )
                updatedBrief = briefsRepository.addPhotoToBrief(briefId, briefPhoto)
            } catch (e: Exception) {
                _lastError.value = e.message ?: "Failed to upload photo."
            } finally {
                _uploadingCount.value = maxOf(0, _uploadingCount.value - 1)
            }
        }

        return updatedBrief ?: briefsRepository.getBrief(briefId)
    }

    fun clearError() {
        _lastError.value = null
    }
}
