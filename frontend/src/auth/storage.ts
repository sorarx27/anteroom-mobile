import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";

const TOKEN_KEY = "anteroom_session_token";

export async function saveToken(token: string): Promise<void> {
  if (Platform.OS === "web") {
    try {
      window.localStorage.setItem(TOKEN_KEY, token);
    } catch {}
    return;
  }
  await SecureStore.setItemAsync(TOKEN_KEY, token);
}

export async function loadToken(): Promise<string | null> {
  if (Platform.OS === "web") {
    try {
      return window.localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  }
  return await SecureStore.getItemAsync(TOKEN_KEY);
}

export async function clearToken(): Promise<void> {
  if (Platform.OS === "web") {
    try {
      window.localStorage.removeItem(TOKEN_KEY);
    } catch {}
    return;
  }
  await SecureStore.deleteItemAsync(TOKEN_KEY);
}
