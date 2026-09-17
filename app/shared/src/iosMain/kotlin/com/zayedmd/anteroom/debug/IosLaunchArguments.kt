package com.zayedmd.anteroom.debug

import platform.Foundation.NSProcessInfo

/**
 * Reads the simulator launch arguments into [DebugOptions]. Called from `iOSApp.init()` as
 * `IosLaunchArgumentsKt.applyDebugLaunchArguments()`, before any Compose state is built.
 *
 * A normally launched app has only argv[0], so every flag stays false.
 */
fun applyDebugLaunchArguments() {
    val arguments = NSProcessInfo.processInfo.arguments.map { it.toString() }
    DebugOptions.applyLaunchArguments(arguments)
}
