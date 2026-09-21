import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { Platform } from "react-native";
import * as Linking from "expo-linking";
import * as WebBrowser from "expo-web-browser";
import { api, ApiError, setAuthToken } from "@/src/api/client";
import { clearToken, loadToken, saveToken } from "@/src/auth/storage";
import { rcEnabled, useSubscription } from "@/src/lib/revenuecat";

WebBrowser.maybeCompleteAuthSession();

export type AppUser = {
  user_id: string;
  email: string;
  name?: string | null;
  picture?: string | null;
  dob?: string | null;
  language?: string | null;
  country?: string | null;
  profile_completed: boolean;
  provider: string;
};

type AuthContextValue = {
  status: "loading" | "authenticated" | "unauthenticated";
  user: AppUser | null;
  purchaseIdentityError: string | null;
  signInEmail: (email: string, password: string) => Promise<void>;
  signUpEmail: (email: string, password: string) => Promise<void>;
  signInWithGoogle: () => Promise<void>;
  updateProfile: (input: {
    name: string;
    dob: string;
    language: string;
    country: string;
  }) => Promise<void>;
  signOut: () => Promise<void>;
  deleteAccount: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthContextValue["status"]>("loading");
  const [user, setUser] = useState<AppUser | null>(null);
  const [purchaseIdentityError, setPurchaseIdentityError] = useState<
    string | null
  >(null);
  const consumedSessionIds = useRef<Set<string>>(new Set());
  const initialUrlHandled = useRef(false);
  const { bindIdentity, identityError } = useSubscription();

  // Keep RevenueCat identity in sync with the app user on every auth path.
  useEffect(() => {
    if (!rcEnabled) return;
    bindIdentity(user?.user_id ?? null);
  }, [user?.user_id, bindIdentity]);

  useEffect(() => {
    setPurchaseIdentityError(identityError);
  }, [identityError]);

  const applyAuth = useCallback(async (token: string, u: AppUser) => {
    await saveToken(token);
    setAuthToken(token);
    setUser(u);
    setStatus("authenticated");
  }, []);

  const doSignOut = useCallback(async () => {
    try {
      await api.post("/auth/logout");
    } catch {
      /* ignore */
    }
    await clearToken();
    setAuthToken(null);
    setUser(null);
    setStatus("unauthenticated");
  }, []);

  const doDeleteAccount = useCallback(async () => {
    // Server first: if this throws, the user stays signed in and sees the error
    // rather than being logged out of an account that still exists.
    await api.del("/auth/account");
    await clearToken();
    setAuthToken(null);
    setUser(null);
    setStatus("unauthenticated");
  }, []);

  const extractSessionId = (url: string | null | undefined): string | null => {
    if (!url) return null;
    const m = url.match(/[?#&]session_id=([^&#]+)/);
    return m ? decodeURIComponent(m[1]) : null;
  };

  const exchangeSessionId = useCallback(
    async (sessionId: string) => {
      if (consumedSessionIds.current.has(sessionId)) return;
      consumedSessionIds.current.add(sessionId);
      try {
        const res = await api.post<{ session_token: string; user: AppUser }>(
          "/auth/session",
          { session_id: sessionId },
        );
        await applyAuth(res.session_token, res.user);
        if (Platform.OS === "web") {
          try {
            const url = new URL(window.location.href);
            url.searchParams.delete("session_id");
            let hash = url.hash.replace(/[?#&]?session_id=[^&#]+/g, "");
            hash = hash === "#" ? "" : hash;
            window.history.replaceState(
              window.history.state,
              "",
              url.pathname + url.search + hash,
            );
          } catch {
            /* ignore */
          }
        }
      } catch (err) {
        console.warn("Google session exchange failed", err);
      }
    },
    [applyAuth],
  );

  // On mount: check for session_id in URL, else load existing token
  useEffect(() => {
    let mounted = true;

    const boot = async () => {
      // 1. Web: check URL for session_id first
      if (Platform.OS === "web") {
        const sid =
          extractSessionId(window.location.hash) ||
          extractSessionId(window.location.search);
        if (sid) {
          await exchangeSessionId(sid);
          return;
        }
      } else if (!initialUrlHandled.current) {
        initialUrlHandled.current = true;
        const initialUrl = await Linking.getInitialURL();
        const sid = extractSessionId(initialUrl);
        if (sid) {
          await exchangeSessionId(sid);
          return;
        }
      }

      // 2. Existing token
      const token = await loadToken();
      if (!token) {
        if (mounted) setStatus("unauthenticated");
        return;
      }
      setAuthToken(token);
      try {
        const me = await api.get<AppUser>("/auth/me");
        if (!mounted) return;
        setUser(me);
        setStatus("authenticated");
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) {
          await clearToken();
          setAuthToken(null);
        }
        if (mounted) setStatus("unauthenticated");
      }
    };

    boot();

    let sub: { remove: () => void } | null = null;
    if (Platform.OS !== "web") {
      sub = Linking.addEventListener("url", ({ url }) => {
        const sid = extractSessionId(url);
        if (sid) exchangeSessionId(sid);
      });
    }
    return () => {
      mounted = false;
      sub?.remove();
    };
  }, [exchangeSessionId]);

  const signInEmail = useCallback(
    async (email: string, password: string) => {
      const res = await api.post<{ session_token: string; user: AppUser }>(
        "/auth/login",
        { email, password },
      );
      await applyAuth(res.session_token, res.user);
    },
    [applyAuth],
  );

  const signUpEmail = useCallback(
    async (email: string, password: string) => {
      const res = await api.post<{ session_token: string; user: AppUser }>(
        "/auth/register",
        { email, password },
      );
      await applyAuth(res.session_token, res.user);
    },
    [applyAuth],
  );

  const signInWithGoogle = useCallback(async () => {
    const redirectUrl =
      Platform.OS === "web"
        ? window.location.origin + "/"
        : Linking.createURL("");
    const authUrl = `https://auth.emergentagent.com/?redirect=${encodeURIComponent(
      redirectUrl,
    )}`;

    if (Platform.OS === "web") {
      window.location.href = authUrl;
      return;
    }

    // Pre-register url listener so we don't miss the deep link on Android
    let capturedUrl: string | null = null;
    const sub = Linking.addEventListener("url", ({ url }) => {
      capturedUrl = url;
    });
    try {
      const result = await WebBrowser.openAuthSessionAsync(authUrl, redirectUrl);
      let finalUrl: string | null = null;
      if (result.type === "success" && (result as any).url) {
        finalUrl = (result as any).url;
      }
      if (!finalUrl && capturedUrl) finalUrl = capturedUrl;
      if (!finalUrl) finalUrl = await Linking.getInitialURL();
      const sid = extractSessionId(finalUrl);
      if (sid) await exchangeSessionId(sid);
    } finally {
      sub.remove();
    }
  }, [exchangeSessionId]);

  const updateProfile = useCallback<AuthContextValue["updateProfile"]>(
    async (input) => {
      const updated = await api.put<AppUser>("/auth/profile", input);
      setUser(updated);
    },
    [],
  );

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      purchaseIdentityError,
      signInEmail,
      signUpEmail,
      signInWithGoogle,
      updateProfile,
      signOut: doSignOut,
      deleteAccount: doDeleteAccount,
    }),
    [
      status,
      user,
      purchaseIdentityError,
      signInEmail,
      signUpEmail,
      signInWithGoogle,
      updateProfile,
      doSignOut,
      doDeleteAccount,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
