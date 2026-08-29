package com.prabhix.operator.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
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
import com.prabhix.operator.ui.visitors.VisitorsScreen

/**
 * Navigation for the OneOps app — the product, for a customer's own organization.
 *
 * <p>There is one nav host per flavor rather than one shared host with flavor-specific branches, and
 * the screens themselves live in `src/oneops/` alongside this file. That is what makes the admin APK
 * free of the product rather than merely uninterested in it: a shared host referencing these screens
 * would compile them into both apps and leave R8 to prove they are unreachable, which it cannot do
 * once Hilt has a provider for anything they depend on.
 *
 * @see com.prabhix.operator.ui.navigation.PrabhixNavHost in `src/admin/` for the staff app
 */
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
}

@Composable
fun PrabhixNavHost(
    isLoggedIn: Boolean,
    hasOrg: Boolean,
    @Suppress("UNUSED_PARAMETER") isPlatformAdmin: Boolean,
    deepLinkChatId: String?,
    deepLinkMailId: String?,
    onSessionEnded: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    val start = when {
        !isLoggedIn -> Route.Login.path
        !hasOrg -> Route.OrgSelect.path
        deepLinkChatId != null -> Route.ChatDetail.create(deepLinkChatId)
        deepLinkMailId != null -> Route.MailDetail.create(deepLinkMailId)
        else -> Route.Chat.path
    }

    val tabs = listOf(
        Triple(Route.Dashboard.path, "Home", Icons.Default.Home),
        Triple(Route.Chat.path, "Chat", Icons.AutoMirrored.Filled.List),
        Triple(Route.Mail.path, "Mail", Icons.Default.Email),
        Triple(Route.Visitors.path, "Live", Icons.Default.Group),
    )

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
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.padding(padding),
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
                VisitorsScreen(onStartChat = { navController.navigate(Route.Chat.path) })
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
