package com.prabhix.operator.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prabhix.operator.ui.auth.LoginScreen
import com.prabhix.operator.ui.chat.ChatDetailScreen
import com.prabhix.operator.ui.chat.ChatInboxScreen
import com.prabhix.operator.ui.dashboard.DashboardScreen
import com.prabhix.operator.ui.mail.MailDetailScreen
import com.prabhix.operator.ui.mail.MailInboxScreen
import com.prabhix.operator.ui.org.OrgSelectScreen
import com.prabhix.operator.ui.platform.PLATFORM_FEATURE_PRESENT
import com.prabhix.operator.ui.platform.PLATFORM_ROUTE
import com.prabhix.operator.ui.platform.PlatformBanner
import com.prabhix.operator.ui.platform.platformDestination
import com.prabhix.operator.ui.visitors.VisitorsScreen

sealed class Route(val path: String) {
    data object Login : Route("login")
    data object OrgSelect : Route("org_select")
    data object Dashboard : Route("dashboard")
    data object Chat : Route("chat")
    data object ChatDetail : Route("chat/{id}") {
        fun create(id: String) = "chat/$id"
    }
    data object Mail : Route("mail")
    data object MailDetail : Route("mail/{id}") {
        fun create(id: String) = "mail/$id"
    }
    data object Visitors : Route("visitors")

    /** Registered by the admin flavor only. The path is shared so both halves agree on it. */
    data object Platform : Route(PLATFORM_ROUTE)
}

@Composable
fun PrabhixNavHost(
    isLoggedIn: Boolean,
    hasOrg: Boolean,
    isPlatformAdmin: Boolean,
    deepLinkChatId: String?,
    deepLinkMailId: String?,
    onSessionEnded: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    // Both conditions matter. The flavor decides whether this build contains the platform screens at
    // all, and the authority decides whether this particular account may use them — staff who lose
    // the authority should lose the tab without needing a new APK. The server refuses the calls
    // regardless of what this says.
    val showPlatform = PLATFORM_FEATURE_PRESENT && isPlatformAdmin

    val start = when {
        !isLoggedIn -> Route.Login.path
        !hasOrg -> Route.OrgSelect.path
        deepLinkChatId != null -> Route.ChatDetail.create(deepLinkChatId)
        deepLinkMailId != null -> Route.MailDetail.create(deepLinkMailId)
        // Staff open the app to check the platform, not to read one organization's chat.
        showPlatform -> Route.Platform.path
        else -> Route.Chat.path
    }

    val tabs = buildList {
        add(Triple(Route.Dashboard.path, "Home", Icons.Default.Home))
        add(Triple(Route.Chat.path, "Chat", Icons.AutoMirrored.Filled.List))
        add(Triple(Route.Mail.path, "Mail", Icons.Default.Email))
        add(Triple(Route.Visitors.path, "Live", Icons.Default.Group))
        if (showPlatform) add(Triple(Route.Platform.path, "Platform", Icons.Default.Apartment))
    }

    val showBottomBar = isLoggedIn && hasOrg && current in tabs.map { it.first }.toSet()

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

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { (route, label, icon) ->
                        NavigationBarItem(
                            selected = current == route,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // Above the nav host rather than inside each screen, so it cannot be scrolled away or
            // forgotten on a screen someone adds later. Draws nothing unless impersonating, and
            // nothing at all in the customer product.
            if (PLATFORM_FEATURE_PRESENT && isLoggedIn) PlatformBanner()

            NavHost(
                navController = navController,
                startDestination = start,
            ) {
                composable(Route.Login.path) {
                    LoginScreen(onLoggedIn = {
                        navController.navigate(Route.OrgSelect.path) { popUpTo(Route.Login.path) { inclusive = true } }
                    })
                }
                composable(Route.OrgSelect.path) {
                    OrgSelectScreen(onSelected = {
                        navController.navigate(Route.Chat.path) { popUpTo(Route.OrgSelect.path) { inclusive = true } }
                    })
                }
                composable(Route.Dashboard.path) { DashboardScreen(onLoggedOut = onSessionEnded) }
                composable(Route.Chat.path) {
                    ChatInboxScreen(onOpenConversation = { navController.navigate(Route.ChatDetail.create(it)) })
                }
                composable(Route.Mail.path) {
                    MailInboxScreen(onOpenThread = { navController.navigate(Route.MailDetail.create(it)) })
                }
                composable(Route.Visitors.path) {
                    VisitorsScreen(onStartChat = { visitorId ->
                        navController.navigate(Route.Chat.path)
                    })
                }
                if (showPlatform) {
                    platformDestination(onViewingTenant = {
                        // Into the customer's chat, which is what "view as" is for. Landing back on
                        // the directory would look like the tap did nothing.
                        navController.navigate(Route.Chat.path) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    })
                }
                composable(
                    Route.ChatDetail.path,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    ChatDetailScreen(
                        conversationId = entry.arguments?.getString("id") ?: return@composable,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    Route.MailDetail.path,
                    arguments = listOf(navArgument("id") { type = NavType.StringType }),
                ) { entry ->
                    MailDetailScreen(threadId = entry.arguments?.getString("id") ?: return@composable)
                }
            }
        }
    }
}
