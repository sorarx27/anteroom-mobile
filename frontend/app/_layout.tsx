import { QueryClientProvider } from "@tanstack/react-query";
import { Stack, useRouter, useSegments } from "expo-router";
import { LogBox } from "react-native";
import { GestureHandlerRootView } from "react-native-gesture-handler";
import { SafeAreaProvider } from "react-native-safe-area-context";
import { StatusBar } from "expo-status-bar";
import { useEffect } from "react";

import { ErrorBoundary } from "@/src/components/error-boundary";
import { queryClient } from "@/src/query-client";
import { AuthProvider, useAuth } from "@/src/auth/AuthContext";
import { SubscriptionProvider, initializeRevenueCat } from "@/src/lib/revenuecat";
import { ProfileProvider } from "@/src/profiles/ProfileContext";

LogBox.ignoreAllLogs(true);

// One-time RevenueCat SDK init at module scope, BEFORE any component mounts.
try {
  initializeRevenueCat();
} catch (err) {
  console.warn("RevenueCat unavailable:", err);
}

const PUBLIC_ROUTES = new Set(["", "index", "onboarding", "auth"]);

function AuthGate({ children }: { children: React.ReactNode }) {
  const { status, user } = useAuth();
  const router = useRouter();
  const segments = useSegments();

  useEffect(() => {
    if (status === "loading") return;
    const current = segments[0] ?? "";

    if (status === "authenticated" && user) {
      if (!user.profile_completed) {
        if (current !== "profile-setup") router.replace("/profile-setup");
        return;
      }
      if (
        current === "auth" ||
        current === "onboarding" ||
        current === "" ||
        current === "index" ||
        current === "profile-setup"
      ) {
        router.replace("/dashboard");
      }
      return;
    }

    if (!PUBLIC_ROUTES.has(current)) router.replace("/");
  }, [status, user, segments, router]);

  return <>{children}</>;
}

export default function RootLayout() {
  return (
    <ErrorBoundary>
      <GestureHandlerRootView style={{ flex: 1 }}>
        <SafeAreaProvider>
          <QueryClientProvider client={queryClient}>
            <SubscriptionProvider>
              <AuthProvider>
                <ProfileProvider>
                  <StatusBar style="auto" />
                  <AuthGate>
                    <Stack
                      screenOptions={{
                        headerShown: false,
                        animation: "fade",
                        contentStyle: { backgroundColor: "#FFFFFF" },
                      }}
                    />
                  </AuthGate>
                </ProfileProvider>
              </AuthProvider>
            </SubscriptionProvider>
          </QueryClientProvider>
        </SafeAreaProvider>
      </GestureHandlerRootView>
    </ErrorBoundary>
  );
}
