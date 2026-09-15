import { Platform } from "react-native";

const BASE_URL = process.env.EXPO_PUBLIC_BACKEND_URL;

if (!BASE_URL) {
  console.warn("EXPO_PUBLIC_BACKEND_URL is not set");
}

let inMemoryToken: string | null = null;

export function setAuthToken(token: string | null) {
  inMemoryToken = token;
}

export function getAuthToken(): string | null {
  return inMemoryToken;
}

export class ApiError extends Error {
  status: number;
  data: any;
  constructor(status: number, message: string, data?: any) {
    super(message);
    this.status = status;
    this.data = data;
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string> | undefined),
  };
  if (inMemoryToken) headers["Authorization"] = `Bearer ${inMemoryToken}`;
  const res = await fetch(`${BASE_URL}/api${path}`, { ...options, headers });
  const text = await res.text();
  let data: any = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  if (!res.ok) {
    const message =
      (data && (data.detail || data.message)) ||
      `Request failed (${res.status})`;
    throw new ApiError(res.status, String(message), data);
  }
  return data as T;
}

/**
 * Upload a local image file to a backend multipart endpoint.
 * `uri` is what expo-image-picker returns (native: file://... , web: blob://...).
 */
export async function apiUpload<T>(
  path: string,
  file: { uri: string; name: string; type: string },
): Promise<T> {
  const form = new FormData();
  if (Platform.OS === "web") {
    // On web, the blob: URI must be fetched and appended as a real Blob.
    const blob = await (await fetch(file.uri)).blob();
    form.append("file", blob, file.name);
  } else {
    // Native shape.
    form.append("file", { uri: file.uri, name: file.name, type: file.type } as any);
  }
  const headers: Record<string, string> = {};
  if (inMemoryToken) headers["Authorization"] = `Bearer ${inMemoryToken}`;
  // NEVER set Content-Type here — the runtime adds the multipart boundary.
  const res = await fetch(`${BASE_URL}/api${path}`, {
    method: "POST",
    headers,
    body: form as any,
  });
  const text = await res.text();
  let data: any = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = text;
  }
  if (!res.ok) {
    const message =
      (data && (data.detail || data.message)) ||
      `Upload failed (${res.status})`;
    throw new ApiError(res.status, String(message), data);
  }
  return data as T;
}

/**
 * Build an image URL the current session can render.
 * Native <Image source={{ uri, headers }} /> can send Authorization.
 * Web <img> cannot — so we tack the session token onto the query string
 * and the backend accepts it there too.
 */
export function getFileUrl(relativePath: string): {
  uri: string;
  headers?: Record<string, string>;
} {
  const absolute = `${BASE_URL}${relativePath}`;
  if (Platform.OS === "web") {
    const sep = absolute.includes("?") ? "&" : "?";
    return {
      uri: inMemoryToken ? `${absolute}${sep}token=${encodeURIComponent(inMemoryToken)}` : absolute,
    };
  }
  return {
    uri: absolute,
    headers: inMemoryToken ? { Authorization: `Bearer ${inMemoryToken}` } : undefined,
  };
}

/**
 * Absolute URL for the brief PDF endpoint, including token + watermark + lang.
 * Used for opening in a native viewer / new tab.
 */
export function getBriefPdfUrl(
  brief_id: string,
  watermark: boolean,
  lang?: string | null,
): string {
  const params = new URLSearchParams({
    watermark: watermark ? "true" : "false",
  });
  if (lang) params.set("lang", lang);
  if (inMemoryToken) params.set("token", inMemoryToken);
  return `${BASE_URL}/api/briefs/${encodeURIComponent(brief_id)}/pdf?${params.toString()}`;
}

/** Absolute URL for the public share page — safe to render into a QR. */
export function getPublicUrl(sharePath: string, lang?: string | null): string {
  if (!lang) return `${BASE_URL}${sharePath}`;
  const sep = sharePath.includes("?") ? "&" : "?";
  return `${BASE_URL}${sharePath}${sep}lang=${encodeURIComponent(lang)}`;
}

export const api = {
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  post: <T>(path: string, body?: any) =>
    request<T>(path, { method: "POST", body: JSON.stringify(body ?? {}) }),
  put: <T>(path: string, body?: any) =>
    request<T>(path, { method: "PUT", body: JSON.stringify(body ?? {}) }),
  patch: <T>(path: string, body?: any) =>
    request<T>(path, { method: "PATCH", body: JSON.stringify(body ?? {}) }),
  del: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};

export { BASE_URL, Platform };
