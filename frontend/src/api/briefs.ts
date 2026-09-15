import { api, apiUpload } from "@/src/api/client";

export type BriefPhoto = {
  photo_id: string;
  filename: string;
  content_type: string;
  size: number;
  url: string; // e.g. /api/briefs/{id}/photos/{pid}/file
};

export type Brief = {
  brief_id: string;
  user_id: string;
  profile_id: string;
  doc_type: "referral" | "med_list" | "lab_result" | "other";
  doc_type_manual_override: boolean;
  detected_doc_type: "referral" | "med_list" | "lab_result" | "other" | null;
  detected_confidence: "low" | "medium" | "high" | null;
  status: "draft" | "complete";
  photos: BriefPhoto[];
  content: BriefContent | null;
  content_translations: Partial<Record<BriefLanguage, BriefContent>> | null;
  source_language: BriefLanguage | null;
  available_languages: BriefLanguage[];
  generated_at: string | null;
  share_url_path: string | null;
  created_at: string;
  updated_at: string;
};

export type BriefLanguage = "en" | "es";

export const BRIEF_LANGUAGES: { key: BriefLanguage; label: string; native: string }[] = [
  { key: "en", label: "English", native: "English" },
  { key: "es", label: "Spanish", native: "Español" },
];

export type BriefContent = {
  patient?: {
    name?: string | null;
    dob?: string | null;
    sex?: string | null;
    id_number?: string | null;
  };
  referral_reason?: string | null;
  medications?: { name: string; dose?: string | null; frequency?: string | null }[];
  allergies?: { substance: string; reaction?: string | null }[];
  flagged_items?: string[];
};

export const DOC_TYPES: {
  key: Brief["doc_type"];
  label: string;
  icon: string;
}[] = [
  { key: "referral", label: "Referral", icon: "mail-outline" },
  { key: "med_list", label: "Med list", icon: "medkit-outline" },
  { key: "lab_result", label: "Lab result", icon: "flask-outline" },
  { key: "other", label: "Other", icon: "document-outline" },
];

export const briefs = {
  create: (
    doc_type: Brief["doc_type"] = "other",
    profile_id?: string | null,
  ) => api.post<Brief>("/briefs", { doc_type, profile_id: profile_id ?? undefined }),
  list: (profile_id?: string | null) =>
    api.get<Brief[]>(
      profile_id ? `/briefs?profile_id=${encodeURIComponent(profile_id)}` : "/briefs",
    ),
  get: (brief_id: string) => api.get<Brief>(`/briefs/${brief_id}`),
  update: (
    brief_id: string,
    body: {
      doc_type?: Brief["doc_type"];
      status?: Brief["status"];
      photo_order?: string[];
    },
  ) => api.patch<Brief>(`/briefs/${brief_id}`, body),
  del: (brief_id: string) => api.del<{ ok: boolean }>(`/briefs/${brief_id}`),
  uploadPhoto: (
    brief_id: string,
    file: { uri: string; name: string; type: string },
  ) => apiUpload<Brief>(`/briefs/${brief_id}/photos`, file),
  deletePhoto: (brief_id: string, photo_id: string) =>
    api.del<Brief>(`/briefs/${brief_id}/photos/${photo_id}`),
  detectDocType: (brief_id: string) =>
    api.post<Brief>(`/briefs/${brief_id}/detect-doc-type`),
  generate: (brief_id: string) => api.post<Brief>(`/briefs/${brief_id}/generate`),
  translate: (brief_id: string, target_language: BriefLanguage) =>
    api.post<Brief>(`/briefs/${brief_id}/translate`, { target_language }),
};
