import { Platform } from "react-native";

const BASE_URL = process.env.EXPO_PUBLIC_BACKEND_URL;

if (!BASE_URL) {
  // Fail fast so config issues are obvious in dev.
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
  if (inMemoryToken) {
    headers["Authorization"] = `Bearer ${inMemoryToken}`;
  }
  const res = await fetch(`${BASE_URL}/api${path}`, {
    ...options,
    headers,
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
      `Request failed (${res.status})`;
    throw new ApiError(res.status, String(message), data);
  }
  return data as T;
}

export const api = {
  get: <T>(path: string) => request<T>(path, { method: "GET" }),
  post: <T>(path: string, body?: any) =>
    request<T>(path, { method: "POST", body: JSON.stringify(body ?? {}) }),
  put: <T>(path: string, body?: any) =>
    request<T>(path, { method: "PUT", body: JSON.stringify(body ?? {}) }),
  del: <T>(path: string) => request<T>(path, { method: "DELETE" }),
};

export { BASE_URL, Platform };
