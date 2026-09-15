import * as ImagePicker from "expo-image-picker";
import { Linking } from "react-native";

export type PermissionOutcome =
  | { granted: true }
  | { granted: false; canAskAgain: boolean; blocked: boolean };

export async function requestCameraPermission(): Promise<PermissionOutcome> {
  const current = await ImagePicker.getCameraPermissionsAsync();
  if (current.status === "granted") return { granted: true };
  if (!current.canAskAgain) {
    return { granted: false, canAskAgain: false, blocked: true };
  }
  const res = await ImagePicker.requestCameraPermissionsAsync();
  if (res.status === "granted") return { granted: true };
  return {
    granted: false,
    canAskAgain: res.canAskAgain ?? false,
    blocked: !(res.canAskAgain ?? false),
  };
}

export async function requestGalleryPermission(): Promise<PermissionOutcome> {
  const current = await ImagePicker.getMediaLibraryPermissionsAsync();
  if (current.status === "granted") return { granted: true };
  if (!current.canAskAgain) {
    return { granted: false, canAskAgain: false, blocked: true };
  }
  const res = await ImagePicker.requestMediaLibraryPermissionsAsync();
  if (res.status === "granted") return { granted: true };
  return {
    granted: false,
    canAskAgain: res.canAskAgain ?? false,
    blocked: !(res.canAskAgain ?? false),
  };
}

export function openAppSettings() {
  Linking.openSettings().catch(() => {});
}
