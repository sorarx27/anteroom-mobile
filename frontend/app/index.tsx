import { useRouter } from "expo-router";
import { useEffect } from "react";
import {
  ActivityIndicator,
  ImageBackground,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useAuth } from "@/src/auth/AuthContext";
import { colors, radius, spacing } from "@/src/theme";

const SPLASH_BG =
  "https://images.unsplash.com/photo-1649861742672-20152f77c1f5?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA1NTJ8MHwxfHNlYXJjaHwxfHxjYWxtJTIwc2FnZSUyMGdyZWVuJTIwYWJzdHJhY3QlMjBncmFkaWVudHxlbnwwfHx8fDE3ODk0ODU2MDR8MA&ixlib=rb-4.1.0&q=85";

export default function Welcome() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { status } = useAuth();

  useEffect(() => {
    // small delay for splash feel handled by loading state
  }, []);

  if (status === "loading") {
    return (
      <View style={styles.loadingContainer} testID="welcome-loading">
        <ActivityIndicator color={colors.brandPrimary} />
      </View>
    );
  }

  return (
    <View style={styles.container} testID="welcome-screen">
      <ImageBackground
        source={{ uri: SPLASH_BG }}
        style={StyleSheet.absoluteFillObject}
        resizeMode="cover"
      >
        <LinearGradient
          colors={["rgba(17,24,21,0)", "rgba(17,24,21,0.85)"]}
          locations={[0.3, 1]}
          style={StyleSheet.absoluteFillObject}
        />
      </ImageBackground>

      <View
        style={[
          styles.content,
          { paddingTop: insets.top + spacing.xl, paddingBottom: insets.bottom + spacing.xl },
        ]}
      >
        <View style={styles.brandBlock}>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>ANTEROOM</Text>
          </View>
          <Text style={styles.title} testID="welcome-title">
            Clarity{"\n"}from chaos.
          </Text>
          <Text style={styles.tagline}>
            Turn messy medical papers into a doctor-ready pre-visit brief in seconds.
          </Text>
        </View>

        <View style={styles.actions}>
          <Pressable
            testID="welcome-get-started-btn"
            onPress={() => router.push("/onboarding")}
            style={({ pressed }) => [styles.primaryBtn, pressed && styles.pressed]}
          >
            <Text style={styles.primaryBtnText}>Get started</Text>
          </Pressable>
          <Pressable
            testID="welcome-signin-link"
            onPress={() => router.push("/auth?mode=signin")}
            style={styles.linkBtn}
          >
            <Text style={styles.linkText}>
              Already have an account? <Text style={styles.linkTextBold}>Sign in</Text>
            </Text>
          </Pressable>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    flex: 1,
    backgroundColor: colors.surface,
    alignItems: "center",
    justifyContent: "center",
  },
  container: {
    flex: 1,
    backgroundColor: colors.surfaceInverse,
  },
  content: {
    flex: 1,
    paddingHorizontal: spacing.xl,
    justifyContent: "flex-end",
  },
  brandBlock: {
    marginBottom: spacing["3xl"],
  },
  badge: {
    alignSelf: "flex-start",
    borderColor: "rgba(255,255,255,0.35)",
    borderWidth: 1,
    paddingHorizontal: spacing.md,
    paddingVertical: 6,
    borderRadius: radius.pill,
    marginBottom: spacing.lg,
  },
  badgeText: {
    color: colors.onSurfaceInverse,
    fontSize: 11,
    letterSpacing: 3,
    fontWeight: "600",
  },
  title: {
    color: colors.onSurfaceInverse,
    fontSize: 44,
    fontWeight: "700",
    lineHeight: 50,
    marginBottom: spacing.md,
    letterSpacing: -1,
  },
  tagline: {
    color: "rgba(255,255,255,0.85)",
    fontSize: 16,
    lineHeight: 22,
    maxWidth: 320,
  },
  actions: {
    gap: spacing.md,
  },
  primaryBtn: {
    backgroundColor: colors.onSurfaceInverse,
    borderRadius: radius.pill,
    paddingVertical: 18,
    alignItems: "center",
  },
  pressed: { opacity: 0.85 },
  primaryBtnText: {
    color: colors.brandPrimary,
    fontSize: 16,
    fontWeight: "700",
  },
  linkBtn: {
    alignItems: "center",
    paddingVertical: spacing.sm,
  },
  linkText: {
    color: "rgba(255,255,255,0.85)",
    fontSize: 14,
  },
  linkTextBold: {
    color: colors.onSurfaceInverse,
    fontWeight: "700",
  },
});
