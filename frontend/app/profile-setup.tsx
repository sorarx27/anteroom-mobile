import { useMemo, useState } from "react";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import Ionicons from "@react-native-vector-icons/ionicons";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { useAuth } from "@/src/auth/AuthContext";
import { colors, radius, spacing } from "@/src/theme";

type Language = { code: "en" | "es"; label: string; flag: string };
const LANGUAGES: Language[] = [
  { code: "en", label: "English", flag: "🇬🇧" },
  { code: "es", label: "Español", flag: "🇪🇸" },
];

type Country = { code: string; label: string };
const COUNTRIES: Country[] = [
  { code: "US", label: "United States" },
  { code: "ES", label: "Spain" },
  { code: "MX", label: "Mexico" },
  { code: "GB", label: "United Kingdom" },
  { code: "DE", label: "Germany" },
  { code: "FR", label: "France" },
  { code: "IT", label: "Italy" },
  { code: "PT", label: "Portugal" },
  { code: "AR", label: "Argentina" },
  { code: "BR", label: "Brazil" },
  { code: "CO", label: "Colombia" },
  { code: "CL", label: "Chile" },
  { code: "CA", label: "Canada" },
  { code: "AU", label: "Australia" },
  { code: "IE", label: "Ireland" },
  { code: "NL", label: "Netherlands" },
  { code: "BE", label: "Belgium" },
  { code: "CH", label: "Switzerland" },
  { code: "AT", label: "Austria" },
  { code: "OTHER", label: "Other" },
];

export default function ProfileSetup() {
  const insets = useSafeAreaInsets();
  const { user, updateProfile, signOut } = useAuth();

  const [name, setName] = useState(user?.name ?? "");
  const [dobYear, setDobYear] = useState("");
  const [dobMonth, setDobMonth] = useState("");
  const [dobDay, setDobDay] = useState("");
  const [language, setLanguage] = useState<"en" | "es" | null>(null);
  const [country, setCountry] = useState<Country | null>(null);
  const [countryOpen, setCountryOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dobValid = useMemo(() => {
    if (dobYear.length !== 4 || !dobMonth || !dobDay) return false;
    const y = parseInt(dobYear, 10);
    const m = parseInt(dobMonth, 10);
    const d = parseInt(dobDay, 10);
    if (isNaN(y) || isNaN(m) || isNaN(d)) return false;
    if (y < 1900 || y > new Date().getFullYear()) return false;
    if (m < 1 || m > 12) return false;
    if (d < 1 || d > 31) return false;
    const dt = new Date(y, m - 1, d);
    return (
      dt.getFullYear() === y && dt.getMonth() === m - 1 && dt.getDate() === d
    );
  }, [dobYear, dobMonth, dobDay]);

  const canSubmit =
    name.trim().length > 0 && dobValid && !!language && !!country && !loading;

  const submit = async () => {
    if (!canSubmit) return;
    setError(null);
    setLoading(true);
    try {
      const dob = `${dobYear.padStart(4, "0")}-${dobMonth.padStart(2, "0")}-${dobDay.padStart(2, "0")}`;
      await updateProfile({
        name: name.trim(),
        dob,
        language: language!,
        country: country!.label,
      });
    } catch (e: any) {
      setError(e?.message ?? "Could not save profile");
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container} testID="profile-setup-screen">
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : "height"}
      >
        <ScrollView
          contentContainerStyle={[
            styles.scroll,
            {
              paddingTop: insets.top + spacing.xl,
              paddingBottom: insets.bottom + 140,
            },
          ]}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.header}>
            <Text style={styles.eyebrow}>STEP 2 OF 2</Text>
            <Text style={styles.title} testID="profile-setup-title">
              A quick profile.
            </Text>
            <Text style={styles.subtitle}>
              These details make each brief personal and translated in your language.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionLabel}>Personal</Text>
            <View style={styles.field}>
              <Text style={styles.label}>Full name</Text>
              <TextInput
                testID="profile-name-input"
                value={name}
                onChangeText={setName}
                placeholder="Alex Rivera"
                placeholderTextColor={colors.muted}
                style={styles.input}
                autoCapitalize="words"
              />
            </View>
            <View style={styles.field}>
              <Text style={styles.label}>Date of birth</Text>
              <View style={styles.dobRow}>
                <TextInput
                  testID="profile-dob-day"
                  value={dobDay}
                  onChangeText={(t) => setDobDay(t.replace(/\D/g, "").slice(0, 2))}
                  placeholder="DD"
                  placeholderTextColor={colors.muted}
                  keyboardType="number-pad"
                  maxLength={2}
                  style={[styles.input, styles.dobPart]}
                />
                <TextInput
                  testID="profile-dob-month"
                  value={dobMonth}
                  onChangeText={(t) => setDobMonth(t.replace(/\D/g, "").slice(0, 2))}
                  placeholder="MM"
                  placeholderTextColor={colors.muted}
                  keyboardType="number-pad"
                  maxLength={2}
                  style={[styles.input, styles.dobPart]}
                />
                <TextInput
                  testID="profile-dob-year"
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
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionLabel}>Preferences</Text>
            <View style={styles.field}>
              <Text style={styles.label}>Language</Text>
              <View style={styles.langRow}>
                {LANGUAGES.map((l) => {
                  const selected = language === l.code;
                  return (
                    <Pressable
                      key={l.code}
                      testID={`profile-lang-${l.code}`}
                      onPress={() => setLanguage(l.code)}
                      style={[
                        styles.langChip,
                        selected && styles.langChipSelected,
                      ]}
                    >
                      <Text style={styles.langFlag}>{l.flag}</Text>
                      <Text
                        style={[
                          styles.langLabel,
                          selected && styles.langLabelSelected,
                        ]}
                      >
                        {l.label}
                      </Text>
                    </Pressable>
                  );
                })}
              </View>
            </View>
            <View style={styles.field}>
              <Text style={styles.label}>Country</Text>
              <Pressable
                testID="profile-country-select"
                onPress={() => setCountryOpen(true)}
                style={[styles.input, styles.selectRow]}
              >
                <Text
                  style={{
                    color: country ? colors.onSurface : colors.muted,
                    fontSize: 16,
                  }}
                >
                  {country ? country.label : "Select country"}
                </Text>
                <Ionicons name="chevron-forward" size={18} color={colors.muted} />
              </Pressable>
            </View>
          </View>

          {error ? (
            <Text style={styles.errorText} testID="profile-error">
              {error}
            </Text>
          ) : null}

          <Pressable
            testID="profile-signout-btn"
            onPress={signOut}
            style={styles.signOutBtn}
          >
            <Text style={styles.signOutText}>Sign out</Text>
          </Pressable>
        </ScrollView>

        <View
          style={[
            styles.footer,
            { paddingBottom: insets.bottom + spacing.lg },
          ]}
        >
          <Pressable
            testID="profile-submit-btn"
            onPress={submit}
            disabled={!canSubmit}
            style={({ pressed }) => [
              styles.primaryBtn,
              (!canSubmit || pressed) && styles.primaryBtnDim,
            ]}
          >
            {loading ? (
              <ActivityIndicator color={colors.onBrandPrimary} />
            ) : (
              <Text style={styles.primaryBtnText}>Complete profile</Text>
            )}
          </Pressable>
        </View>
      </KeyboardAvoidingView>

      <Modal
        visible={countryOpen}
        animationType="slide"
        transparent
        onRequestClose={() => setCountryOpen(false)}
      >
        <Pressable
          style={styles.modalBackdrop}
          onPress={() => setCountryOpen(false)}
        />
        <View
          style={[
            styles.modalSheet,
            { paddingBottom: insets.bottom + spacing.md },
          ]}
        >
          <View style={styles.modalHandle} />
          <Text style={styles.modalTitle}>Select country</Text>
          <ScrollView showsVerticalScrollIndicator={false}>
            {COUNTRIES.map((c) => {
              const selected = country?.code === c.code;
              return (
                <Pressable
                  key={c.code}
                  testID={`profile-country-option-${c.code}`}
                  onPress={() => {
                    setCountry(c);
                    setCountryOpen(false);
                  }}
                  style={styles.countryRow}
                >
                  <Text
                    style={[
                      styles.countryLabel,
                      selected && { color: colors.brandPrimary, fontWeight: "700" },
                    ]}
                  >
                    {c.label}
                  </Text>
                  {selected ? (
                    <Ionicons
                      name="checkmark"
                      size={20}
                      color={colors.brandPrimary}
                    />
                  ) : null}
                </Pressable>
              );
            })}
          </ScrollView>
        </View>
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.surface },
  scroll: {
    paddingHorizontal: spacing.xl,
    gap: spacing.xl,
  },
  header: { gap: spacing.sm },
  eyebrow: {
    color: colors.brandPrimary,
    fontSize: 11,
    letterSpacing: 2,
    fontWeight: "700",
  },
  title: {
    color: colors.onSurface,
    fontSize: 30,
    fontWeight: "700",
    letterSpacing: -0.5,
  },
  subtitle: { color: colors.muted, fontSize: 15, lineHeight: 21 },
  section: { gap: spacing.lg },
  sectionLabel: {
    color: colors.onSurfaceTertiary,
    fontSize: 12,
    letterSpacing: 1.5,
    fontWeight: "700",
    textTransform: "uppercase",
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
  langRow: { flexDirection: "row", gap: spacing.md },
  langChip: {
    flex: 1,
    backgroundColor: colors.surfaceSecondary,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: radius.md,
    paddingVertical: 14,
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "center",
    gap: spacing.sm,
  },
  langChipSelected: {
    backgroundColor: colors.brandTertiary,
    borderColor: colors.brandPrimary,
  },
  langFlag: { fontSize: 18 },
  langLabel: { color: colors.onSurfaceSecondary, fontSize: 15, fontWeight: "600" },
  langLabelSelected: { color: colors.brandPrimary },
  selectRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  errorText: { color: colors.error, fontSize: 14 },
  signOutBtn: { alignItems: "center", paddingVertical: spacing.md },
  signOutText: { color: colors.muted, fontSize: 14, fontWeight: "600" },
  footer: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.md,
    backgroundColor: "rgba(255,255,255,0.94)",
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
  modalBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(17,24,21,0.4)",
  },
  modalSheet: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    maxHeight: "70%",
    backgroundColor: colors.surface,
    borderTopLeftRadius: radius.lg,
    borderTopRightRadius: radius.lg,
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.sm,
  },
  modalHandle: {
    alignSelf: "center",
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.border,
    marginBottom: spacing.md,
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: "700",
    color: colors.onSurface,
    marginBottom: spacing.md,
  },
  countryRow: {
    paddingVertical: 14,
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },
  countryLabel: { color: colors.onSurface, fontSize: 16 },
});
