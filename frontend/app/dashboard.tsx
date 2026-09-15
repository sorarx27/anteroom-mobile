import { useCallback } from "react";
import {
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { Image } from "expo-image";
import Ionicons from "@react-native-vector-icons/ionicons";
import { useFocusEffect, useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { Brief, briefs, DOC_TYPES } from "@/src/api/briefs";
import { getFileUrl } from "@/src/api/client";
import { useAuth } from "@/src/auth/AuthContext";
import { useSubscription } from "@/src/lib/revenuecat";
import { colors, radius, spacing } from "@/src/theme";

const CLIPBOARD_IMG =
  "https://images.unsplash.com/photo-1651760680066-db9d32bd0357?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA1MDV8MHwxfHNlYXJjaHwxfHxlbXB0eSUyMG1lZGljYWwlMjBjbGlwYm9hcmQlMjBjbGVhbnxlbnwwfHx8fDE3ODk0ODU2MDR8MA&ixlib=rb-4.1.0&q=85";

const LOCKED_FEATURES = [
  { key: "clean-export", icon: "document-text-outline", label: "Clean export" },
  { key: "family-profiles", icon: "people-outline", label: "Family profiles" },
  { key: "translate", icon: "language-outline", label: "Translate" },
];

function docTypeLabel(dt: Brief["doc_type"]): { label: string; icon: string } {
  return DOC_TYPES.find((d) => d.key === dt) ?? DOC_TYPES[3];
}

function relativeTime(iso: string): string {
  const diffMs = Date.now() - new Date(iso).getTime();
  const s = Math.max(1, Math.floor(diffMs / 1000));
  if (s < 60) return `${s}s ago`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  const d = Math.floor(h / 24);
  return `${d}d ago`;
}

export default function Dashboard() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { user, signOut } = useAuth();
  const { isSubscribed } = useSubscription();

  const listQuery = useQuery({
    queryKey: ["briefs"],
    queryFn: () => briefs.list(),
  });

  useFocusEffect(
    useCallback(() => {
      queryClient.invalidateQueries({ queryKey: ["briefs"] });
    }, [queryClient]),
  );

  const initials = (user?.name ?? user?.email ?? "A")
    .split(" ")
    .map((s) => s.trim()[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();

  const items = listQuery.data ?? [];
  const openPaywall = (trigger: string) => router.push(`/paywall?trigger=${trigger}`);
  const createBrief = () => router.push("/capture");

  const openBrief = (b: Brief) => {
    router.push(`/brief-draft?brief_id=${b.brief_id}`);
  };

  const renderHeader = () => (
    <>
      <View style={styles.hero}>
        <View style={styles.imageWrap}>
          {items.length === 0 ? (
            <Image
              source={{ uri: CLIPBOARD_IMG }}
              style={styles.image}
              contentFit="cover"
              transition={200}
            />
          ) : (
            <Ionicons name="folder-open-outline" size={44} color={colors.brandPrimary} />
          )}
        </View>
        <Text style={styles.heroTitle} testID="dashboard-empty-title">
          {items.length === 0 ? "No briefs yet" : `${items.length} brief${items.length === 1 ? "" : "s"}`}
        </Text>
        <Text style={styles.heroText}>
          {items.length === 0
            ? "Snap your first referral or lab result to generate a doctor-ready 1-page summary."
            : "Keep drafting, or start a new brief for your next visit."}
        </Text>

        {!isSubscribed ? (
          <View style={styles.lockedRow} testID="dashboard-locked-features">
            {LOCKED_FEATURES.map((f) => (
              <Pressable
                key={f.key}
                testID={`dashboard-locked-${f.key}`}
                onPress={() => openPaywall(f.key)}
                style={styles.lockedChip}
              >
                <Ionicons name={f.icon as any} size={14} color={colors.onSurfaceSecondary} />
                <Text style={styles.lockedChipText}>{f.label}</Text>
                <Ionicons name="lock-closed" size={11} color={colors.muted} />
              </Pressable>
            ))}
          </View>
        ) : null}
      </View>

      {items.length > 0 ? (
        <Text style={styles.listLabel}>Your briefs</Text>
      ) : null}
    </>
  );

  const renderItem = ({ item }: { item: Brief }) => {
    const dt = docTypeLabel(item.doc_type);
    const cover = item.photos[0];
    return (
      <Pressable
        testID={`brief-card-${item.brief_id}`}
        onPress={() => openBrief(item)}
        style={({ pressed }) => [styles.card, pressed && styles.cardPressed]}
      >
        <View style={styles.cardThumb}>
          {cover ? (
            <Image
              source={getFileUrl(cover.url) as any}
              style={StyleSheet.absoluteFillObject}
              contentFit="cover"
              transition={150}
            />
          ) : (
            <Ionicons name="images-outline" size={22} color={colors.muted} />
          )}
        </View>
        <View style={styles.cardBody}>
          <View style={styles.cardTitleRow}>
            <Ionicons name={dt.icon as any} size={14} color={colors.brandPrimary} />
            <Text style={styles.cardTitle}>{dt.label}</Text>
            {item.status === "draft" ? (
              <View style={styles.draftPill}>
                <Text style={styles.draftPillText}>DRAFT</Text>
              </View>
            ) : null}
          </View>
          <Text style={styles.cardMeta} numberOfLines={1}>
            {item.photos.length} photo{item.photos.length === 1 ? "" : "s"} • {relativeTime(item.updated_at)}
          </Text>
        </View>
        <Ionicons name="chevron-forward" size={18} color={colors.muted} />
      </Pressable>
    );
  };

  return (
    <View style={styles.container} testID="dashboard-screen">
      <View
        style={[styles.header, { paddingTop: insets.top + spacing.md }]}
      >
        <View style={styles.headerLeft}>
          <Text style={styles.brand} testID="dashboard-brand">Anteroom</Text>
          <Text style={styles.greeting} numberOfLines={1}>
            Hi{user?.name ? `, ${user.name.split(" ")[0]}` : ""}
          </Text>
        </View>
        <View style={styles.headerRight}>
          {isSubscribed ? (
            <View style={styles.proBadge} testID="dashboard-pro-badge">
              <Ionicons name="star" size={12} color={colors.onBrandPrimary} />
              <Text style={styles.proBadgeText}>PRO</Text>
            </View>
          ) : (
            <Pressable
              testID="dashboard-upgrade-btn"
              onPress={() => openPaywall("header")}
              style={styles.upgradeBtn}
            >
              <Ionicons name="sparkles" size={13} color={colors.brandPrimary} />
              <Text style={styles.upgradeText}>Upgrade</Text>
            </Pressable>
          )}
          <Pressable
            testID="dashboard-avatar"
            onPress={signOut}
            style={styles.avatar}
            hitSlop={8}
          >
            <Text style={styles.avatarText}>{initials || "A"}</Text>
          </Pressable>
        </View>
      </View>

      <FlatList
        data={items}
        keyExtractor={(b) => b.brief_id}
        renderItem={renderItem}
        ListHeaderComponent={renderHeader}
        contentContainerStyle={[
          styles.listContent,
          { paddingBottom: insets.bottom + 120 },
        ]}
        ItemSeparatorComponent={() => <View style={{ height: spacing.md }} />}
        refreshControl={
          <RefreshControl
            refreshing={listQuery.isFetching}
            onRefresh={() => listQuery.refetch()}
            tintColor={colors.brandPrimary}
          />
        }
      />

      <View
        style={[styles.footer, { paddingBottom: insets.bottom + spacing.lg }]}
      >
        <Pressable
          testID="dashboard-create-btn"
          style={({ pressed }) => [styles.primaryBtn, pressed && styles.pressed]}
          onPress={createBrief}
        >
          <Ionicons name="camera-outline" size={20} color={colors.onBrandPrimary} />
          <Text style={styles.primaryBtnText}>Create a brief</Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.surface },
  header: {
    paddingHorizontal: spacing.xl,
    paddingBottom: spacing.md,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  headerLeft: { gap: 2, flex: 1 },
  headerRight: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  brand: {
    color: colors.brandPrimary,
    fontSize: 22,
    fontWeight: "800",
    letterSpacing: -0.4,
  },
  greeting: { color: colors.muted, fontSize: 13 },
  upgradeBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: colors.brandTertiary,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.md,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: colors.brandSecondary,
  },
  upgradeText: {
    color: colors.brandPrimary,
    fontSize: 12,
    fontWeight: "700",
    letterSpacing: 0.3,
  },
  proBadge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    backgroundColor: colors.brandPrimary,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
  },
  proBadgeText: {
    color: colors.onBrandPrimary,
    fontSize: 11,
    fontWeight: "800",
    letterSpacing: 1,
  },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: radius.pill,
    backgroundColor: colors.brandTertiary,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: 1,
    borderColor: colors.border,
  },
  avatarText: {
    color: colors.brandPrimary,
    fontSize: 14,
    fontWeight: "700",
  },
  listContent: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
  },
  hero: {
    alignItems: "center",
    gap: spacing.md,
    paddingVertical: spacing.xl,
  },
  imageWrap: {
    width: 140,
    height: 140,
    borderRadius: radius.lg,
    backgroundColor: colors.brandTertiary,
    overflow: "hidden",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.sm,
  },
  image: { width: "100%", height: "100%" },
  heroTitle: {
    color: colors.onSurface,
    fontSize: 20,
    fontWeight: "700",
    letterSpacing: -0.3,
  },
  heroText: {
    color: colors.muted,
    fontSize: 14,
    textAlign: "center",
    lineHeight: 20,
    maxWidth: 300,
  },
  lockedRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    justifyContent: "center",
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  lockedChip: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    backgroundColor: colors.surfaceSecondary,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.pill,
    paddingHorizontal: spacing.md,
    paddingVertical: 8,
  },
  lockedChipText: {
    color: colors.onSurfaceSecondary,
    fontSize: 12,
    fontWeight: "600",
  },
  listLabel: {
    color: colors.onSurfaceTertiary,
    fontSize: 12,
    letterSpacing: 1.5,
    fontWeight: "700",
    textTransform: "uppercase",
    marginBottom: spacing.md,
    marginTop: spacing.sm,
  },
  card: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    padding: spacing.md,
    borderRadius: radius.md,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
  },
  cardPressed: { opacity: 0.75 },
  cardThumb: {
    width: 56,
    height: 72,
    borderRadius: radius.sm,
    backgroundColor: colors.surfaceSecondary,
    overflow: "hidden",
    alignItems: "center",
    justifyContent: "center",
  },
  cardBody: { flex: 1, gap: 4 },
  cardTitleRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  cardTitle: { color: colors.onSurface, fontSize: 15, fontWeight: "700" },
  cardMeta: { color: colors.muted, fontSize: 12 },
  draftPill: {
    marginLeft: spacing.sm,
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: radius.pill,
    backgroundColor: colors.warning,
  },
  draftPillText: {
    color: colors.onWarning,
    fontSize: 9,
    fontWeight: "800",
    letterSpacing: 0.6,
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
