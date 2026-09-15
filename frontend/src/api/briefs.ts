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
  doc_type: "referral" | "med_list" | "lab_result" | "other";
  status: "draft" | "complete";
  photos: BriefPhoto[];
  created_at: string;
  updated_at: string;
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
  create: (doc_type: Brief["doc_type"] = "other") =>
    api.post<Brief>("/briefs", { doc_type }),
  list: () => api.get<Brief[]>("/briefs"),
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
};
