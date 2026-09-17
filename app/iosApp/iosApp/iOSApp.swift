import SwiftUI
import FirebaseCore
import Shared

@main
struct iOSApp: App {
    init() {
        // Firebase reads GoogleService-Info.plist from the app bundle. GitLive's Kotlin
        // wrappers call into FIRApp, so this has to happen before any repository is touched.
        FirebaseApp.configure()

        // Simulator QA flags. A normally launched app passes no arguments, so these stay off.
        IosLaunchArgumentsKt.applyDebugLaunchArguments()

        // Configure StoreKit through purchases-kmp before the first Compose frame, so the
        // offerings cache is warm by the time the paywall can be reached. The API key is
        // resolved in Kotlin (RevenueCatConfig.iosApiKey): the appl_ App Store key when one
        // exists, the RevenueCat Test Store key until then.
        IosPurchasesKt.startRevenueCat()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
