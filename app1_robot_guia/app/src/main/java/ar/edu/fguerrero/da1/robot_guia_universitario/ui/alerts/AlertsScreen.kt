package ar.edu.fguerrero.da1.robot_guia_universitario.ui.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.AlertSeverity
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ObserveSelectedRobotUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotAlert
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SettingsRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.label
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.AppViewModelProvider
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.EmptyState
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.LocalSnackbarHostState
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.StatusPill
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.formatDateTime
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.severityColor
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.severityIcon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlertsUiState(
    val robotId: String? = null,
    val alerts: List<RobotAlert> = emptyList(),
    val pendingOnly: Boolean = true,
    val acknowledging: Set<String> = emptySet(),
    val message: String? = null,
) {
    val visible: List<RobotAlert> get() = if (pendingOnly) alerts.filter { !it.acknowledged } else alerts
    val pendingCount: Int get() = alerts.count { !it.acknowledged }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModel(private val repository: RobotRepository, settings: SettingsRepository) : ViewModel() {
    private val local = MutableStateFlow(AlertsUiState())
    private val robotId = ObserveSelectedRobotUseCase(repository, settings)()

    val state: StateFlow<AlertsUiState> = combine(
        robotId,
        robotId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeAlerts(id) },
        local,
    ) { id, alerts, l -> l.copy(robotId = id, alerts = alerts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlertsUiState())

    fun setPendingOnly(value: Boolean) = local.update { it.copy(pendingOnly = value) }

    fun acknowledge(alertId: String) {
        val robot = state.value.robotId ?: return
        viewModelScope.launch {
            local.update { it.copy(acknowledging = it.acknowledging + alertId) }
            val result = repository.acknowledgeAlert(robot, alertId)
            local.update {
                it.copy(
                    acknowledging = it.acknowledging - alertId,
                    message = result.fold({ r -> "Reconocer alerta: ${r.status.label()}" }, { e -> e.message }),
                )
            }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }
}

@Composable
fun AlertsScreen(viewModel: AlertsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }
    AlertsContent(state, viewModel::setPendingOnly, viewModel::acknowledge)
}

private fun AlertSeverity.text() = when (this) {
    AlertSeverity.INFO -> "Info"
    AlertSeverity.WARNING -> "Advertencia"
    AlertSeverity.CRITICAL -> "Crítica"
}

@Composable
fun AlertsContent(state: AlertsUiState, onPendingOnly: (Boolean) -> Unit, onAcknowledge: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.pendingOnly, onClick = { onPendingOnly(true) }, label = { Text("Pendientes (${state.pendingCount})") })
            FilterChip(selected = !state.pendingOnly, onClick = { onPendingOnly(false) }, label = { Text("Todas (${state.alerts.size})") })
        }
        if (state.visible.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.NotificationsNone,
                title = if (state.pendingOnly) "Sin alertas pendientes" else "Sin alertas",
                message = "Cuando el robot reporte un incidente aparecerá aquí.",
                modifier = Modifier.fillMaxSize(),
            )
            return
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.visible, key = { it.id }) { alert ->
                val c = severityColor(alert.severity)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (alert.acknowledged) MaterialTheme.colorScheme.surfaceContainer else c.copy(alpha = 0.10f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusPill(alert.severity.text(), c, severityIcon(alert.severity))
                            Spacer(Modifier.width(8.dp))
                            Text(formatDateTime(alert.timestamp), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        }
                        Text(alert.message, style = MaterialTheme.typography.bodyLarge)
                        if (alert.acknowledged) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.width(4.dp))
                                Text("Reconocida", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { onAcknowledge(alert.id) },
                                enabled = alert.id !in state.acknowledging,
                            ) { Text(if (alert.id in state.acknowledging) "Enviando…" else "Reconocer") }
                        }
                    }
                }
            }
        }
    }
}
