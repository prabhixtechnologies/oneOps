package com.prabhix.operator.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.prabhix.operator.ui.auth.LoginScreen
import com.prabhix.operator.ui.org.OrgSelectScreen
import com.prabhix.operator.ui.platform.PlatformScreen

/**
 * Navigation for the Prabhix Admin app — the staff control tower.
 *
 * <p>One screen, because there is one job: is anything wrong across the platform, and with whom.
 * There is no bottom bar, no inbox and no shop, and none of that code is in this APK — the product
 * screens live in `src/oneops/` and this source set cannot see them.
 *
 * <p>Support work is not here either. Reading a customer's mail or chat means using their screens,
 * which means the OneOps console on the web, where the handoff carries the organization across and
 * the access is announced and recorded. A phone-sized copy of somebody else's inbox would be a
 * second implementation of the product to keep in step with the first, for the rarer half of the
 * job.
 *
 * @see com.prabhix.operator.ui.navigation.PrabhixNavHost in `src/oneops/` for the product
 */
sealed class Route(val path: String) {
    data object Login : Route("login")
    data object OrgSelect : Route("org_select")
    data object Platform : Route("platform")
}

@Composable
fun PrabhixNavHost(
    isLoggedIn: Boolean,
    hasOrg: Boolean,
    @Suppress("UNUSED_PARAMETER") isPlatformAdmin: Boolean,
    @Suppress("UNUSED_PARAMETER") deepLinkChatId: String?,
    @Suppress("UNUSED_PARAMETER") deepLinkMailId: String?,
    @Suppress("UNUSED_PARAMETER") onSessionEnded: () -> Unit = {},
) {
    val navController = rememberNavController()

    // Deep links from push are deliberately ignored: they address a conversation or a mail thread,
    // and this app has nowhere to open one. Sending staff to the platform screen instead is honest;
    // registering the routes so the notification "worked" would open a blank screen.
    val start = when {
        !isLoggedIn -> Route.Login.path
        !hasOrg -> Route.OrgSelect.path
        else -> Route.Platform.path
    }

    LaunchedEffect(isLoggedIn, hasOrg) {
        when {
            !isLoggedIn -> navController.navigate(Route.Login.path) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
            !hasOrg -> navController.navigate(Route.OrgSelect.path) {
                popUpTo(Route.Login.path) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = start,
        modifier = Modifier.padding(),
    ) {
        composable(Route.Login.path) {
            LoginScreen(onLoggedIn = {
                navController.navigate(Route.OrgSelect.path) { popUpTo(Route.Login.path) { inclusive = true } }
            })
        }
        composable(Route.OrgSelect.path) {
            OrgSelectScreen(onSelected = {
                navController.navigate(Route.Platform.path) { popUpTo(Route.OrgSelect.path) { inclusive = true } }
            })
        }
        composable(Route.Platform.path) { PlatformScreen() }
    }
}
