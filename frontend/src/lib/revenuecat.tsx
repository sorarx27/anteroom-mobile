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
import Purchases, { LOG_LEVEL } from "react-native-purchases";
import type { CustomerInfo, PurchasesPackage } from "react-native-purchases";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

const REVENUECAT_TEST_API_KEY = process.env.EXPO_PUBLIC_REVENUECAT_TEST_API_KEY;
const REVENUECAT_IOS_API_KEY = process.env.EXPO_PUBLIC_REVENUECAT_IOS_API_KEY;
const REVENUECAT_ANDROID_API_KEY = process.env.EXPO_PUBLIC_REVENUECAT_ANDROID_API_KEY;

// entitlement_lookup_key from /setup response
export const REVENUECAT_ENTITLEMENT_IDENTIFIER = "pro";

// Web preview uses the Test Store; production web has no store.
export const rcEnabled = Platform.OS !== "web" || __DEV__;

function getRevenueCatApiKey(): string {
  if (
    !REVENUECAT_TEST_API_KEY ||
    !REVENUECAT_IOS_API_KEY ||
    !REVENUECAT_ANDROID_API_KEY
  ) {
    throw new Error(
      "RevenueCat public API keys not found — run the Setup section first",
    );
  }
  if (Platform.OS === "web" || __DEV__) {
    // Expo Go and web preview → Test Store
    return REVENUECAT_TEST_API_KEY;
  }
  if (Platform.OS === "ios") return REVENUECAT_IOS_API_KEY;
  if (Platform.OS === "android") return REVENUECAT_ANDROID_API_KEY;
  return REVENUECAT_TEST_API_KEY;
}

export function initializeRevenueCat() {
  if (!rcEnabled) return;
  Purchases.setLogLevel(__DEV__ ? LOG_LEVEL.DEBUG : LOG_LEVEL.WARN);
  Purchases.configure({ apiKey: getRevenueCatApiKey() });
}

type Ctx = {
  customerInfo: CustomerInfo | undefined;
  offerings: Awaited<ReturnType<typeof Purchases.getOfferings>> | undefined;
  isSubscribed: boolean;
  identityReady: boolean;
  currentAppUserId: string | null;
  identityError: string | null;
  isLoading: boolean;
  purchase: (pkg: PurchasesPackage) => Promise<CustomerInfo>;
  restore: () => Promise<CustomerInfo>;
  isPurchasing: boolean;
  isRestoring: boolean;
  purchaseError: unknown;
  resetPurchaseError: () => void;
  // Called by AuthContext on every auth path.
  bindIdentity: (userId: string | null) => Promise<void>;
};

const Context = createContext<Ctx | null>(null);

function useSubscriptionContext(): Ctx {
  const queryClient = useQueryClient();
  const [currentAppUserId, setCurrentAppUserId] = useState<string | null>(null);
  const [identityError, setIdentityError] = useState<string | null>(null);
  const identityRef = useRef<string | null>(null);

  const customerInfoQuery = useQuery({
    queryKey: ["revenuecat", "customer-info"],
    queryFn: () => Purchases.getCustomerInfo(),
    enabled: rcEnabled,
    staleTime: 60 * 1000,
    retry: 1,
  });

  const offeringsQuery = useQuery({
    queryKey: ["revenuecat", "offerings"],
    queryFn: () => Purchases.getOfferings(),
    enabled: rcEnabled,
    staleTime: 300 * 1000,
    retry: 1,
  });

  useEffect(() => {
    if (!rcEnabled) return;
    const listener = (info: CustomerInfo) =>
      queryClient.setQueryData(["revenuecat", "customer-info"], info);
    Purchases.addCustomerInfoUpdateListener(listener);
    // Initial read
    Purchases.getAppUserID()
      .then((id) => setCurrentAppUserId(id))
      .catch(() => {});
    return () => {
      Purchases.removeCustomerInfoUpdateListener(listener);
    };
  }, [queryClient]);

  const bindIdentity = useCallback<Ctx["bindIdentity"]>(async (userId) => {
    if (!rcEnabled) return;
    try {
      if (userId && identityRef.current !== userId) {
        await Purchases.logIn(userId);
        identityRef.current = userId;
        const current = await Purchases.getAppUserID();
        setCurrentAppUserId(current);
        setIdentityError(null);
        // Refresh customer info now that identity changed
        const fresh = await Purchases.getCustomerInfo();
        queryClient.setQueryData(["revenuecat", "customer-info"], fresh);
      } else if (!userId && identityRef.current) {
        await Purchases.logOut();
        identityRef.current = null;
        const current = await Purchases.getAppUserID();
        setCurrentAppUserId(current);
      }
    } catch (e: any) {
      setIdentityError(e?.message ?? String(e));
    }
  }, [queryClient]);

  const purchaseMutation = useMutation({
    mutationFn: async (pkg: PurchasesPackage) => {
      const id = await Purchases.getAppUserID();
      if (!id || id.startsWith("$RCAnonymousID:"))
        throw new Error("identity_not_ready");
      const { customerInfo } = await Purchases.purchasePackage(pkg);
      queryClient.setQueryData(["revenuecat", "customer-info"], customerInfo);
      return customerInfo;
    },
  });

  const restoreMutation = useMutation({
    mutationFn: async () => {
      const info = await Purchases.restorePurchases();
      queryClient.setQueryData(["revenuecat", "customer-info"], info);
      return info;
    },
  });

  const isSubscribed =
    customerInfoQuery.data?.entitlements.active?.[
      REVENUECAT_ENTITLEMENT_IDENTIFIER
    ] !== undefined;

  const identityReady =
    !!currentAppUserId && !currentAppUserId.startsWith("$RCAnonymousID:");

  return {
    customerInfo: customerInfoQuery.data,
    offerings: offeringsQuery.data,
    isSubscribed,
    identityReady,
    currentAppUserId,
    identityError,
    isLoading: customerInfoQuery.isLoading || offeringsQuery.isLoading,
    purchase: purchaseMutation.mutateAsync,
    restore: restoreMutation.mutateAsync,
    isPurchasing: purchaseMutation.isPending,
    isRestoring: restoreMutation.isPending,
    purchaseError: purchaseMutation.error,
    resetPurchaseError: () => purchaseMutation.reset(),
    bindIdentity,
  };
}

export function SubscriptionProvider({ children }: { children: React.ReactNode }) {
  const value = useSubscriptionContext();
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useSubscription(): Ctx {
  const ctx = useContext(Context);
  if (!ctx) throw new Error("useSubscription must be used within SubscriptionProvider");
  return ctx;
}
