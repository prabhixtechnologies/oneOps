package com.prabhix.operator.ui.platform

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * The admin flavor's half of the platform-feature seam. See the OneOps copy of this file for why the
 * seam exists.
 */
const val PLATFORM_FEATURE_PRESENT = true

const val PLATFORM_ROUTE = "platform"

@Composable
fun PlatformBanner() {
    ViewingOrgBanner()
}

fun NavGraphBuilder.platformDestination(onViewingTenant: () -> Unit) {
    composable(PLATFORM_ROUTE) {
        PlatformScreen(onViewingTenant = onViewingTenant)
    }
}
