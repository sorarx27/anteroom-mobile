import { useLocalSearchParams, useRouter } from "expo-router";
import { useMemo, useState } from "react";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import AntDesign from "@react-native-vector-icons/ant-design";
import Ionicons from "@react-native-vector-icons/ionicons";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useAuth } from "@/src/auth/AuthContext";
import { colors, radius, spacing } from "@/src/theme";

type Mode = "signin" | "signup";

export default function AuthScreen() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const params = useLocalSearchParams<{ mode?: string }>();
  const initialMode: Mode = params.mode === "signin" ? "signin" : "signup";
  const [mode, setMode] = useState<Mode>(initialMode);
  const { signInEmail, signUpEmail, signInWithGoogle } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPw, setShowPw] = useState(false);
  const [loadingEmail, setLoadingEmail] = useState(false);
  const [loadingGoogle, setLoadingGoogle] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const validEmail = useMemo(() => /^\S+@\S+\.\S+$/.test(email.trim()), [email]);
  const validPassword = password.length >= 6;
  const canSubmit = validEmail && validPassword && !loadingEmail;

  const submit = async () => {
    if (!canSubmit) return;
    setError(null);
    setLoadingEmail(true);
    try {
      if (mode === "signup") {
        await signUpEmail(email.trim().toLowerCase(), password);
      } else {
        await signInEmail(email.trim().toLowerCase(), password);
      }
    } catch (e: any) {
      setError(e?.message ?? "Something went wrong");
    } finally {
      setLoadingEmail(false);
    }
  };

  const doGoogle = async () => {
    setError(null);
    setLoadingGoogle(true);
    try {
      await signInWithGoogle();
    } catch (e: any) {
      setError(e?.message ?? "Google sign-in failed");
    } finally {
      setLoadingGoogle(false);
    }
  };

  return (
    <View style={styles.container} testID="auth-screen">
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
      >
        <ScrollView
          contentContainerStyle={[
            styles.scroll,
            { paddingTop: insets.top + spacing.md, paddingBottom: insets.bottom + spacing.xl },
          ]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <Pressable
            testID="auth-back-btn"
            onPress={() => router.back()}
            style={styles.backBtn}
            hitSlop={12}
          >
            <Ionicons name="chevron-back" size={26} color={colors.onSurface} />
          </Pressable>

          <View style={styles.header}>
            <Text style={styles.title} testID="auth-title">
              {mode === "signup" ? "Create your account" : "Welcome back"}
            </Text>
            <Text style={styles.subtitle}>
              {mode === "signup"
                ? "Your medical documents, organized and clinic-ready."
                : "Pick up where you left off."}
            </Text>
          </View>

          <View style={styles.form}>
            <View style={styles.field}>
              <Text style={styles.label}>Email</Text>
              <TextInput
                testID="auth-email-input"
                value={email}
                onChangeText={setEmail}
                placeholder="you@example.com"
                placeholderTextColor={colors.muted}
                autoCapitalize="none"
                autoComplete="email"
                keyboardType="email-address"
                style={styles.input}
              />
            </View>
            <View style={styles.field}>
              <Text style={styles.label}>Password</Text>
              <View style={styles.pwWrap}>
                <TextInput
                  testID="auth-password-input"
                  value={password}
                  onChangeText={setPassword}
                  placeholder="At least 6 characters"
                  placeholderTextColor={colors.muted}
                  secureTextEntry={!showPw}
                  autoCapitalize="none"
                  style={[styles.input, { flex: 1, borderWidth: 0 }]}
                />
                <Pressable
                  testID="auth-toggle-password"
                  onPress={() => setShowPw((v) => !v)}
                  style={styles.eyeBtn}
                  hitSlop={8}
                >
                  <Ionicons
                    name={showPw ? "eye-off-outline" : "eye-outline"}
                    size={20}
                    color={colors.muted}
                  />
                </Pressable>
              </View>
            </View>

            {error ? (
              <Text style={styles.errorText} testID="auth-error">
                {error}
              </Text>
            ) : null}

            <Pressable
              testID="auth-submit-btn"
              disabled={!canSubmit}
              onPress={submit}
              style={({ pressed }) => [
                styles.primaryBtn,
                (!canSubmit || pressed) && styles.primaryBtnDim,
              ]}
            >
              {loadingEmail ? (
                <ActivityIndicator color={colors.onBrandPrimary} />
              ) : (
                <Text style={styles.primaryBtnText}>
                  {mode === "signup" ? "Continue" : "Sign in"}
                </Text>
              )}
            </Pressable>

            <View style={styles.dividerRow}>
              <View style={styles.dividerLine} />
              <Text style={styles.dividerText}>or</Text>
              <View style={styles.dividerLine} />
            </View>

            <Pressable
              testID="auth-google-btn"
              onPress={doGoogle}
              disabled={loadingGoogle}
              style={({ pressed }) => [styles.googleBtn, pressed && styles.pressed]}
            >
              {loadingGoogle ? (
                <ActivityIndicator color={colors.onSurfaceSecondary} />
              ) : (
                <>
                  <AntDesign name="google" size={18} color={colors.onSurfaceSecondary} />
                  <Text style={styles.googleText}>Continue with Google</Text>
                </>
              )}
            </Pressable>
          </View>

          <Pressable
            testID="auth-toggle-mode"
            onPress={() => {
              setError(null);
              setMode(mode === "signup" ? "signin" : "signup");
            }}
            style={styles.switchBtn}
          >
            <Text style={styles.switchText}>
              {mode === "signup"
                ? "Already have an account? "
                : "New to Anteroom? "}
              <Text style={styles.switchTextBold}>
                {mode === "signup" ? "Sign in" : "Create account"}
              </Text>
            </Text>
          </Pressable>
        </ScrollView>
      </KeyboardAvoidingView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.surface },
  scroll: {
    paddingHorizontal: spacing.xl,
    gap: spacing.xl,
  },
  backBtn: {
    width: 40,
    height: 40,
    borderRadius: radius.pill,
    backgroundColor: colors.surfaceSecondary,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.md,
  },
  header: { gap: spacing.sm },
  title: {
    color: colors.onSurface,
    fontSize: 32,
    fontWeight: "700",
    letterSpacing: -0.5,
  },
  subtitle: { color: colors.muted, fontSize: 15, lineHeight: 21 },
  form: { gap: spacing.lg },
  field: { gap: spacing.sm },
  label: {
    color: colors.onSurfaceSecondary,
    fontSize: 13,
    fontWeight: "600",
  },
  input: {
    backgroundColor: colors.surfaceSecondary,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.lg,
    paddingVertical: 14,
    fontSize: 16,
    color: colors.onSurface,
  },
  pwWrap: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: colors.surfaceSecondary,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    paddingRight: spacing.sm,
  },
  eyeBtn: { padding: spacing.sm },
  errorText: { color: colors.error, fontSize: 14 },
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
  dividerRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
  },
  dividerLine: { flex: 1, height: 1, backgroundColor: colors.divider },
  dividerText: { color: colors.muted, fontSize: 13 },
  googleBtn: {
    backgroundColor: colors.surfaceSecondary,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: radius.pill,
    paddingVertical: 16,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: spacing.md,
    minHeight: 56,
  },
  googleText: {
    color: colors.onSurfaceSecondary,
    fontSize: 15,
    fontWeight: "600",
  },
  pressed: { opacity: 0.85 },
  switchBtn: { alignItems: "center", paddingVertical: spacing.md },
  switchText: { color: colors.muted, fontSize: 14 },
  switchTextBold: { color: colors.brandPrimary, fontWeight: "700" },
});
