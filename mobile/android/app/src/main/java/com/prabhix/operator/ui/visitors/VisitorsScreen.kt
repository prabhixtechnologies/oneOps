package com.prabhix.operator.ui.visitors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prabhix.operator.data.api.LiveVisitor
import com.prabhix.operator.data.repository.VisitorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VisitorsViewModel @Inject constructor(
    private val visitorRepository: VisitorRepository,
) : ViewModel() {
    private val _live = MutableStateFlow<List<LiveVisitor>>(emptyList())
    val live: StateFlow<List<LiveVisitor>> = _live.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            runCatching { visitorRepository.live() }.onSuccess { _live.value = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorsScreen(
    onStartChat: (String) -> Unit,
    viewModel: VisitorsViewModel = hiltViewModel(),
) {
    val live by viewModel.live.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(topBar = { TopAppBar(title = { Text("Live visitors (${live.size})") }) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(live, key = { it.visitorId }) { visitor ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onStartChat(visitor.visitorId) }
                        .padding(16.dp),
                ) {
                    Text(visitor.displayName ?: visitor.email ?: "Anonymous", style = MaterialTheme.typography.titleMedium)
                    Text(visitor.currentTitle ?: visitor.currentPath ?: "/")
                }
            }
        }
    }
}
