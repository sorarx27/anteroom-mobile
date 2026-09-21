import { useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useRouter } from "expo-router";
import Constants from "expo-constants";
import Ionicons from "@react-native-vector-icons/ionicons";
import * as WebBrowser from "expo-web-browser";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useAuth } from "@/src/auth/AuthContext";
import {
  MANAGE_SUBSCRIPTION_URL,
  PRIVACY_URL,
  SUPPORT_URL,
  TERMS_URL,
} from "@/src/constants/links";
import { colors, radius, spacing } from "@/src/theme";

export default function Settings() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const { user, signOut, deleteAccount } = useAuth();
  const [isDeleting, setIsDeleting] = useState(false);

  const version = Constants.expoConfig?.version ?? "1.0.0";

  const open = (url: string) => {
    WebBrowser.openBrowserAsync(url).catch(() => {});
  };

  const confirmDelete = () => {
    Alert.alert(
      "Delete account?",
      "This permanently deletes your account, every family profile under it, every brief and every document image you have uploaded.",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Continue",
          style: "destructive",
          onPress: () =>
            Alert.alert(
              "This cannot be undone",
              "Your briefs cannot be recovered afterwards. Delete your Anteroom account?",
              [
                { text: "Keep my account", style: "cancel" },
                { text: "Delete account", style: "destructive", onPress: runDelete },
              ],
            ),
        },
      ],
    );
  };

  const runDelete = async () => {
    setIsDeleting(true);
    try {
      await deleteAccount();
      router.replace("/");
    } catch (e: any) {
      Alert.alert(
        "Could not delete your account",
        e?.message ??
          "Something went wrong and your account was not deleted. Please try again, or email support.",
      );
    } finally {
      setIsDeleting(false);
    }
  };

  const Row = ({
    icon,
    label,
    onPress,
    destructive,
    testID,
  }: {
    icon: string;
    label: string;
    onPress: () => void;
    destructive?: boolean;
    testID: string;
  }) => (
    <Pressable testID={testID} onPress={onPress} style={styles.row}>
      <Ionicons
        name={icon as any}
        size={19}
        color={destructive ? colors.error : colors.onSurfaceSecondary}
      />
      <Text style={[styles.rowLabel, destructive && styles.rowLabelDestructive]}>
        {label}
      </Text>
      {!destructive ? (
        <Ionicons name="chevron-forward" size={17} color={colors.muted} />
      ) : null}
    </Pressable>
  );

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <Pressable
          testID="settings-back"
          onPress={() => router.back()}
          hitSlop={10}
          style={styles.backBtn}
        >
          <Ionicons name="chevron-back" size={22} color={colors.onSurface} />
        </Pressable>
        <Text style={styles.title}>Settings</Text>
        <View style={styles.backBtn} />
      </View>

      <ScrollView
        contentContainerStyle={[
          styles.content,
          { paddingBottom: insets.bottom + spacing.xl },
        ]}
      >
        <Text style={styles.sectionLabel}>ACCOUNT</Text>
        <View style={styles.card}>
          <View style={styles.row}>
            <Ionicons name="person-outline" size={19} color={colors.onSurfaceSecondary} />
            <Text style={styles.rowLabel} numberOfLines={1}>
              {user?.email ?? "Signed in"}
            </Text>
          </View>
          <View style={styles.divider} />
          <Row
            testID="settings-subscription"
            icon="card-outline"
            label="Manage subscription"
            onPress={() => open(MANAGE_SUBSCRIPTION_URL)}
          />
        </View>

        <Text style={styles.sectionLabel}>LEGAL</Text>
        <View style={styles.card}>
          <Row
            testID="settings-terms"
            icon="document-text-outline"
            label="Terms of Use"
            onPress={() => open(TERMS_URL)}
          />
          <View style={styles.divider} />
          <Row
            testID="settings-privacy"
            icon="lock-closed-outline"
            label="Privacy Policy"
            onPress={() => open(PRIVACY_URL)}
          />
          <View style={styles.divider} />
          <Row
            testID="settings-support"
            icon="help-circle-outline"
            label="Support"
            onPress={() => open(SUPPORT_URL)}
          />
        </View>

        <View style={styles.card}>
          <Row
            testID="settings-signout"
            icon="log-out-outline"
            label="Sign out"
            onPress={signOut}
          />
        </View>

        <View style={styles.card}>
          {isDeleting ? (
            <View style={styles.row}>
              <ActivityIndicator color={colors.error} />
              <Text style={[styles.rowLabel, styles.rowLabelDestructive]}>
                Deleting your account…
              </Text>
            </View>
          ) : (
            <Row
              testID="settings-delete-account"
              icon="trash-outline"
              label="Delete account"
              destructive
              onPress={confirmDelete}
            />
          )}
        </View>
        <Text style={styles.deleteNote}>
          Deleting your account permanently removes every profile, brief and uploaded
          image. It cannot be undone.
        </Text>

        <Text style={styles.disclaimer}>
          Anteroom does not diagnose and does not recommend treatment. It organises what
          is already written on your documents. Always check a brief against the original.
        </Text>
        <Text style={styles.version}>Anteroom {version}</Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.surfaceSecondary },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  backBtn: { width: 32, height: 32, justifyContent: "center" },
  title: { fontSize: 17, fontWeight: "700", color: colors.onSurface },
  content: { paddingHorizontal: spacing.lg },
  sectionLabel: {
    fontSize: 11,
    fontWeight: "700",
    letterSpacing: 0.6,
    color: colors.muted,
    marginTop: spacing.lg,
    marginBottom: spacing.sm,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    marginBottom: spacing.md,
    overflow: "hidden",
  },
  row: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.md,
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
  },
  rowLabel: { flex: 1, fontSize: 15, color: colors.onSurface },
  rowLabelDestructive: { color: colors.error, fontWeight: "600" },
  divider: { height: 1, backgroundColor: colors.divider, marginLeft: spacing.lg },
  deleteNote: {
    fontSize: 12,
    lineHeight: 17,
    color: colors.muted,
    marginTop: -spacing.xs,
    marginBottom: spacing.lg,
  },
  disclaimer: {
    fontSize: 12,
    lineHeight: 17,
    color: colors.muted,
    marginTop: spacing.md,
  },
  version: {
    fontSize: 12,
    color: colors.muted,
    textAlign: "center",
    marginTop: spacing.lg,
  },
});
