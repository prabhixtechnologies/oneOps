package com.prabhix.operator.ui.platform

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder

/**
 * The customer product has no platform feature. This file is the OneOps half of a seam: both flavors
 * declare the same four names, and the shared navigation code calls them without knowing which
 * flavor it is compiled into.
 *
 * <h2>Why a seam and not a runtime flag</h2>
 *
 * <p>A `BuildConfig` check in shared code compiles the admin screens into both APKs and relies on
 * R8 to remove them from this one. That mostly works, but the API interface behind them survives:
 * its Hilt provider is reachable from the dependency graph whether or not any screen calls it, so
 * the customer's APK ends up naming staff-only endpoints in its strings. Keeping the screens, the
 * repository and the provider in `src/admin/` means there is nothing to strip.
 *
 * <p>`PLATFORM_FEATURE_PRESENT` is a `const val`, so the caller's branch is resolved by the Kotlin
 * compiler rather than left to the shrinker — which also means the debug build behaves like the
 * release one instead of only the release one being clean.
 */
const val PLATFORM_FEATURE_PRESENT = false

const val PLATFORM_ROUTE = "platform"

@Composable
fun PlatformBanner() {
    // No impersonation in the customer product: there is no other organization to view.
}

fun NavGraphBuilder.platformDestination(@Suppress("UNUSED_PARAMETER") onViewingTenant: () -> Unit) {
    // No destination to register.
}
