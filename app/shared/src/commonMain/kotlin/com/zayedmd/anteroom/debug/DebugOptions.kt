package com.zayedmd.anteroom.debug

import com.zayedmd.anteroom.model.AppUser

/**
 * QA affordances for driving Anteroom on a simulator, where no UI automation is available to tap
 * through sign-in and open the paywall.
 *
 * Every flag defaults to false and can only be turned on by an explicit process launch argument.
 * An app started the normal way - Home screen, TestFlight, App Store - passes no arguments, so
 * these states are unreachable in the hands of a user. Nothing here reads from disk, the network,
 * or a build config, so there is no path to flip them accidentally.
 *
 * To drive it on a simulator:
 * ```
 * xcrun simctl launch booted com.zayedmd.anteroom \
 *   --anteroom-demo-auth --anteroom-open-paywall --anteroom-autobuy
 * ```
 *
 * Delete this file (and its three call sites) once the demo video is recorded.
 */
object DebugOptions {
    const val ARG_DEMO_AUTH = "--anteroom-demo-auth"
    const val ARG_OPEN_PAYWALL = "--anteroom-open-paywall"
    const val ARG_AUTO_PURCHASE = "--anteroom-autobuy"

    /** Skip Firebase auth and sign in a local, throwaway user. */
    var demoAuth: Boolean = false
        private set

    /** Open the paywall as soon as the Dashboard is on screen. */
    var openPaywallOnLaunch: Boolean = false
        private set

    /**
     * Run the paywall's purchase call once offerings have loaded. This invokes exactly the same
     * [com.zayedmd.anteroom.subscription.RevenueCatService.purchasePackage] the CTA does - it
     * substitutes for the tap only, not for the billing path.
     */
    var autoPurchase: Boolean = false
        private set

    fun applyLaunchArguments(arguments: List<String>) {
        demoAuth = arguments.contains(ARG_DEMO_AUTH)
        openPaywallOnLaunch = arguments.contains(ARG_OPEN_PAYWALL)
        autoPurchase = arguments.contains(ARG_AUTO_PURCHASE)
    }

    /**
     * `profile_completed = true` so [com.zayedmd.anteroom.navigation.AnteroomApp] routes straight
     * to the Dashboard instead of the profile setup screen.
     */
    val DEMO_USER = AppUser(
        user_id = "demo_user_simulator",
        email = "test1@anteroom.dev",
        name = "Sarah Jenkins",
        language = "en",
        country = "ES",
        profile_completed = true,
        provider = "demo"
    )
}
