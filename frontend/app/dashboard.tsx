import {
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import Ionicons from "@react-native-vector-icons/ionicons";
import { Image } from "expo-image";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useAuth } from "@/src/auth/AuthContext";
import { useSubscription } from "@/src/lib/revenuecat";
import { colors, radius, spacing } from "@/src/theme";

const CLIPBOARD_IMG =
  "https://images.unsplash.com/photo-1651760680066-db9d32bd0357?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA1MDV8MHwxfHNlYXJjaHwxfHxlbXB0eSUyMG1lZGljYWwlMjBjbGlwYm9hcmQlMjBjbGVhbnxlbnwwfHx8fDE3ODk0ODU2MDR8MA&ixlib=rb-4.1.0&q=85";

type LockedFeature = {
  key: string;
  icon: string;
  label: string;
};

const LOCKED_FEATURES: LockedFeature[] = [
  { key: "clean-export", icon: "document-text-outline", label: "Clean export" },
  { key: "family-profiles", icon: "people-outline", label: "Family profiles" },
  { key: "translate", icon: "language-outline", label: "Translate" },
];

export default function Dashboard() {
  const insets = useSafeAreaInsets();
  const router = useRouter();
  const { user, signOut } = useAuth();
  const { isSubscribed } = useSubscription();

  const initials = (user?.name ?? user?.email ?? "A")
    .split(" ")
    .map((s) => s.trim()[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();

  const openPaywall = (trigger: string) =>
    router.push(`/paywall?trigger=${trigger}`);

  return (
    <View style={styles.container} testID="dashboard-screen">
      <View
        style={[
          styles.header,
          { paddingTop: insets.top + spacing.md },
        ]}
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

      <View style={styles.body}>
        <View style={styles.imageWrap}>
          <Image
            source={{ uri: CLIPBOARD_IMG }}
            style={styles.image}
            contentFit="cover"
            transition={200}
          />
        </View>
        <Text style={styles.emptyTitle} testID="dashboard-empty-title">
          No briefs yet
        </Text>
        <Text style={styles.emptyText}>
          Snap your first referral or lab result to generate a doctor-ready 1-page summary.
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

      <View
        style={[styles.footer, { paddingBottom: insets.bottom + spacing.lg }]}
      >
        <Pressable
          testID="dashboard-create-btn"
          style={({ pressed }) => [styles.primaryBtn, pressed && styles.pressed]}
          onPress={() => {
            // future feature
          }}
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
  body: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: spacing.xl,
    gap: spacing.lg,
  },
  imageWrap: {
    width: 200,
    height: 200,
    borderRadius: radius.lg,
    backgroundColor: colors.brandTertiary,
    overflow: "hidden",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.md,
  },
  image: { width: "100%", height: "100%" },
  emptyTitle: {
    color: colors.onSurface,
    fontSize: 22,
    fontWeight: "700",
    letterSpacing: -0.3,
  },
  emptyText: {
    color: colors.muted,
    fontSize: 15,
    textAlign: "center",
    lineHeight: 22,
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
  footer: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
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
