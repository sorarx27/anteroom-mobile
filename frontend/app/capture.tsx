import { useState, useEffect } from "react";
import {
  ActivityIndicator,
  Modal,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import Ionicons from "@react-native-vector-icons/ionicons";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import * as ImagePicker from "expo-image-picker";

import { briefs } from "@/src/api/briefs";
import {
  openAppSettings,
  requestCameraPermission,
  requestGalleryPermission,
} from "@/src/utils/permissions";
import { ApiError } from "@/src/api/client";
import { colors, radius, spacing } from "@/src/theme";

type PermissionPromptState = {
  visible: boolean;
  type: "camera" | "gallery";
  blocked: boolean;
} | null;

export default function Capture() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { brief_id: routeBriefId } = useLocalSearchParams<{ brief_id?: string }>();

  const [briefId, setBriefId] = useState<string | null>(routeBriefId ?? null);
  const [preparing, setPreparing] = useState(!routeBriefId);
  const [uploadingCount, setUploadingCount] = useState(0);
  const [totalPhotos, setTotalPhotos] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [permissionPrompt, setPermissionPrompt] = useState<PermissionPromptState>(null);

  // If we arrived without a brief_id, create a fresh draft.
  useEffect(() => {
    if (briefId) return;
    let mounted = true;
    (async () => {
      try {
        const created = await briefs.create("other");
        if (mounted) {
          setBriefId(created.brief_id);
          setTotalPhotos(created.photos.length);
        }
      } catch (e: any) {
        if (mounted) setError(e?.message ?? "Could not create brief");
      } finally {
        if (mounted) setPreparing(false);
      }
    })();
    return () => {
      mounted = false;
    };
  }, [briefId]);

  const uploadAssets = async (assets: ImagePicker.ImagePickerAsset[]) => {
    if (!briefId) return;
    setError(null);
    for (const asset of assets) {
      setUploadingCount((c) => c + 1);
      try {
        const name =
          asset.fileName ??
          `photo_${Date.now()}.${(asset.uri.split(".").pop() || "jpg").split("?")[0]}`;
        const type = asset.mimeType || guessMime(name);
        const updated = await briefs.uploadPhoto(briefId, {
          uri: asset.uri,
          name,
          type,
        });
        setTotalPhotos(updated.photos.length);
      } catch (e: any) {
        setError(
          e instanceof ApiError && e.status === 413
            ? "One of the photos is too large (max 8 MB)"
            : e?.message ?? "Upload failed",
        );
      } finally {
        setUploadingCount((c) => Math.max(0, c - 1));
      }
    }
  };

  const takePhoto = async () => {
    const perm = await requestCameraPermission();
    if (!perm.granted) {
      setPermissionPrompt({
        visible: true,
        type: "camera",
        blocked: perm.blocked,
      });
      return;
    }
    const result = await ImagePicker.launchCameraAsync({
      mediaTypes: ["images"],
      quality: 0.8,
      allowsEditing: false,
      cameraType: ImagePicker.CameraType.back,
    });
    if (!result.canceled && result.assets?.length) {
      await uploadAssets(result.assets);
    }
  };

  const pickFromGallery = async () => {
    const perm = await requestGalleryPermission();
    if (!perm.granted) {
      setPermissionPrompt({
        visible: true,
        type: "gallery",
        blocked: perm.blocked,
      });
      return;
    }
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ["images"],
      quality: 0.8,
      allowsMultipleSelection: true,
      selectionLimit: 10,
    });
    if (!result.canceled && result.assets?.length) {
      await uploadAssets(result.assets);
    }
  };

  const goToReview = () => {
    if (!briefId) return;
    router.replace(`/brief-draft?brief_id=${briefId}`);
  };

  const cancel = () => {
    if (briefId && totalPhotos === 0) {
      briefs.del(briefId).catch(() => {});
    }
    router.replace("/dashboard");
  };

  return (
    <View style={styles.container} testID="capture-screen">
      <View
        style={[
          styles.header,
          { paddingTop: insets.top + spacing.md, paddingBottom: spacing.lg },
        ]}
      >
        <Pressable
          testID="capture-cancel-btn"
          onPress={cancel}
          style={styles.iconBtn}
          hitSlop={12}
        >
          <Ionicons name="close" size={22} color={colors.onSurface} />
        </Pressable>
        <View style={styles.headerText}>
          <Text style={styles.eyebrow}>NEW BRIEF</Text>
          <Text style={styles.title}>Snap your documents</Text>
        </View>
        <View style={{ width: 40 }} />
      </View>

      <View style={styles.body}>
        <View style={styles.hero}>
          <View style={styles.heroIconWrap}>
            <Ionicons name="images-outline" size={44} color={colors.brandPrimary} />
          </View>
          <Text style={styles.heroTitle} testID="capture-status">
            {totalPhotos > 0
              ? `${totalPhotos} photo${totalPhotos === 1 ? "" : "s"} attached`
              : "Add referral letters, med lists, or lab photos"}
          </Text>
          <Text style={styles.heroBody}>
            Snap a page or pick a few from your gallery. You'll be able to review and delete before saving.
          </Text>
        </View>

        {error ? (
          <Text style={styles.errorText} testID="capture-error">
            {error}
          </Text>
        ) : null}

        <View style={styles.actionRow}>
          <Pressable
            testID="capture-camera-btn"
            onPress={takePhoto}
            disabled={preparing || !briefId}
            style={({ pressed }) => [
              styles.actionBtn,
              styles.actionPrimary,
              pressed && styles.pressed,
              (preparing || !briefId) && styles.actionDim,
            ]}
          >
            <Ionicons name="camera" size={22} color={colors.onBrandPrimary} />
            <Text style={styles.actionPrimaryText}>Take photo</Text>
          </Pressable>
          <Pressable
            testID="capture-gallery-btn"
            onPress={pickFromGallery}
            disabled={preparing || !briefId}
            style={({ pressed }) => [
              styles.actionBtn,
              styles.actionSecondary,
              pressed && styles.pressed,
              (preparing || !briefId) && styles.actionDim,
            ]}
          >
            <Ionicons name="image" size={22} color={colors.brandPrimary} />
            <Text style={styles.actionSecondaryText}>From gallery</Text>
          </Pressable>
        </View>

        {uploadingCount > 0 ? (
          <View style={styles.uploadingRow} testID="capture-uploading">
            <ActivityIndicator color={colors.brandPrimary} />
            <Text style={styles.uploadingText}>
              Uploading {uploadingCount} photo{uploadingCount === 1 ? "" : "s"}…
            </Text>
          </View>
        ) : null}
      </View>

      <View
        style={[styles.footer, { paddingBottom: insets.bottom + spacing.lg }]}
      >
        <Pressable
          testID="capture-next-btn"
          onPress={goToReview}
          disabled={totalPhotos === 0 || uploadingCount > 0}
          style={({ pressed }) => [
            styles.primaryBtn,
            (totalPhotos === 0 || uploadingCount > 0 || pressed) && styles.primaryBtnDim,
          ]}
        >
          <Text style={styles.primaryBtnText}>
            {totalPhotos > 0 ? `Review ${totalPhotos} photo${totalPhotos === 1 ? "" : "s"}` : "Add a photo to continue"}
          </Text>
        </Pressable>
      </View>

      <Modal
        visible={!!permissionPrompt?.visible}
        transparent
        animationType="fade"
        onRequestClose={() => setPermissionPrompt(null)}
      >
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>
              {permissionPrompt?.type === "camera"
                ? "Camera access needed"
                : "Photos access needed"}
            </Text>
            <Text style={styles.modalBody}>
              {permissionPrompt?.type === "camera"
                ? "Anteroom needs camera access to snap medical documents."
                : "Anteroom needs access to your photos to attach existing pictures."}
              {permissionPrompt?.blocked
                ? "\n\nEnable it in Settings to continue."
                : ""}
            </Text>
            <View style={styles.modalActions}>
              <Pressable
                testID="permission-cancel-btn"
                style={styles.modalGhost}
                onPress={() => setPermissionPrompt(null)}
              >
                <Text style={styles.modalGhostText}>Not now</Text>
              </Pressable>
              {permissionPrompt?.blocked ? (
                <Pressable
                  testID="permission-settings-btn"
                  style={styles.modalCta}
                  onPress={() => {
                    openAppSettings();
                    setPermissionPrompt(null);
                  }}
                >
                  <Text style={styles.modalCtaText}>Open Settings</Text>
                </Pressable>
              ) : null}
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

function guessMime(name: string): string {
  const ext = name.split(".").pop()?.toLowerCase() ?? "";
  if (ext === "png") return "image/png";
  if (ext === "webp") return "image/webp";
  return "image/jpeg";
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.surface },
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: spacing.xl,
    gap: spacing.md,
  },
  headerText: { flex: 1 },
  iconBtn: {
    width: 40,
    height: 40,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceSecondary,
    alignItems: "center",
    justifyContent: "center",
  },
  eyebrow: {
    color: colors.brandPrimary,
    fontSize: 11,
    letterSpacing: 2,
    fontWeight: "700",
  },
  title: {
    color: colors.onSurface,
    fontSize: 22,
    fontWeight: "700",
    letterSpacing: -0.3,
  },
  body: {
    flex: 1,
    paddingHorizontal: spacing.xl,
    gap: spacing.xl,
    justifyContent: "center",
  },
  hero: { alignItems: "center", gap: spacing.md, paddingHorizontal: spacing.md },
  heroIconWrap: {
    width: 96,
    height: 96,
    borderRadius: radius.lg,
    backgroundColor: colors.brandTertiary,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.sm,
  },
  heroTitle: {
    color: colors.onSurface,
    fontSize: 20,
    fontWeight: "700",
    textAlign: "center",
    letterSpacing: -0.2,
  },
  heroBody: {
    color: colors.muted,
    fontSize: 14,
    lineHeight: 20,
    textAlign: "center",
    maxWidth: 320,
  },
  errorText: { color: colors.error, fontSize: 14, textAlign: "center" },
  actionRow: {
    flexDirection: "row",
    gap: spacing.md,
  },
  actionBtn: {
    flex: 1,
    borderRadius: radius.md,
    paddingVertical: 16,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: spacing.sm,
    borderWidth: 1,
  },
  actionPrimary: {
    backgroundColor: colors.brandPrimary,
    borderColor: colors.brandPrimary,
  },
  actionPrimaryText: { color: colors.onBrandPrimary, fontSize: 15, fontWeight: "700" },
  actionSecondary: {
    backgroundColor: colors.surfaceSecondary,
    borderColor: colors.border,
  },
  actionSecondaryText: { color: colors.brandPrimary, fontSize: 15, fontWeight: "700" },
  actionDim: { opacity: 0.55 },
  pressed: { opacity: 0.85 },
  uploadingRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.sm,
  },
  uploadingText: { color: colors.muted, fontSize: 13 },
  footer: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    backgroundColor: colors.surface,
  },
  primaryBtn: {
    backgroundColor: colors.brandPrimary,
    borderRadius: radius.pill,
    paddingVertical: 18,
    alignItems: "center",
    justifyContent: "center",
    minHeight: 56,
  },
  primaryBtnDim: { opacity: 0.55 },
  primaryBtnText: { color: colors.onBrandPrimary, fontSize: 16, fontWeight: "700" },
  modalBackdrop: {
    flex: 1,
    backgroundColor: "rgba(17,24,21,0.5)",
    alignItems: "center",
    justifyContent: "center",
    padding: spacing.xl,
  },
  modalCard: {
    width: "100%",
    maxWidth: 360,
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    padding: spacing.xl,
    gap: spacing.md,
  },
  modalTitle: { color: colors.onSurface, fontSize: 18, fontWeight: "700" },
  modalBody: { color: colors.onSurfaceSecondary, fontSize: 14, lineHeight: 20 },
  modalActions: { flexDirection: "row", gap: spacing.md, marginTop: spacing.sm },
  modalGhost: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceSecondary,
    alignItems: "center",
  },
  modalGhostText: { color: colors.onSurfaceSecondary, fontSize: 15, fontWeight: "600" },
  modalCta: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: radius.pill,
    backgroundColor: colors.brandPrimary,
    alignItems: "center",
  },
  modalCtaText: { color: colors.onBrandPrimary, fontSize: 15, fontWeight: "700" },
});
