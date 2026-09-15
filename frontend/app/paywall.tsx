import { useMemo, useState } from "react";
import {
  ActivityIndicator,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useLocalSearchParams, useRouter } from "expo-router";
import Ionicons from "@react-native-vector-icons/ionicons";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { LinearGradient } from "expo-linear-gradient";
import type { PurchasesPackage } from "react-native-purchases";

import { useSubscription } from "@/src/lib/revenuecat";
import { useAuth } from "@/src/auth/AuthContext";
import { colors, radius, spacing } from "@/src/theme";

const PRO_FEATURES: { icon: string; title: string; body: string }[] = [
  {
    icon: "document-text-outline",
    title: "Clean export",
    body: "Doctor-ready PDF without watermark, with a scannable clinic QR.",
  },
  {
    icon: "people-outline",
    title: "Family profiles",
    body: "One account for the whole household — kids, partner, parents.",
  },
  {
    icon: "language-outline",
    title: "Translation",
    body: "Translate every brief to your doctor's language in one tap.",
  },
];

function packageLabel(pkg: PurchasesPackage): {
  title: string;
  cadence: string;
  badge?: string;
} {
  const id = pkg.identifier;
  if (id === "$rc_monthly") return { title: "Monthly", cadence: "per month" };
  if (id === "$rc_annual")
    return { title: "Annual", cadence: "per year", badge: "Best value" };
  if (id === "$rc_lifetime")
    return { title: "Lifetime", cadence: "one time", badge: "Best value" };
  return { title: pkg.product.title || id, cadence: pkg.product.priceString };
}

export default function Paywall() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const params = useLocalSearchParams<{ trigger?: string }>();
  const { user, purchaseIdentityError } = useAuth();
  const {
    offerings,
    isSubscribed,
    identityReady,
    isLoading,
    purchase,
    restore,
    isPurchasing,
    isRestoring,
    purchaseError,
    resetPurchaseError,
  } = useSubscription();

  const packages = useMemo<PurchasesPackage[]>(() => {
    const list = offerings?.current?.availablePackages ?? [];
    // Prefer annual first when both monthly & annual exist.
    return [...list].sort((a, b) => {
      const order = (id: string) =>
        id === "$rc_annual" ? 0 : id === "$rc_lifetime" ? 1 : id === "$rc_monthly" ? 2 : 3;
      return order(a.identifier) - order(b.identifier);
    });
  }, [offerings]);

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [confirmVisible, setConfirmVisible] = useState(false);
  const [banner, setBanner] = useState<string | null>(null);

  const currentPkg =
    packages.find((p) => p.identifier === selectedId) ?? packages[0] ?? null;

  const canPurchase = identityReady && !isPurchasing && !!currentPkg && !isSubscribed;

  const doPurchase = async () => {
    if (!currentPkg) return;
    setConfirmVisible(false);
    setBanner(null);
    resetPurchaseError();
    try {
      await purchase(currentPkg);
      router.back();
    } catch (e: any) {
      const msg = e?.message ?? "Purchase failed";
      if (/user\s*cancel/i.test(msg) || /userCancelled/i.test(msg)) {
        // silent
        return;
      }
      setBanner(msg);
    }
  };

  const doRestore = async () => {
    setBanner(null);
    try {
      await restore();
      setBanner("Purchases restored");
    } catch (e: any) {
      setBanner(e?.message ?? "Could not restore purchases");
    }
  };

  return (
    <View style={styles.container} testID="paywall-screen">
      <LinearGradient
        colors={[colors.brandPrimary, "#22453B"]}
        style={[styles.header, { paddingTop: insets.top + spacing.md }]}
      >
        <View style={styles.headerRow}>
          <Pressable
            testID="paywall-close-btn"
            onPress={() => router.back()}
            style={styles.iconBtn}
            hitSlop={12}
          >
            <Ionicons name="close" size={22} color={colors.onBrandPrimary} />
          </Pressable>
          <Text style={styles.eyebrow}>ANTEROOM PRO</Text>
          <View style={{ width: 36 }} />
        </View>
        <Text style={styles.title} testID="paywall-title">
          Every brief,{"\n"}clinic-ready.
        </Text>
        <Text style={styles.subtitle}>
          Unlock clean PDFs, family profiles, and doctor-language translation.
        </Text>
      </LinearGradient>

      <ScrollView
        style={styles.body}
        contentContainerStyle={[
          styles.bodyContent,
          { paddingBottom: insets.bottom + 220 },
        ]}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.featureList}>
          {PRO_FEATURES.map((f) => (
            <View style={styles.featureRow} key={f.title}>
              <View style={styles.featureIconWrap}>
                <Ionicons name={f.icon as any} size={20} color={colors.brandPrimary} />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.featureTitle}>{f.title}</Text>
                <Text style={styles.featureBody}>{f.body}</Text>
              </View>
            </View>
          ))}
        </View>

        <View style={styles.plansHeader}>
          <Text style={styles.plansTitle}>Choose your plan</Text>
        </View>

        {isLoading && packages.length === 0 ? (
          <View style={styles.loading}>
            <ActivityIndicator color={colors.brandPrimary} />
          </View>
        ) : packages.length === 0 ? (
          <View style={styles.unavailable} testID="paywall-unavailable">
            <Text style={styles.unavailableText}>
              Subscription options are unavailable right now. Please try again later.
            </Text>
          </View>
        ) : (
          <View style={styles.plans}>
            {packages.map((pkg) => {
              const label = packageLabel(pkg);
              const selected = (selectedId ?? packages[0]?.identifier) === pkg.identifier;
              return (
                <Pressable
                  key={pkg.identifier}
                  testID={`paywall-plan-${pkg.identifier.replace("$", "")}`}
                  onPress={() => setSelectedId(pkg.identifier)}
                  style={[styles.planCard, selected && styles.planCardSelected]}
                >
                  <View style={styles.planLeft}>
                    <View style={styles.radioOuter}>
                      {selected ? <View style={styles.radioInner} /> : null}
                    </View>
                    <View>
                      <View style={styles.planTitleRow}>
                        <Text style={styles.planTitle}>{label.title}</Text>
                        {label.badge ? (
                          <View style={styles.badge}>
                            <Text style={styles.badgeText}>{label.badge}</Text>
                          </View>
                        ) : null}
                      </View>
                      <Text style={styles.planCadence}>{label.cadence}</Text>
                    </View>
                  </View>
                  <Text style={styles.planPrice}>{pkg.product.priceString}</Text>
                </Pressable>
              );
            })}
          </View>
        )}

        {purchaseIdentityError ? (
          <View style={styles.banner} testID="paywall-identity-banner">
            <Ionicons name="warning-outline" size={18} color={colors.warning} />
            <Text style={styles.bannerText}>
              We couldn't link your account for purchases. Please try signing out and back in.
            </Text>
          </View>
        ) : null}

        {banner ? (
          <View style={styles.banner} testID="paywall-banner">
            <Ionicons name="information-circle-outline" size={18} color={colors.info} />
            <Text style={styles.bannerText}>{banner}</Text>
          </View>
        ) : null}

        {purchaseError ? (
          <Text style={styles.errorText} testID="paywall-error">
            {(purchaseError as Error).message}
          </Text>
        ) : null}

        <Pressable
          testID="paywall-restore-btn"
          onPress={doRestore}
          disabled={isRestoring}
          style={styles.restoreBtn}
        >
          <Text style={styles.restoreText}>
            {isRestoring ? "Restoring…" : "Restore purchases"}
          </Text>
        </Pressable>

        <Text style={styles.legal}>
          Subscriptions auto-renew until cancelled. Manage or cancel any time in your
          store account. By continuing you agree to our Terms and Privacy Policy.
        </Text>
      </ScrollView>

      <View
        style={[styles.footer, { paddingBottom: insets.bottom + spacing.lg }]}
        pointerEvents="box-none"
      >
        {isSubscribed ? (
          <View style={styles.subscribedBanner} testID="paywall-subscribed">
            <Ionicons name="checkmark-circle" size={20} color={colors.success} />
            <Text style={styles.subscribedText}>You're on Anteroom Pro</Text>
          </View>
        ) : (
          <Pressable
            testID="paywall-purchase-btn"
            disabled={!canPurchase}
            onPress={() => (__DEV__ ? setConfirmVisible(true) : doPurchase())}
            style={({ pressed }) => [
              styles.primaryBtn,
              (!canPurchase || pressed) && styles.primaryBtnDim,
            ]}
          >
            {isPurchasing ? (
              <ActivityIndicator color={colors.onBrandPrimary} />
            ) : (
              <Text style={styles.primaryBtnText}>
                {currentPkg ? `Continue • ${currentPkg.product.priceString}` : "Continue"}
              </Text>
            )}
          </Pressable>
        )}
        {__DEV__ && !isSubscribed && currentPkg ? (
          <Text style={styles.simulated}>Simulated purchase (Test Store)</Text>
        ) : null}
      </View>

      <Modal
        visible={confirmVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setConfirmVisible(false)}
      >
        <View style={styles.modalBackdrop}>
          <View style={styles.modalCard}>
            <Text style={styles.modalTitle}>Confirm test purchase</Text>
            <Text style={styles.modalBody}>
              {currentPkg
                ? `Simulate purchase of ${packageLabel(currentPkg).title} for ${currentPkg.product.priceString}?`
                : ""}
              {"\n\n"}This uses the RevenueCat Test Store. No real charge.
            </Text>
            <View style={styles.modalActions}>
              <Pressable
                testID="paywall-confirm-cancel"
                style={styles.modalGhost}
                onPress={() => setConfirmVisible(false)}
              >
                <Text style={styles.modalGhostText}>Cancel</Text>
              </Pressable>
              <Pressable
                testID="paywall-confirm-ok"
                style={styles.modalCta}
                onPress={doPurchase}
              >
                <Text style={styles.modalCtaText}>Confirm</Text>
              </Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.surface },
  header: {
    paddingHorizontal: spacing.xl,
    paddingBottom: spacing.xl,
  },
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: spacing.lg,
  },
  iconBtn: {
    width: 36,
    height: 36,
    borderRadius: radius.pill,
    backgroundColor: "rgba(255,255,255,0.15)",
    alignItems: "center",
    justifyContent: "center",
  },
  eyebrow: {
    color: colors.onBrandPrimary,
    fontSize: 11,
    letterSpacing: 3,
    fontWeight: "700",
  },
  title: {
    color: colors.onBrandPrimary,
    fontSize: 32,
    fontWeight: "700",
    lineHeight: 38,
    letterSpacing: -0.5,
    marginBottom: spacing.md,
  },
  subtitle: {
    color: "rgba(255,255,255,0.85)",
    fontSize: 15,
    lineHeight: 21,
  },
  body: { flex: 1, backgroundColor: colors.surface },
  bodyContent: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.xl,
    gap: spacing.xl,
  },
  featureList: { gap: spacing.lg },
  featureRow: {
    flexDirection: "row",
    gap: spacing.md,
    alignItems: "flex-start",
  },
  featureIconWrap: {
    width: 40,
    height: 40,
    borderRadius: radius.md,
    backgroundColor: colors.brandTertiary,
    alignItems: "center",
    justifyContent: "center",
  },
  featureTitle: {
    color: colors.onSurface,
    fontSize: 15,
    fontWeight: "700",
    marginBottom: 2,
  },
  featureBody: { color: colors.muted, fontSize: 13, lineHeight: 18 },
  plansHeader: { gap: spacing.sm },
  plansTitle: {
    color: colors.onSurface,
    fontSize: 18,
    fontWeight: "700",
    letterSpacing: -0.3,
  },
  loading: { paddingVertical: spacing.xl, alignItems: "center" },
  unavailable: {
    backgroundColor: colors.surfaceSecondary,
    padding: spacing.lg,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
  },
  unavailableText: {
    color: colors.onSurfaceSecondary,
    fontSize: 14,
    textAlign: "center",
  },
  plans: { gap: spacing.md },
  planCard: {
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: spacing.lg,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  planCardSelected: {
    borderColor: colors.brandPrimary,
    backgroundColor: colors.brandTertiary,
  },
  planLeft: { flexDirection: "row", alignItems: "center", gap: spacing.md, flex: 1 },
  radioOuter: {
    width: 20,
    height: 20,
    borderRadius: 10,
    borderWidth: 2,
    borderColor: colors.borderStrong,
    alignItems: "center",
    justifyContent: "center",
  },
  radioInner: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: colors.brandPrimary,
  },
  planTitleRow: { flexDirection: "row", alignItems: "center", gap: spacing.sm },
  planTitle: { color: colors.onSurface, fontSize: 16, fontWeight: "700" },
  planCadence: { color: colors.muted, fontSize: 13, marginTop: 2 },
  planPrice: { color: colors.onSurface, fontSize: 17, fontWeight: "700" },
  badge: {
    backgroundColor: colors.brandPrimary,
    paddingHorizontal: spacing.sm,
    paddingVertical: 3,
    borderRadius: radius.pill,
  },
  badgeText: {
    color: colors.onBrandPrimary,
    fontSize: 10,
    fontWeight: "700",
    letterSpacing: 0.5,
  },
  banner: {
    flexDirection: "row",
    gap: spacing.sm,
    padding: spacing.md,
    backgroundColor: colors.surfaceSecondary,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    alignItems: "center",
  },
  bannerText: { color: colors.onSurfaceSecondary, fontSize: 13, flex: 1 },
  errorText: { color: colors.error, fontSize: 13, textAlign: "center" },
  restoreBtn: { alignItems: "center", paddingVertical: spacing.sm },
  restoreText: { color: colors.brandPrimary, fontSize: 14, fontWeight: "600" },
  legal: {
    color: colors.muted,
    fontSize: 11,
    lineHeight: 16,
    textAlign: "center",
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
    gap: spacing.sm,
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
  simulated: {
    color: colors.muted,
    fontSize: 11,
    textAlign: "center",
  },
  subscribedBanner: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: spacing.sm,
    paddingVertical: 18,
    backgroundColor: colors.brandTertiary,
    borderRadius: radius.pill,
  },
  subscribedText: {
    color: colors.onSurface,
    fontSize: 15,
    fontWeight: "700",
  },
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
  modalTitle: {
    color: colors.onSurface,
    fontSize: 18,
    fontWeight: "700",
  },
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
