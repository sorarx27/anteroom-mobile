package com.zayedmd.anteroom.ui

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy

/**
 * Teaches Coil how to fetch over the network, once, for the whole app.
 *
 * Coil 3 ships no networking in its core on any target, and the singleton
 * loader only picks the Ktor fetcher up automatically through a JVM
 * `ServiceLoader` — which does not exist on iOS or the web, where the pages
 * would silently fail to load. Registering it explicitly makes all four
 * targets behave the same way.
 *
 * The disk cache stays off deliberately. These images are photographs of
 * medical paperwork; caching them to unencrypted app storage would keep a copy
 * of a patient's referral on the device after the brief is deleted. The
 * in-memory cache still covers scrolling the thumbnail strip.
 */
@Composable
fun ConfigureImageLoading() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
}
