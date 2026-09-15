import { api } from "@/src/api/client";

export type Relationship = "self" | "partner" | "child" | "parent" | "other";

export type Profile = {
  profile_id: string;
  user_id: string;
  name: string;
  relationship: Relationship;
  dob: string | null;
  sex: string | null;
  is_self: boolean;
  created_at: string;
  updated_at: string;
};

export const RELATIONSHIP_LABELS: Record<Relationship, string> = {
  self: "You",
  partner: "Partner",
  child: "Child",
  parent: "Parent",
  other: "Other",
};

export const RELATIONSHIP_ICONS: Record<Relationship, string> = {
  self: "person-outline",
  partner: "heart-outline",
  child: "happy-outline",
  parent: "people-outline",
  other: "person-add-outline",
};

export const profiles = {
  list: () => api.get<Profile[]>("/profiles"),
  create: (body: {
    name: string;
    relationship: Exclude<Relationship, "self">;
    dob?: string | null;
    sex?: "male" | "female" | "other" | null;
  }) => api.post<Profile>("/profiles", body),
  update: (
    profile_id: string,
    body: Partial<{
      name: string;
      relationship: Relationship;
      dob: string | null;
      sex: "male" | "female" | "other" | null;
    }>,
  ) => api.patch<Profile>(`/profiles/${profile_id}`, body),
  del: (profile_id: string) => api.del<{ ok: boolean }>(`/profiles/${profile_id}`),
};

export function initialsFromName(name: string): string {
  return name
    .split(" ")
    .map((s) => s.trim()[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase() || "?";
}
