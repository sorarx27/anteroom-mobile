package com.zayedmd.anteroom.data

import com.zayedmd.anteroom.model.*

object MockData {
    val sampleSelfProfile = Profile(
        profile_id = "prof_self_01",
        user_id = "user_default",
        name = "Sarah Jenkins",
        relationship = Relationship.self,
        dob = "1988-04-12",
        sex = BiologicalSex.female,
        is_self = true,
        created_at = "2026-01-10T10:00:00Z",
        updated_at = "2026-01-10T10:00:00Z"
    )

    val samplePartnerProfile = Profile(
        profile_id = "prof_partner_02",
        user_id = "user_default",
        name = "David Jenkins",
        relationship = Relationship.partner,
        dob = "1985-09-24",
        sex = BiologicalSex.male,
        is_self = false,
        created_at = "2026-02-01T14:30:00Z",
        updated_at = "2026-02-01T14:30:00Z"
    )

    val sampleChildProfile = Profile(
        profile_id = "prof_child_03",
        user_id = "user_default",
        name = "Leo Jenkins",
        relationship = Relationship.child,
        dob = "2019-11-03",
        sex = BiologicalSex.male,
        is_self = false,
        created_at = "2026-03-05T09:15:00Z",
        updated_at = "2026-03-05T09:15:00Z"
    )

    val sampleProfiles = listOf(
        sampleSelfProfile,
        samplePartnerProfile,
        sampleChildProfile
    )

    val sampleBrief1 = Brief(
        brief_id = "br_001",
        user_id = "user_default",
        profile_id = "prof_self_01",
        doc_type = DocType.referral,
        doc_type_manual_override = false,
        detected_doc_type = DocType.referral,
        detected_confidence = BriefConfidence.high,
        status = BriefStatus.complete,
        photos = listOf(
            BriefPhoto(
                photo_id = "ph_01",
                filename = "referral_page1.jpg",
                content_type = "image/jpeg",
                size = 1420500,
                url = "https://images.unsplash.com/photo-1584515979956-d9f6e5d09982?auto=format&fit=crop&q=80&w=400"
            )
        ),
        content = BriefContent(
            patient = PatientInfo(name = "Sarah Jenkins", dob = "1988-04-12", sex = "Female"),
            referral_reason = "Evaluation for persistent right lower quadrant abdominal pain radiating posteriorly, suspicious for nephrolithiasis or chronic appendiceal etiology.",
            medications = listOf(
                MedicationItem(name = "Amoxicillin-Clavulanate", dose = "875/125 mg", frequency = "Twice daily with meals"),
                MedicationItem(name = "Ibuprofen", dose = "400 mg", frequency = "Every 6 hours as needed for discomfort")
            ),
            allergies = listOf(
                AllergyItem(substance = "Sulfa antibiotics", reaction = "Severe maculopapular rash")
            ),
            flagged_items = listOf(
                "Patient allergic to Sulfa compounds - confirm any contrast media",
                "Creatinine borderline elevated at 1.28 mg/dL"
            )
        ),
        source_language = BriefLanguage.en,
        available_languages = listOf(BriefLanguage.en, BriefLanguage.es),
        generated_at = "2026-09-15T08:30:00Z",
        share_url_path = "/public/briefs/share_br_001",
        created_at = "2026-09-15T08:28:00Z",
        updated_at = "2026-09-15T08:30:00Z"
    )

    val sampleBrief2 = Brief(
        brief_id = "br_002",
        user_id = "user_default",
        profile_id = "prof_self_01",
        doc_type = DocType.lab_result,
        doc_type_manual_override = false,
        detected_doc_type = DocType.lab_result,
        detected_confidence = BriefConfidence.high,
        status = BriefStatus.complete,
        photos = listOf(
            BriefPhoto(
                photo_id = "ph_02",
                filename = "lipid_panel.jpg",
                content_type = "image/jpeg",
                size = 980200,
                url = "https://images.unsplash.com/photo-1579154204601-01588f351e67?auto=format&fit=crop&q=80&w=400"
            )
        ),
        content = BriefContent(
            patient = PatientInfo(name = "Sarah Jenkins", dob = "1988-04-12", sex = "Female"),
            referral_reason = "Routine annual fasting metabolic and comprehensive lipid profile.",
            medications = listOf(
                MedicationItem(name = "Atorvastatin", dose = "20 mg", frequency = "Once nightly at bedtime")
            ),
            allergies = emptyList(),
            flagged_items = listOf(
                "LDL Cholesterol mildly elevated: 142 mg/dL (Target < 100 mg/dL)",
                "Fasting Glucose normal: 89 mg/dL"
            )
        ),
        source_language = BriefLanguage.en,
        available_languages = listOf(BriefLanguage.en),
        generated_at = "2026-09-10T14:15:00Z",
        share_url_path = "/public/briefs/share_br_002",
        created_at = "2026-09-10T14:10:00Z",
        updated_at = "2026-09-10T14:15:00Z"
    )

    val sampleBriefDraft = Brief(
        brief_id = "br_003",
        user_id = "user_default",
        profile_id = "prof_partner_02",
        doc_type = DocType.med_list,
        doc_type_manual_override = true,
        detected_doc_type = DocType.med_list,
        detected_confidence = BriefConfidence.medium,
        status = BriefStatus.draft,
        photos = listOf(
            BriefPhoto(
                photo_id = "ph_03",
                filename = "discharge_slip.jpg",
                content_type = "image/jpeg",
                size = 1845000,
                url = "https://images.unsplash.com/photo-1584515933487-779824d29309?auto=format&fit=crop&q=80&w=400"
            )
        ),
        content = null,
        source_language = BriefLanguage.en,
        available_languages = listOf(BriefLanguage.en),
        generated_at = null,
        share_url_path = null,
        created_at = "2026-09-16T10:45:00Z",
        updated_at = "2026-09-16T10:46:00Z"
    )

    val sampleBriefs = listOf(
        sampleBrief1,
        sampleBrief2,
        sampleBriefDraft
    )
}
