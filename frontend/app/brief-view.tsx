import { useMemo } from "react";
import {
  ActivityIndicator,
  Linking,
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
import QRCode from "react-native-qrcode-svg";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { briefs, DOC_TYPES } from "@/src/api/briefs";
import { getBriefPdfUrl, getFileUrl, getPublicUrl } from "@/src/api/client";
import { useSubscription } from "@/src/lib/revenuecat";
import { colors, radius, spacing } from "@/src/theme";

function docLabel(dt: string): string {
  return DOC_TYPES.find((d) => d.key === (dt as any))?.label ?? "Other";
}

export default function BriefView() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { brief_id } = useLocalSearchParams<{ brief_id: string }>();
  const { isSubscribed } = useSubscription();
  const queryClient = useQueryClient();

  const briefQuery = useQuery({
    queryKey: ["brief", brief_id],
    queryFn: () => briefs.get(brief_id!),
    enabled: !!brief_id,
  });

  const brief = briefQuery.data;
  const content = brief?.content;

  const shareUrl = useMemo(
    () => (brief?.share_url_path ? getPublicUrl(brief.share_url_path) : null),
    [brief?.share_url_path],
  );

  const openPdf = () => {
    if (!brief) return;
    const url = getBriefPdfUrl(brief.brief_id, !isSubscribed);
    Linking.openURL(url).catch(() => {});
  };

  const openShare = () => {
    if (!shareUrl) return;
    Linking.openURL(shareUrl).catch(() => {});
  };

  if (briefQuery.isLoading || !brief) {
    return (
      <View style={styles.loadingContainer} testID="brief-view-loading">
        <ActivityIndicator color={colors.brandPrimary} />
      </View>
    );
  }

  if (!content) {
    // Brief hasn't been generated yet — bounce back to review
    router.replace(`/brief-draft?brief_id=${brief.brief_id}`);
    return null;
  }

  const patient = content.patient || {};
  const meds = content.medications || [];
  const allergies = content.allergies || [];
  const referral = content.referral_reason;
  const flagged = content.flagged_items || [];

  const nothingDetected =
    !patient.name &&
    !patient.dob &&
    !patient.sex &&
    !patient.id_number &&
    !referral &&
    meds.length === 0 &&
    allergies.length === 0;

  return (
    <View style={styles.container} testID="brief-view-screen">
      <View
        style={[
          styles.header,
          { paddingTop: insets.top + spacing.md },
        ]}
      >
        <Pressable
          testID="brief-view-back-btn"
          onPress={() => router.replace("/dashboard")}
          style={styles.iconBtn}
          hitSlop={12}
        >
          <Ionicons name="chevron-back" size={22} color={colors.onSurface} />
        </Pressable>
        <View style={styles.headerText}>
          <Text style={styles.eyebrow}>
            {docLabel(brief.doc_type).toUpperCase()}
          </Text>
          <Text style={styles.title}>Pre-visit brief</Text>
        </View>
        <Pressable
          testID="brief-view-edit-btn"
          onPress={() => router.push(`/brief-draft?brief_id=${brief.brief_id}`)}
          style={styles.iconBtn}
          hitSlop={12}
        >
          <Ionicons name="create-outline" size={20} color={colors.onSurface} />
        </Pressable>
      </View>

      {!isSubscribed ? (
        <Pressable
          testID="brief-view-upgrade-ribbon"
          onPress={() => router.push("/paywall?trigger=brief-view")}
          style={styles.ribbon}
        >
          <Ionicons name="sparkles" size={13} color={colors.onBrandPrimary} />
          <Text style={styles.ribbonText}>
            ANTEROOM FREE — Upgrade for clean export & translation
          </Text>
        </Pressable>
      ) : null}

      <ScrollView
        contentContainerStyle={[
          styles.scroll,
          { paddingBottom: insets.bottom + 140 },
        ]}
        showsVerticalScrollIndicator={false}
      >
        <Text style={styles.disclaimer}>
          Extracted directly from the patient's documents. No diagnosis, nothing invented — if a field isn't shown, it wasn't clearly on the page.
        </Text>

        {nothingDetected ? (
          <View style={styles.emptyCard} testID="brief-view-empty">
            <Ionicons name="scan-outline" size={28} color={colors.muted} />
            <Text style={styles.emptyTitle}>Nothing readable was extracted</Text>
            <Text style={styles.emptyBody}>
              The photos may be too blurry or cut off. Try re-shooting the pages with more light.
            </Text>
          </View>
        ) : null}

        {/* Patient */}
        <Section title="Patient">
          {patient.name || patient.dob || patient.sex || patient.id_number ? (
            <View style={styles.kvBlock}>
              {patient.name ? <Kv label="Name" value={patient.name} /> : null}
              {patient.dob ? <Kv label="DOB" value={patient.dob} /> : null}
              {patient.sex ? <Kv label="Sex" value={patient.sex} /> : null}
              {patient.id_number ? <Kv label="ID" value={patient.id_number} /> : null}
            </View>
          ) : (
            <NoneDetected />
          )}
        </Section>

        {/* Referral reason */}
        <Section title="Referral reason">
          {referral ? (
            <Text style={styles.paragraph}>{referral}</Text>
          ) : (
            <NoneDetected />
          )}
        </Section>

        {/* Medications */}
        <Section title={`Medications (${meds.length})`}>
          {meds.length ? (
            <View style={styles.list}>
              {meds.map((m, idx) => (
                <View key={idx} style={styles.medRow} testID={`brief-med-${idx}`}>
                  <Text style={styles.medName}>{m.name}</Text>
                  <Text style={styles.medMeta}>
                    {(m.dose || "—")}
                    {"  ·  "}
                    {(m.frequency || "—")}
                  </Text>
                </View>
              ))}
            </View>
          ) : (
            <NoneDetected />
          )}
        </Section>

        {/* Allergies */}
        <Section title={`Allergies (${allergies.length})`}>
          {allergies.length ? (
            <View style={styles.list}>
              {allergies.map((a, idx) => (
                <View key={idx} style={styles.medRow} testID={`brief-allergy-${idx}`}>
                  <Text style={styles.medName}>{a.substance}</Text>
                  {a.reaction ? (
                    <Text style={styles.medMeta}>{a.reaction}</Text>
                  ) : null}
                </View>
              ))}
            </View>
          ) : (
            <NoneDetected />
          )}
        </Section>

        {/* Flagged */}
        {flagged.length ? (
          <Section title="Flagged for review" tint>
            <View style={styles.list}>
              {flagged.map((f, idx) => (
                <View key={idx} style={styles.flagRow} testID={`brief-flagged-${idx}`}>
                  <Ionicons name="warning-outline" size={16} color={colors.error} />
                  <Text style={styles.flagText}>{f}</Text>
                </View>
              ))}
            </View>
          </Section>
        ) : null}

        {/* QR share */}
        {shareUrl ? (
          <View style={styles.qrCard} testID="brief-view-qr">
            <View style={styles.qrLeft}>
              <Text style={styles.qrTitle}>Share with your clinic</Text>
              <Text style={styles.qrBody}>
                Scan this code at reception to open the brief in a browser.
              </Text>
              <Pressable
                testID="brief-view-open-share"
                onPress={openShare}
                style={styles.qrLink}
              >
                <Ionicons name="open-outline" size={13} color={colors.brandPrimary} />
                <Text style={styles.qrLinkText}>Open link</Text>
              </Pressable>
            </View>
            <View style={styles.qrBox}>
              <QRCode value={shareUrl} size={110} color={colors.onSurface} backgroundColor="white" />
            </View>
          </View>
        ) : null}

        {/* Source pages */}
        {brief.photos.length ? (
          <Section title={`Source pages (${brief.photos.length})`}>
            <ScrollView horizontal showsHorizontalScrollIndicator={false}>
              <View style={styles.pagesRow}>
                {brief.photos.map((p, idx) => (
                  <View key={p.photo_id} style={styles.pageThumb} testID={`brief-view-page-${idx}`}>
                    <Image
                      source={getFileUrl(p.url) as any}
                      style={StyleSheet.absoluteFillObject}
                      contentFit="cover"
                      transition={150}
                    />
                  </View>
                ))}
              </View>
            </ScrollView>
          </Section>
        ) : null}
      </ScrollView>

      <View
        style={[styles.footer, { paddingBottom: insets.bottom + spacing.lg }]}
      >
        <Pressable
          testID="brief-view-pdf-btn"
          onPress={openPdf}
          style={({ pressed }) => [styles.primaryBtn, pressed && styles.pressed]}
        >
          <Ionicons name="download-outline" size={20} color={colors.onBrandPrimary} />
          <Text style={styles.primaryBtnText}>
            Download PDF {isSubscribed ? "" : "(watermarked)"}
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

function Section({
  title,
  tint,
  children,
}: {
  title: string;
  tint?: boolean;
  children: React.ReactNode;
}) {
  return (
    <View style={styles.section}>
      <Text style={[styles.sectionLabel, tint && { color: colors.error }]}>
        {title}
      </Text>
      <View>{children}</View>
    </View>
  );
}

function Kv({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.kvRow}>
      <Text style={styles.kvLabel}>{label}</Text>
      <Text style={styles.kvValue} numberOfLines={2}>
        {value}
      </Text>
    </View>
  );
}

function NoneDetected() {
  return (
    <Text style={styles.noneText}>Not detected on the pages.</Text>
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
    paddingBottom: spacing.md,
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
  ribbon: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 6,
    marginHorizontal: spacing.xl,
    marginBottom: spacing.md,
    paddingHorizontal: spacing.md,
    paddingVertical: 10,
    borderRadius: radius.pill,
    backgroundColor: colors.brandPrimary,
  },
  ribbonText: {
    color: colors.onBrandPrimary,
    fontSize: 12,
    fontWeight: "700",
    letterSpacing: 0.4,
  },
  scroll: {
    paddingHorizontal: spacing.xl,
    gap: spacing.xl,
  },
  disclaimer: {
    color: colors.muted,
    fontSize: 12,
    lineHeight: 17,
    textAlign: "center",
    marginBottom: spacing.sm,
  },
  section: { gap: spacing.md },
  sectionLabel: {
    color: colors.onSurfaceTertiary,
    fontSize: 12,
    letterSpacing: 1.5,
    fontWeight: "700",
    textTransform: "uppercase",
  },
  kvBlock: {
    gap: 4,
    padding: spacing.md,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceSecondary,
    borderWidth: 1,
    borderColor: colors.border,
  },
  kvRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "flex-start",
    paddingVertical: 6,
    gap: spacing.md,
  },
  kvLabel: { color: colors.muted, fontSize: 13, minWidth: 60 },
  kvValue: {
    color: colors.onSurface,
    fontSize: 14,
    fontWeight: "600",
    flex: 1,
    textAlign: "right",
  },
  paragraph: {
    color: colors.onSurface,
    fontSize: 15,
    lineHeight: 22,
    padding: spacing.md,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceSecondary,
    borderWidth: 1,
    borderColor: colors.border,
  },
  noneText: {
    color: colors.muted,
    fontSize: 13,
    fontStyle: "italic",
  },
  list: { gap: spacing.sm },
  medRow: {
    padding: spacing.md,
    borderRadius: radius.md,
    backgroundColor: colors.surfaceSecondary,
    borderWidth: 1,
    borderColor: colors.border,
    gap: 2,
  },
  medName: {
    color: colors.onSurface,
    fontSize: 15,
    fontWeight: "700",
  },
  medMeta: { color: colors.muted, fontSize: 13 },
  flagRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: spacing.sm,
    padding: spacing.md,
    borderRadius: radius.md,
    backgroundColor: "#FFF3F3",
    borderWidth: 1,
    borderColor: "#E6D0D0",
  },
  flagText: {
    color: colors.error,
    fontSize: 13,
    flex: 1,
    lineHeight: 18,
    fontWeight: "600",
  },
  qrCard: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.lg,
    padding: spacing.lg,
    borderRadius: radius.md,
    backgroundColor: colors.brandTertiary,
    borderWidth: 1,
    borderColor: colors.brandSecondary,
  },
  qrLeft: { flex: 1, gap: 6 },
  qrTitle: { color: colors.onSurface, fontSize: 15, fontWeight: "700" },
  qrBody: { color: colors.muted, fontSize: 12, lineHeight: 17 },
  qrLink: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    marginTop: spacing.sm,
    alignSelf: "flex-start",
  },
  qrLinkText: {
    color: colors.brandPrimary,
    fontSize: 12,
    fontWeight: "700",
  },
  qrBox: {
    padding: spacing.sm,
    borderRadius: radius.sm,
    backgroundColor: "#FFFFFF",
  },
  pagesRow: { flexDirection: "row", gap: spacing.md },
  pageThumb: {
    width: 80,
    height: 108,
    borderRadius: radius.sm,
    backgroundColor: colors.surfaceSecondary,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: colors.border,
  },
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
  pressed: { opacity: 0.85 },
  primaryBtnText: { color: colors.onBrandPrimary, fontSize: 16, fontWeight: "700" },
});
