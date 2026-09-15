import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { profiles as api, Profile } from "@/src/api/profiles";
import { useAuth } from "@/src/auth/AuthContext";
import { storage } from "@/src/utils/storage";

const ACTIVE_PROFILE_KEY = "anteroom_active_profile_id";

type Ctx = {
  profiles: Profile[];
  self: Profile | null;
  activeProfile: Profile | null;
  setActiveProfile: (profile_id: string) => Promise<void>;
  isLoading: boolean;
  refresh: () => Promise<void>;
};

const ProfileContext = createContext<Ctx | null>(null);

export function ProfileProvider({ children }: { children: React.ReactNode }) {
  const { status, user } = useAuth();
  const queryClient = useQueryClient();
  const hydrated = useRef(false);
  const [activeId, setActiveId] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ["profiles"],
    queryFn: () => api.list(),
    enabled: status === "authenticated" && !!user?.user_id,
    staleTime: 30_000,
  });

  const profiles = query.data ?? [];
  const self = useMemo(() => profiles.find((p) => p.is_self) ?? null, [profiles]);

  // Load persisted active profile once profiles arrive
  useEffect(() => {
    if (status !== "authenticated") return;
    if (hydrated.current) return;
    if (profiles.length === 0) return;
    hydrated.current = true;
    (async () => {
      const stored = await storage.getItem(ACTIVE_PROFILE_KEY, "" as string);
      const found = stored && profiles.find((p) => p.profile_id === stored);
      setActiveId(found ? found.profile_id : self?.profile_id ?? profiles[0].profile_id);
    })();
  }, [profiles, self?.profile_id, status]);

  // Reset when the user signs out
  useEffect(() => {
    if (status === "unauthenticated") {
      hydrated.current = false;
      setActiveId(null);
      storage.removeItem(ACTIVE_PROFILE_KEY).catch(() => {});
    }
  }, [status]);

  // If active profile disappeared (e.g. deleted), fall back to self.
  useEffect(() => {
    if (!activeId || profiles.length === 0) return;
    const stillThere = profiles.find((p) => p.profile_id === activeId);
    if (!stillThere) {
      setActiveId(self?.profile_id ?? profiles[0]?.profile_id ?? null);
    }
  }, [profiles, activeId, self?.profile_id]);

  const setActiveProfile = useCallback<Ctx["setActiveProfile"]>(
    async (profile_id) => {
      setActiveId(profile_id);
      await storage.setItem(ACTIVE_PROFILE_KEY, profile_id);
      // Invalidate profile-scoped brief lists so the dashboard refetches.
      queryClient.invalidateQueries({ queryKey: ["briefs"] });
    },
    [queryClient],
  );

  const refresh = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: ["profiles"] });
  }, [queryClient]);

  const activeProfile =
    profiles.find((p) => p.profile_id === activeId) ?? self ?? null;

  const value = useMemo<Ctx>(
    () => ({
      profiles,
      self,
      activeProfile,
      setActiveProfile,
      isLoading: query.isLoading,
      refresh,
    }),
    [profiles, self, activeProfile, setActiveProfile, query.isLoading, refresh],
  );

  return <ProfileContext.Provider value={value}>{children}</ProfileContext.Provider>;
}

export function useProfiles(): Ctx {
  const ctx = useContext(ProfileContext);
  if (!ctx) throw new Error("useProfiles must be used within ProfileProvider");
  return ctx;
}
