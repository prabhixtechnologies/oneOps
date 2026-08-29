package com.prabhix.operator.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.prabhix.operator.data.repository.PlatformRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ViewingOrgBannerViewModel @Inject constructor(
    private val platformRepository: PlatformRepository,
) : ViewModel() {
    val viewing = platformRepository.viewing
    fun stop() = platformRepository.stopViewing()
}

/**
 * Says whose data is on screen while staff are inside a customer's organization.
 *
 * <p>Sits above the nav host so it is present on every screen, not only the one that started it.
 * Every screen in this app looks the same whichever tenant is loaded — the same chat list, the same
 * inbox — so without something permanent and unmissable it is easy to answer a customer's
 * conversation believing it is your own company's.
 */
@Composable
fun ViewingOrgBanner(viewModel: ViewingOrgBannerViewModel = hiltViewModel()) {
    val viewing by viewModel.viewing.collectAsState()
    val org = viewing ?: return

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Viewing ${org.name} · recorded",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { viewModel.stop() }) { Text("Leave") }
    }
}
