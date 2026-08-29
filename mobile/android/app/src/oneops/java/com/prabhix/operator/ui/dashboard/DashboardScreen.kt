package com.prabhix.operator.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhix.operator.data.api.DashboardResponse
import com.prabhix.operator.data.repository.AuthRepository
import com.prabhix.operator.data.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _dashboard = MutableStateFlow<DashboardResponse?>(null)
    val dashboard: StateFlow<DashboardResponse?> = _dashboard.asStateFlow()

    val displayName: String? get() = authRepository.session()?.displayName

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            runCatching { dashboardRepository.load() }.onSuccess { _dashboard.value = it }
        }
    }

    /**
     * Signs out here, then hands the caller Identity's end-session URL.
     *
     * <p>Clearing the local tokens alone leaves the browser signed in, so the next "Continue" comes
     * straight back with a new session and no prompt — which looks like the sign-out did nothing.
     */
    fun logout(onLoggedOut: (android.net.Uri) -> Unit) {
        viewModelScope.launch {
            val endSession = authRepository.logout()
            onLoggedOut(endSession)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLoggedOut: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val dashboard by viewModel.dashboard.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refresh() }

    val signOut = {
        viewModel.logout { endSession ->
            // Failing to open a tab must not strand the user on a screen with no session. The local
            // tokens are already gone by this point, so navigating away is correct either way.
            runCatching { CustomTabsIntent.Builder().build().launchUrl(context, endSession) }
            onLoggedOut()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dashboard")
                        viewModel.displayName?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = signOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            dashboard?.kpis?.let { k ->
                KpiCard("Open threads", "${k.openThreads}")
                KpiCard("Avg first response", "${"%.0f".format(k.avgFirstResponseMinutes)} min")
                KpiCard("SLA breaches", "${k.slaBreaches}")
                KpiCard("Seats", "${k.seatsUsed} / ${k.seatsLimit}")
            } ?: Text("Loading…")
            dashboard?.recentActivity?.take(5)?.forEach { item ->
                Text("• ${item.description}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String) {
    Card(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
