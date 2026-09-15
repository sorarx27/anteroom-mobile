// Design tokens for Anteroom. Light theme. Values sourced from
// /app/design_guidelines.json.
import { useMemo } from "react";
import { Appearance, StyleSheet, useColorScheme } from "react-native";

export type ColorScheme = "light" | "dark";

const light = {
  // Surfaces
  surface: "#FFFFFF",
  onSurface: "#111815",
  surfaceSecondary: "#F4F7F6",
  onSurfaceSecondary: "#2C3E36",
  surfaceTertiary: "#E6EDE9",
  onSurfaceTertiary: "#455E53",
  surfaceInverse: "#111815",
  onSurfaceInverse: "#FFFFFF",
  muted: "#698075",

  // Brand
  brand: "#4B7A68",
  onBrand: "#FFFFFF",
  brandPrimary: "#365F50",
  onBrandPrimary: "#FFFFFF",
  brandSecondary: "#DCE6E1",
  onBrandSecondary: "#2C3E36",
  brandTertiary: "#EAF0ED",
  onBrandTertiary: "#365F50",

  // Status
  success: "#2D6B4E",
  onSuccess: "#FFFFFF",
  warning: "#8F671E",
  onWarning: "#FFFFFF",
  error: "#9E3838",
  onError: "#FFFFFF",
  info: "#4B7A68",
  onInfo: "#FFFFFF",

  // Lines
  border: "#E6EDE9",
  borderStrong: "#C2D1CB",
  divider: "#E6EDE9",
};

export type ThemeColors = typeof light;

export const defaultScheme = "light" satisfies ColorScheme;
export const themes: { light: ThemeColors; dark?: ThemeColors } = { light };

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  "2xl": 32,
  "3xl": 48,
} as const;

export const radius = {
  sm: 6,
  md: 12,
  lg: 20,
  pill: 999,
} as const;

export const fontSize = {
  sm: 12,
  base: 14,
  lg: 16,
  xl: 20,
  "2xl": 24,
  "3xl": 32,
  "4xl": 40,
} as const;

export function setColorScheme(scheme: ColorScheme | null) {
  Appearance.setColorScheme?.(scheme);
}

setColorScheme?.(themes.dark ? null : defaultScheme);

export function useTheme(): { scheme: ColorScheme; colors: ThemeColors } {
  const system = useColorScheme();
  const scheme: ColorScheme = system && themes[system] ? system : defaultScheme;
  return { scheme, colors: themes[scheme] ?? themes.light };
}

export const colors = themes.light;

export function makeStyles<
  T extends StyleSheet.NamedStyles<T> | StyleSheet.NamedStyles<any>,
>(
  factory: (colors: ThemeColors) => T & StyleSheet.NamedStyles<any>,
): () => T {
  return function useStyles(): T {
    const { colors } = useTheme();
    return useMemo(() => StyleSheet.create(factory(colors)), [colors]);
  };
}
