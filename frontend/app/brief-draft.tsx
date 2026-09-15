import { useMemo, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Image } from "expo-image";
import { useLocalSearchParams, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import Ionicons from "@react-native-vector-icons/ionicons";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { Brief, briefs, DOC_TYPES } from "@/src/api/briefs";
import { getFileUrl } from "@/src/api/client";
import { colors, radius, spacing } from "@/src/theme";

export default function BriefDraft() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const { brief_id } = useLocalSearchParams<{ brief_id: string }>();

  const briefQuery = useQuery({
    queryKey: ["brief", brief_id],
    queryFn: () => briefs.get(brief_id!),
    enabled: !!brief_id,
    // Poll while classification hasn't landed yet.
    refetchInterval: (query) => {
      const b = query.state.data as Brief | undefined;
      if (!b) return 2000;
      if (b.detected_doc_type === null && b.photos.length > 0) return 2500;
      return false;
    },
  });

  const brief = briefQuery.data;
  const [pendingDelete, setPendingDelete] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [detecting, setDetecting] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const docType = brief?.doc_type ?? "other";
  const photoCount = brief?.photos.length ?? 0;
  const detectedLabel = useMemo(() => {
    if (!brief?.detected_doc_type) return null;
    return DOC_TYPES.find((d) => d.key === brief.detected_doc_type)?.label ?? "Other";
  }, [brief?.detected_doc_type]);
  const showDetectionHint =
    !!brief &&
    !brief.doc_type_manual_override &&
    !!brief.detected_doc_type &&
    photoCount > 0;

  const setBrief = (b: Brief) => queryClient.setQueryData(["brief", brief_id], b);

  const setDocType = async (dt: Brief["doc_type"]) => {
    if (!brief || dt === brief.doc_type) return;
    setError(null);
    // optimistic — flip type AND set override so hint hides immediately
    setBrief({ ...brief, doc_type: dt, doc_type_manual_override: true });
    try {
      const updated = await briefs.update(brief.brief_id, { doc_type: dt });
      setBrief(updated);
    } catch (e: any) {
      setError(e?.message ?? "Could not update doc type");
      setBrief(brief);
    }
  };

  const redetect = async () => {
    if (!brief || photoCount === 0 || detecting) return;
    setError(null);
    setDetecting(true);
    try {
      const updated = await briefs.detectDocType(brief.brief_id);
      setBrief(updated);
    } catch (e: any) {
      setError(e?.message ?? "Could not detect doc type");
    } finally {
      setDetecting(false);
    }
  };

  const removePhoto = async (photo_id: string) => {
    if (!brief) return;
    setPendingDelete(photo_id);
    try {
      const updated = await briefs.deletePhoto(brief.brief_id, photo_id);
      setBrief(updated);
    } catch (e: any) {
      setError(e?.message ?? "Could not delete photo");
    } finally {
      setPendingDelete(null);
    }
  };

  const saveDraft = async () => {
    if (!brief) return;
    setSaving(true);
    setError(null);
    try {
      // Ensure it's persisted as draft. (Backend already keeps it as draft.)
      queryClient.invalidateQueries({ queryKey: ["briefs"] });
      router.replace("/dashboard");
    } finally {
      setSaving(false);
    }
  };

  const generateBrief = async () => {
    if (!brief || photoCount === 0 || generating) return;
    setGenerating(true);
    setError(null);
    try {
      const updated = await briefs.generate(brief.brief_id);
      setBrief(updated);
      queryClient.invalidateQueries({ queryKey: ["briefs"] });
      router.replace(`/brief-view?brief_id=${updated.brief_id}`);
    } catch (e: any) {
      setError(e?.message ?? "Could not generate brief");
    } finally {
      setGenerating(false);
    }
  };

  const discard = async () => {
    if (!brief) {
      router.replace("/dashboard");
      return;
    }
    try {
      await briefs.del(brief.brief_id);
    } catch {
      /* ignore */
    }
    queryClient.invalidateQueries({ queryKey: ["briefs"] });
    router.replace("/dashboard");
  };

  const addMore = () => {
    if (!brief) return;
    router.push(`/capture?brief_id=${brief.brief_id}`);
  };

  const rendered = useMemo(() => brief?.photos ?? [], [brief]);

  if (briefQuery.isLoading || !brief) {
    return (
      <View style={styles.loadingContainer} testID="brief-draft-loading">
        <ActivityIndicator color={colors.brandPrimary} />
      </View>
    );
  }

  return (
    <View style={styles.container} testID="brief-draft-screen">
      <View
        style={[
          styles.header,
          { paddingTop: insets.top + spacing.md },
        ]}
      >
        <Pressable
          testID="brief-draft-back-btn"
          onPress={() => router.replace("/dashboard")}
          style={styles.iconBtn}
          hitSlop={12}
        >
          <Ionicons name="chevron-back" size={22} color={colors.onSurface} />
        </Pressable>
        <View style={styles.headerText}>
          <Text style={styles.eyebrow}>DRAFT BRIEF</Text>
          <Text style={styles.title}>Review & label</Text>
        </View>
        <Pressable
          testID="brief-draft-discard-btn"
          onPress={discard}
          hitSlop={8}
          style={styles.iconBtn}
        >
          <Ionicons name="trash-outline" size={20} color={colors.error} />
        </Pressable>
      </View>

      <ScrollView
        contentContainerStyle={[
          styles.scroll,
          { paddingBottom: insets.bottom + 140 },
        ]}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text style={styles.sectionLabel}>Document type</Text>
            <Pressable
              testID="brief-redetect-btn"
              onPress={redetect}
              disabled={photoCount === 0 || detecting}
              style={[
                styles.detectBtn,
                (photoCount === 0 || detecting) && styles.detectBtnDim,
              ]}
              hitSlop={6}
            >
              {detecting ? (
                <ActivityIndicator color={colors.brandPrimary} size="small" />
              ) : (
                <Ionicons
                  name="sparkles"
                  size={13}
                  color={colors.brandPrimary}
                />
              )}
              <Text style={styles.detectBtnText}>
                {detecting ? "Detecting…" : "Detect"}
              </Text>
            </Pressable>
          </View>
          {showDetectionHint ? (
            <Pressable
              testID="brief-detection-hint"
              onPress={() => {}}
              style={styles.hintRow}
            >
              <Ionicons name="sparkles" size={14} color={colors.brandPrimary} />
              <Text style={styles.hintText}>
                Detected:{" "}
                <Text style={styles.hintTextBold}>{detectedLabel}</Text>
                {brief?.detected_confidence === "low"
                  ? " (low confidence)"
                  : ""}
                {"  "}— tap a chip to change
              </Text>
            </Pressable>
          ) : null}
          <View style={styles.chipsRow}>
            {DOC_TYPES.map((dt) => {
              const selected = docType === dt.key;
              return (
                <Pressable
                  key={dt.key}
                  testID={`brief-doctype-${dt.key}`}
                  onPress={() => setDocType(dt.key)}
                  style={[styles.chip, selected && styles.chipSelected]}
                >
                  <Ionicons
                    name={dt.icon as any}
                    size={14}
                    color={selected ? colors.brandPrimary : colors.onSurfaceSecondary}
                  />
                  <Text
                    style={[
                      styles.chipText,
                      selected && styles.chipTextSelected,
                    ]}
                  >
                    {dt.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>

        <View style={styles.section}>
          <View style={styles.sectionHeader}>
            <Text style={styles.sectionLabel}>
              Photos ({photoCount})
            </Text>
            <Pressable
              testID="brief-add-more-btn"
              onPress={addMore}
              style={styles.addBtn}
            >
              <Ionicons name="add" size={16} color={colors.brandPrimary} />
              <Text style={styles.addBtnText}>Add more</Text>
            </Pressable>
          </View>

          {rendered.length === 0 ? (
            <View style={styles.emptyPhotos} testID="brief-empty-photos">
              <Ionicons name="images-outline" size={32} color={colors.muted} />
              <Text style={styles.emptyPhotosText}>
                No photos left. Add some or discard this draft.
              </Text>
            </View>
          ) : (
            <FlatList
              data={rendered}
              scrollEnabled={false}
              numColumns={2}
              keyExtractor={(p) => p.photo_id}
              columnWrapperStyle={{ gap: spacing.md }}
              contentContainerStyle={{ gap: spacing.md }}
              renderItem={({ item, index }) => {
                const src = getFileUrl(item.url);
                const isDeleting = pendingDelete === item.photo_id;
                return (
                  <View style={styles.photoCard} testID={`brief-photo-${item.photo_id}`}>
                    <Image
                      source={src as any}
                      style={styles.photoImage}
                      contentFit="cover"
                      transition={150}
                    />
                    <View style={styles.photoIndex}>
                      <Text style={styles.photoIndexText}>{index + 1}</Text>
                    </View>
                    <Pressable
                      testID={`brief-photo-delete-${item.photo_id}`}
                      onPress={() => removePhoto(item.photo_id)}
                      style={styles.photoDelete}
                      disabled={isDeleting}
                      hitSlop={6}
                    >
                      {isDeleting ? (
                        <ActivityIndicator color={colors.onError} size="small" />
                      ) : (
                        <Ionicons name="close" size={16} color={colors.onError} />
                      )}
                    </Pressable>
                  </View>
                );
              }}
            />
          )}
        </View>

        {error ? (
          <Text style={styles.errorText} testID="brief-draft-error">
            {error}
          </Text>
        ) : null}
      </ScrollView>

      <View
        style={[styles.footer, { paddingBottom: insets.bottom + spacing.lg }]}
      >
        {brief.content ? (
          <Pressable
            testID="brief-draft-view-btn"
            onPress={() => router.replace(`/brief-view?brief_id=${brief.brief_id}`)}
            style={({ pressed }) => [styles.primaryBtn, pressed && { opacity: 0.85 }]}
          >
            <Ionicons name="document-text-outline" size={18} color={colors.onBrandPrimary} />
            <Text style={styles.primaryBtnText}>View brief</Text>
          </Pressable>
        ) : photoCount > 0 ? (
          <>
            <Pressable
              testID="brief-draft-generate-btn"
              onPress={generateBrief}
              disabled={generating}
              style={({ pressed }) => [
                styles.primaryBtn,
                (generating || pressed) && styles.primaryBtnDim,
              ]}
            >
              {generating ? (
                <>
                  <ActivityIndicator color={colors.onBrandPrimary} />
                  <Text style={styles.primaryBtnText}>Reading pages…</Text>
                </>
              ) : (
                <>
                  <Ionicons name="sparkles" size={18} color={colors.onBrandPrimary} />
                  <Text style={styles.primaryBtnText}>Generate brief</Text>
                </>
              )}
            </Pressable>
            <Pressable
              testID="brief-draft-save-btn"
              onPress={saveDraft}
              disabled={saving || generating}
              style={styles.secondaryBtn}
            >
              <Text style={styles.secondaryBtnText}>Save draft for later</Text>
            </Pressable>
          </>
        ) : (
          <Pressable
            testID="brief-draft-save-btn"
            onPress={saveDraft}
            disabled
            style={[styles.primaryBtn, styles.primaryBtnDim]}
          >
            <Text style={styles.primaryBtnText}>Add a photo to continue</Text>
          </Pressable>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: colors.surface,
  },
  container: { flex: 1, backgroundColor: colors.surface },
  header: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: spacing.xl,
    paddingBottom: spacing.lg,
    gap: spacing.md,
  },
  iconBtn: {
    width: 40,
    height: 40,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceSecondary,
    alignItems: "center",
    justifyContent: "center",
  },
  headerText: { flex: 1 },
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
  scroll: {
    paddingHorizontal: spacing.xl,
    gap: spacing.xl,
  },
  section: { gap: spacing.md },
  sectionHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  sectionLabel: {
    color: colors.onSurfaceTertiary,
    fontSize: 12,
    letterSpacing: 1.5,
    fontWeight: "700",
    textTransform: "uppercase",
  },
  chipsRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: spacing.sm,
  },
  chip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: spacing.md,
    paddingVertical: 10,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceSecondary,
    borderWidth: 1,
    borderColor: colors.border,
  },
  chipSelected: {
    backgroundColor: colors.brandTertiary,
    borderColor: colors.brandPrimary,
  },
  chipText: { color: colors.onSurfaceSecondary, fontSize: 13, fontWeight: "600" },
  chipTextSelected: { color: colors.brandPrimary },
  detectBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: spacing.md,
    paddingVertical: 8,
    borderRadius: radius.pill,
    backgroundColor: colors.brandTertiary,
    borderWidth: 1,
    borderColor: colors.brandSecondary,
  },
  detectBtnDim: { opacity: 0.55 },
  detectBtnText: {
    color: colors.brandPrimary,
    fontSize: 12,
    fontWeight: "700",
    letterSpacing: 0.3,
  },
  hintRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: spacing.md,
    paddingVertical: 10,
    borderRadius: radius.md,
    backgroundColor: colors.brandTertiary,
    borderWidth: 1,
    borderColor: colors.brandSecondary,
  },
  hintText: {
    color: colors.onSurface,
    fontSize: 13,
    flex: 1,
    lineHeight: 18,
  },
  hintTextBold: { fontWeight: "700", color: colors.brandPrimary },
  addBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: spacing.md,
    paddingVertical: 8,
    borderRadius: radius.pill,
    backgroundColor: colors.brandTertiary,
  },
  addBtnText: { color: colors.brandPrimary, fontSize: 13, fontWeight: "700" },
  emptyPhotos: {
    alignItems: "center",
    gap: spacing.sm,
    padding: spacing.xl,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceSecondary,
    borderWidth: 1,
    borderColor: colors.border,
  },
  emptyPhotosText: { color: colors.muted, fontSize: 13, textAlign: "center" },
  photoCard: {
    flex: 1,
    aspectRatio: 3 / 4,
    borderRadius: radius.md,
    overflow: "hidden",
    backgroundColor: colors.surfaceSecondary,
    borderWidth: 1,
    borderColor: colors.border,
  },
  photoImage: { width: "100%", height: "100%" },
  photoIndex: {
    position: "absolute",
    top: 6,
    left: 6,
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: "rgba(17,24,21,0.7)",
    alignItems: "center",
    justifyContent: "center",
  },
  photoIndexText: { color: "#FFFFFF", fontSize: 11, fontWeight: "700" },
  photoDelete: {
    position: "absolute",
    top: 6,
    right: 6,
    width: 26,
    height: 26,
    borderRadius: 13,
    backgroundColor: colors.error,
    alignItems: "center",
    justifyContent: "center",
  },
  errorText: { color: colors.error, fontSize: 14, textAlign: "center" },
  footer: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    backgroundColor: "rgba(255,255,255,0.96)",
    borderTopWidth: 1,
    borderTopColor: colors.border,
  },
  primaryBtn: {
    backgroundColor: colors.brandPrimary,
    borderRadius: radius.pill,
    paddingVertical: 18,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: spacing.sm,
    minHeight: 56,
  },
  primaryBtnDim: { opacity: 0.55 },
  primaryBtnText: { color: colors.onBrandPrimary, fontSize: 16, fontWeight: "700" },
  secondaryBtn: {
    marginTop: spacing.sm,
    paddingVertical: 12,
    alignItems: "center",
  },
  secondaryBtnText: { color: colors.muted, fontSize: 13, fontWeight: "600" },
});
