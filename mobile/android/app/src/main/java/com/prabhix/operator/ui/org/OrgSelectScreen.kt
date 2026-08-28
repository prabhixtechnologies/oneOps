package com.prabhix.operator.ui.org

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.prabhix.operator.data.api.OrganizationView
import com.prabhix.operator.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OrgSelectViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    suspend fun loadOrgs() = authRepository.organizations()
    suspend fun select(orgId: String) = authRepository.selectOrganization(orgId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgSelectScreen(onSelected: () -> Unit, viewModel: OrgSelectViewModel = hiltViewModel()) {
    var orgs by remember { mutableStateOf<List<OrganizationView>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { viewModel.loadOrgs() }
            .onSuccess { list ->
                orgs = list
                if (list.size == 1) {
                    viewModel.select(list.first().id).onSuccess { onSelected() }
                }
            }
            .onFailure { error = it.message }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Choose organization") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            LazyColumn {
                items(orgs, key = { it.id }) { org ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    viewModel.select(org.id).onSuccess { onSelected() }
                                        .onFailure { error = it.message }
                                }
                            }
                            .padding(16.dp),
                    ) {
                        Text(org.name, style = MaterialTheme.typography.titleMedium)
                        Text(org.slug, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
