import { useState } from "react";
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
import { useLocalSearchParams, useRouter } from "expo-router";
import Ionicons from "@react-native-vector-icons/ionicons";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { useQueryClient } from "@tanstack/react-query";

import {
  profiles as api,
  Profile,
  Relationship,
  RELATIONSHIP_ICONS,
  RELATIONSHIP_LABELS,
} from "@/src/api/profiles";
import { useProfiles } from "@/src/profiles/ProfileContext";
import { useSubscription } from "@/src/lib/revenuecat";
import { colors, radius, spacing } from "@/src/theme";

const RELATIONSHIPS: Exclude<Relationship, "self">[] = [
  "partner",
  "child",
  "parent",
  "other",
];

type SexOption = "male" | "female" | "other";
const SEXES: { key: SexOption; label: string }[] = [
  { key: "female", label: "Female" },
  { key: "male", label: "Male" },
  { key: "other", label: "Other" },
];

export default function ProfileAdd() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const { isSubscribed } = useSubscription();
  const { refresh, setActiveProfile, profiles: existing } = useProfiles();
  const { profile_id: editingId } = useLocalSearchParams<{ profile_id?: string }>();
  const editing: Profile | null = editingId
    ? existing.find((p) => p.profile_id === editingId) ?? null
    : null;

  const [name, setName] = useState(editing?.name ?? "");
  const [relationship, setRelationship] = useState<Relationship>(
    editing?.relationship ?? "partner",
  );
  const [dobDay, setDobDay] = useState(editing?.dob ? editing.dob.split("-")[2] : "");
  const [dobMonth, setDobMonth] = useState(
    editing?.dob ? editing.dob.split("-")[1] : "",
  );
  const [dobYear, setDobYear] = useState(
    editing?.dob ? editing.dob.split("-")[0] : "",
  );
  const [sex, setSex] = useState<SexOption | null>(
    (editing?.sex as SexOption | null) ?? null,
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isEditing = !!editing;
  const isEditingSelf = editing?.is_self === true;

  const dobValid = () => {
    if (!dobDay && !dobMonth && !dobYear) return true; // optional
    if (dobYear.length !== 4 || !dobMonth || !dobDay) return false;
    const y = parseInt(dobYear, 10);
    const m = parseInt(dobMonth, 10);
    const d = parseInt(dobDay, 10);
    if (isNaN(y) || isNaN(m) || isNaN(d)) return false;
    if (y < 1900 || y > new Date().getFullYear()) return false;
    if (m < 1 || m > 12) return false;
    if (d < 1 || d > 31) return false;
    const dt = new Date(y, m - 1, d);
    return dt.getFullYear() === y && dt.getMonth() === m - 1 && dt.getDate() === d;
  };

  const canSubmit = name.trim().length > 0 && dobValid() && !saving;

  const submit = async () => {
    if (!canSubmit) return;
    // Free users can't ADD non-self profiles.
    if (!isEditing && !isSubscribed) {
      router.replace("/paywall?trigger=family-profiles");
      return;
    }
    setError(null);
    setSaving(true);
    const dob =
      dobDay && dobMonth && dobYear
        ? `${dobYear.padStart(4, "0")}-${dobMonth.padStart(2, "0")}-${dobDay.padStart(2, "0")}`
        : null;
    try {
      if (editing) {
        const updated = await api.update(editing.profile_id, {
          name: name.trim(),
          relationship: editing.is_self ? "self" : relationship,
          dob,
          sex,
        });
        await refresh();
        queryClient.setQueryData(["profiles"], (old: Profile[] | undefined) =>
          (old ?? []).map((p) => (p.profile_id === updated.profile_id ? updated : p)),
        );
      } else {
        const created = await api.create({
          name: name.trim(),
          relationship: relationship as Exclude<Relationship, "self">,
          dob,
          sex,
        });
        await refresh();
        await setActiveProfile(created.profile_id);
      }
      router.replace("/dashboard");
    } catch (e: any) {
      setError(e?.message ?? "Could not save profile");
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={styles.container} testID="profile-add-screen">
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
      >
        <View
          style={[
            styles.header,
            { paddingTop: insets.top + spacing.md, paddingBottom: spacing.md },
          ]}
        >
          <Pressable
            testID="profile-add-close-btn"
            onPress={() => router.back()}
            style={styles.iconBtn}
            hitSlop={12}
          >
            <Ionicons name="close" size={22} color={colors.onSurface} />
          </Pressable>
          <View style={styles.headerText}>
            <Text style={styles.eyebrow}>
              {isEditing ? "EDIT PROFILE" : "NEW PROFILE"}
            </Text>
            <Text style={styles.title}>
              {isEditing
                ? isEditingSelf
                  ? "You"
                  : "Edit member"
                : "Add family member"}
            </Text>
          </View>
          <View style={{ width: 40 }} />
        </View>

        <ScrollView
          contentContainerStyle={[
            styles.scroll,
            { paddingBottom: insets.bottom + 140 },
          ]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.field}>
            <Text style={styles.label}>Name</Text>
            <TextInput
              testID="profile-add-name-input"
              value={name}
              onChangeText={setName}
              placeholder="e.g. Alex"
              placeholderTextColor={colors.muted}
              style={styles.input}
              autoCapitalize="words"
            />
          </View>

          {!isEditingSelf ? (
            <View style={styles.field}>
              <Text style={styles.label}>Relationship</Text>
              <View style={styles.chipsRow}>
                {RELATIONSHIPS.map((r) => {
                  const selected = relationship === r;
                  return (
                    <Pressable
                      key={r}
                      testID={`profile-add-rel-${r}`}
                      onPress={() => setRelationship(r)}
                      style={[
                        styles.chip,
                        selected && styles.chipSelected,
                      ]}
                    >
                      <Ionicons
                        name={RELATIONSHIP_ICONS[r] as any}
                        size={14}
                        color={selected ? colors.brandPrimary : colors.onSurfaceSecondary}
                      />
                      <Text
                        style={[
                          styles.chipText,
                          selected && styles.chipTextSelected,
                        ]}
                      >
                        {RELATIONSHIP_LABELS[r]}
                      </Text>
                    </Pressable>
                  );
                })}
              </View>
            </View>
          ) : null}

          <View style={styles.field}>
            <Text style={styles.label}>Date of birth (optional)</Text>
            <View style={styles.dobRow}>
              <TextInput
                testID="profile-add-dob-day"
                value={dobDay}
                onChangeText={(t) => setDobDay(t.replace(/\D/g, "").slice(0, 2))}
                placeholder="DD"
                placeholderTextColor={colors.muted}
                keyboardType="number-pad"
                maxLength={2}
                style={[styles.input, styles.dobPart]}
              />
              <TextInput
                testID="profile-add-dob-month"
                value={dobMonth}
                onChangeText={(t) => setDobMonth(t.replace(/\D/g, "").slice(0, 2))}
                placeholder="MM"
                placeholderTextColor={colors.muted}
                keyboardType="number-pad"
                maxLength={2}
                style={[styles.input, styles.dobPart]}
              />
              <TextInput
                testID="profile-add-dob-year"
                value={dobYear}
                onChangeText={(t) => setDobYear(t.replace(/\D/g, "").slice(0, 4))}
                placeholder="YYYY"
                placeholderTextColor={colors.muted}
                keyboardType="number-pad"
                maxLength={4}
                style={[styles.input, styles.dobYear]}
              />
            </View>
          </View>

          <View style={styles.field}>
            <Text style={styles.label}>Sex (optional)</Text>
            <View style={styles.chipsRow}>
              {SEXES.map((s) => {
                const selected = sex === s.key;
                return (
                  <Pressable
                    key={s.key}
                    testID={`profile-add-sex-${s.key}`}
                    onPress={() => setSex(selected ? null : s.key)}
                    style={[
                      styles.chip,
                      selected && styles.chipSelected,
                    ]}
                  >
                    <Text
                      style={[
                        styles.chipText,
                        selected && styles.chipTextSelected,
                      ]}
                    >
                      {s.label}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
          </View>

          {!isSubscribed && !isEditing ? (
            <View style={styles.proNotice} testID="profile-add-pro-notice">
              <Ionicons name="sparkles" size={16} color={colors.brandPrimary} />
              <Text style={styles.proNoticeText}>
                Family profiles are a{" "}
                <Text style={styles.proNoticeBold}>Pro</Text> feature.
              </Text>
            </View>
          ) : null}

          {error ? (
            <Text style={styles.errorText} testID="profile-add-error">
              {error}
            </Text>
          ) : null}
        </ScrollView>

        <View
          style={[styles.footer, { paddingBottom: insets.bottom + spacing.lg }]}
        >
          <Pressable
            testID="profile-add-save-btn"
            onPress={submit}
            disabled={!canSubmit}
            style={({ pressed }) => [
              styles.primaryBtn,
              (!canSubmit || pressed) && styles.primaryBtnDim,
            ]}
          >
            {saving ? (
              <ActivityIndicator color={colors.onBrandPrimary} />
            ) : (
              <Text style={styles.primaryBtnText}>
                {isEditing
                  ? "Save changes"
                  : isSubscribed
                    ? "Add member"
                    : "Upgrade to add"}
              </Text>
            )}
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </View>
  );
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
  scroll: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    gap: spacing.lg,
  },
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
  dobRow: { flexDirection: "row", gap: spacing.md },
  dobPart: { flex: 1, textAlign: "center" },
  dobYear: { flex: 1.4, textAlign: "center" },
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
  proNotice: {
    flexDirection: "row",
    alignItems: "center",
    gap: spacing.sm,
    padding: spacing.md,
    borderRadius: radius.md,
    backgroundColor: colors.brandTertiary,
    borderWidth: 1,
    borderColor: colors.brandSecondary,
  },
  proNoticeText: { color: colors.onSurface, fontSize: 13, flex: 1 },
  proNoticeBold: { color: colors.brandPrimary, fontWeight: "700" },
  errorText: { color: colors.error, fontSize: 14 },
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
    minHeight: 56,
  },
  primaryBtnDim: { opacity: 0.55 },
  primaryBtnText: { color: colors.onBrandPrimary, fontSize: 16, fontWeight: "700" },
});
