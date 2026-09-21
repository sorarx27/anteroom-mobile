import { useRef, useState } from "react";
import {
  Dimensions,
  FlatList,
  ImageBackground,
  NativeScrollEvent,
  NativeSyntheticEvent,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { useRouter } from "expo-router";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { colors, radius, spacing } from "@/src/theme";

const { width: SCREEN_W } = Dimensions.get("window");

type Slide = {
  key: string;
  image: string;
  title: string;
  subtitle: string;
};

const SLIDES: Slide[] = [
  {
    key: "s1",
    image:
      "https://images.unsplash.com/photo-1500067803284-4304564c8655?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA2ODl8MHwxfHNlYXJjaHwxfHxtZXNzeSUyMG1lZGljYWwlMjBwYXBlcnMlMjBvbiUyMGRlc2t8ZW58MHx8fHwxNzg5NDg1NjA0fDA&ixlib=rb-4.1.0&q=85",
    title: "Snap the paperwork.",
    subtitle:
      "Referral letters, med lists, lab photos — Anteroom reads them and pulls out what matters.",
  },
  {
    key: "s2",
    image:
      "https://images.unsplash.com/photo-1651760680066-db9d32bd0357?crop=entropy&cs=srgb&fm=jpg&ixid=M3w4NjA1MDV8MHwxfHNlYXJjaHwxfHxlbXB0eSUyMG1lZGljYWwlMjBjbGlwYm9hcmQlMjBjbGVhbnxlbnwwfHx8fDE3ODk0ODU2MDR8MA&ixlib=rb-4.1.0&q=85",
    title: "Walk in doctor-ready.",
    subtitle:
      "One clean page. Flags unreadable high-risk doses instead of guessing. Export a PDF with a clinic QR.\n\nAnteroom does not diagnose and does not recommend treatment. It organises what is already written on your documents.",
  },
];

export default function Onboarding() {
  const router = useRouter();
  const insets = useSafeAreaInsets();
  const [index, setIndex] = useState(0);
  const listRef = useRef<FlatList<Slide>>(null);

  const onScroll = (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    const i = Math.round(e.nativeEvent.contentOffset.x / SCREEN_W);
    if (i !== index) setIndex(i);
  };

  const next = () => {
    if (index < SLIDES.length - 1) {
      const nextIndex = index + 1;
      setIndex(nextIndex);
      listRef.current?.scrollToOffset({
        offset: nextIndex * SCREEN_W,
        animated: true,
      });
    } else {
      router.push("/auth?mode=signup");
    }
  };

  return (
    <View style={styles.container} testID="onboarding-screen">
      <FlatList
        ref={listRef}
        data={SLIDES}
        horizontal
        pagingEnabled
        showsHorizontalScrollIndicator={false}
        onScroll={onScroll}
        scrollEventThrottle={16}
        keyExtractor={(s) => s.key}
        renderItem={({ item }) => (
          <View style={{ width: SCREEN_W, height: "100%" }}>
            <ImageBackground
              source={{ uri: item.image }}
              style={StyleSheet.absoluteFillObject}
              resizeMode="cover"
            >
              <LinearGradient
                colors={["rgba(17,24,21,0.15)", "rgba(17,24,21,0.9)"]}
                locations={[0.2, 0.95]}
                style={StyleSheet.absoluteFillObject}
              />
            </ImageBackground>
            <View
              style={[
                styles.slideContent,
                { paddingTop: insets.top + spacing.xl },
              ]}
            >
              <View style={styles.textBlock}>
                <Text style={styles.title}>{item.title}</Text>
                <Text style={styles.subtitle}>{item.subtitle}</Text>
              </View>
            </View>
          </View>
        )}
      />

      <View
        style={[
          styles.footer,
          { paddingBottom: insets.bottom + spacing.lg },
        ]}
        pointerEvents="box-none"
      >
        <View style={styles.dots}>
          {SLIDES.map((s, i) => (
            <View
              key={s.key}
              style={[styles.dot, i === index && styles.dotActive]}
            />
          ))}
        </View>
        <Pressable
          testID="onboarding-next-btn"
          onPress={next}
          style={({ pressed }) => [styles.cta, pressed && styles.pressed]}
        >
          <Text style={styles.ctaText}>
            {index < SLIDES.length - 1 ? "Next" : "Create account"}
          </Text>
        </Pressable>
        <Pressable
          testID="onboarding-signin-link"
          onPress={() => router.push("/auth?mode=signin")}
          style={styles.linkBtn}
        >
          <Text style={styles.linkText}>
            Already have an account?{" "}
            <Text style={styles.linkTextBold}>Sign in</Text>
          </Text>
        </Pressable>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.surfaceInverse },
  slideContent: {
    flex: 1,
    paddingHorizontal: spacing.xl,
    justifyContent: "flex-end",
    paddingBottom: 220,
  },
  textBlock: { gap: spacing.md },
  title: {
    color: colors.onSurfaceInverse,
    fontSize: 34,
    fontWeight: "700",
    lineHeight: 40,
    letterSpacing: -0.5,
  },
  subtitle: {
    color: "rgba(255,255,255,0.85)",
    fontSize: 16,
    lineHeight: 22,
  },
  footer: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.lg,
    gap: spacing.md,
  },
  dots: {
    flexDirection: "row",
    gap: 6,
    justifyContent: "center",
    marginBottom: spacing.sm,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: "rgba(255,255,255,0.35)",
  },
  dotActive: {
    width: 22,
    backgroundColor: colors.onSurfaceInverse,
  },
  cta: {
    backgroundColor: colors.onSurfaceInverse,
    borderRadius: radius.pill,
    paddingVertical: 18,
    alignItems: "center",
  },
  pressed: { opacity: 0.85 },
  ctaText: { color: colors.brandPrimary, fontSize: 16, fontWeight: "700" },
  linkBtn: { alignItems: "center", paddingVertical: spacing.sm },
  linkText: { color: "rgba(255,255,255,0.85)", fontSize: 14 },
  linkTextBold: { color: colors.onSurfaceInverse, fontWeight: "700" },
});
