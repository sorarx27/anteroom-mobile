package com.zayedmd.anteroom.model

import kotlinx.serialization.Serializable

@Serializable
enum class DocType(val key: String, val label: String, val icon: String) {
    referral("referral", "Referral", "mail"),
    med_list("med_list", "Med list", "medkit"),
    lab_result("lab_result", "Lab result", "flask"),
    other("other", "Other", "document");

    companion object {
        fun fromKey(key: String?): DocType =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: other
    }
}

@Serializable
enum class BriefStatus {
    draft,

    /** Set by generateBrief before it calls the model, so a client that
     *  reconnects mid-extraction sees work in progress rather than a draft. */
    generating,
    complete,

    /** generateBrief failed after it had already claimed the brief. Paired
     *  with [Brief.last_error] so the UI can explain itself and offer a retry
     *  instead of spinning forever. */
    failed
}

@Serializable
enum class BriefConfidence {
    low,
    medium,
    high
}

@Serializable
enum class BriefLanguage(val key: String, val label: String, val nativeName: String) {
    en("en", "English", "English"),
    es("es", "Spanish", "Español");

    companion object {
        fun fromKey(key: String?): BriefLanguage =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: en
    }
}

@Serializable
data class BriefPhoto(
    val photo_id: String,
    val filename: String = "",
    val content_type: String = "image/jpeg",
    val size: Long = 0L,

    /** Object path inside the Firebase Storage bucket, for example
     *  `users/{uid}/briefs/{briefId}/pages/photo_ab12cd34.jpg`. This is the
     *  canonical reference and what Cloud Functions read bytes from. */
    val storage_path: String = "",

    /** Download URL, used only for rendering on the client. Empty until the
     *  upload completes. */
    val url: String = ""
)

@Serializable
data class PatientInfo(
    val name: String? = null,
    val dob: String? = null,
    val sex: String? = null,
    val id_number: String? = null
)

@Serializable
data class MedicationItem(
    val name: String,
    val dose: String? = null,
    val frequency: String? = null
)

@Serializable
data class AllergyItem(
    val substance: String,
    val reaction: String? = null
)

@Serializable
data class BriefContent(
    val patient: PatientInfo? = null,
    val referral_reason: String? = null,
    val medications: List<MedicationItem> = emptyList(),
    val allergies: List<AllergyItem> = emptyList(),
    val flagged_items: List<String> = emptyList()
)

@Serializable
data class Brief(
    val brief_id: String,
    val user_id: String,
    val profile_id: String,
    val doc_type: DocType = DocType.other,
    val doc_type_manual_override: Boolean = false,
    val detected_doc_type: DocType? = null,
    val detected_confidence: BriefConfidence? = null,
    val status: BriefStatus = BriefStatus.draft,
    val photos: List<BriefPhoto> = emptyList(),
    val content: BriefContent? = null,
    val content_translations: Map<String, BriefContent>? = null,
    val source_language: BriefLanguage? = null,
    val available_languages: List<BriefLanguage> = emptyList(),
    val generated_at: String? = null,
    val share_url_path: String? = null,

    /** Random token backing the public share page. Server-written; the client
     *  may read its own but can never set it. */
    val share_token: String? = null,
    val share_expires_at: String? = null,

    /** Populated alongside [BriefStatus.failed]. */
    val last_error: String? = null,

    // ISO-8601 UTC strings, never Firestore Timestamps. The Python Admin SDK
    // writes a datetime as a Timestamp, which would not decode into these
    // String fields — and BriefsRepository would swallow the error and make
    // the brief vanish from the dashboard. ISO-8601 UTC also sorts
    // lexicographically, so orderBy still works.
    val created_at: String = "",
    val updated_at: String = ""
)
