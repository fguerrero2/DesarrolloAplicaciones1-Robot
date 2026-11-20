package ar.edu.fguerrero.da1.robot_guia_universitario.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandRecord
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionEvent
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionFinalState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionRecord
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.label
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.AppViewModelProvider
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.CommandStatusPill
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.EmptyState
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.LabeledValue
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.StatusPill
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.formatDateTime
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.formatDuration
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.formatTime
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.theme.LocalStatusColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val missions: List<MissionRecord> = emptyList(),
    val expandedId: String? = null,
    val events: List<MissionEvent> = emptyList(),
    val commands: List<CommandRecord> = emptyList(),
) {
    val completed get() = missions.count { it.finalState == MissionFinalState.COMPLETED }
    val interrupted get() = missions.count { it.finalState !in setOf(MissionFinalState.COMPLETED, MissionFinalState.IN_PROGRESS) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: RobotRepository) : ViewModel() {
    private val expanded = MutableStateFlow<String?>(null)
    private val missions = repository.observeMissionHistory()

    private val detail = combine(expanded, missions) { id, list -> list.firstOrNull { it.id == id } }
        .flatMapLatest { m ->
            if (m == null) flowOf(emptyList<MissionEvent>() to emptyList())
            else combine(
                repository.observeMissionEvents(m.id),
                repository.observeCommands(m.robotId).map { c -> c.filter { it.missionId == m.id } },
            ) { e, c -> e to c }
        }

    val state: StateFlow<HistoryUiState> = combine(missions, expanded, detail) { list, id, d ->
        HistoryUiState(list, id, d.first, d.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun toggle(missionId: String) { expanded.value = if (expanded.value == missionId) null else missionId }
}

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HistoryContent(state, viewModel::toggle)
}

@Composable
private fun finalStateColor(s: MissionFinalState) = when (s) {
    MissionFinalState.COMPLETED -> LocalStatusColors.current.success
    MissionFinalState.IN_PROGRESS -> LocalStatusColors.current.info
    MissionFinalState.CANCELLED, MissionFinalState.STOPPED -> LocalStatusColors.current.danger
    MissionFinalState.RETURNED_SAFE -> LocalStatusColors.current.warning
}

@Composable
fun HistoryContent(state: HistoryUiState, onToggle: (String) -> Unit) {
    if (state.missions.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.History,
            title = "Todavía no hay misiones",
            message = "Las misiones se guardan en el dispositivo y se pueden consultar sin conexión.",
            modifier = Modifier.fillMaxSize(),
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledValue("Misiones", "${state.missions.size}", Modifier.weight(1f))
                LabeledValue("Completadas", "${state.completed}", Modifier.weight(1f))
                LabeledValue("Interrumpidas", "${state.interrupted}", Modifier.weight(1f))
            }
        }
        items(state.missions, key = { it.id }) { m ->
            val expanded = m.id == state.expandedId
            Card(onClick = { onToggle(m.id) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.destinationName, style = MaterialTheme.typography.titleMedium)
                            Text("${formatDateTime(m.startedAt)} · ${m.robotId}", style = MaterialTheme.typography.bodySmall)
                        }
                        StatusPill(m.finalState.label(), finalStateColor(m.finalState), null)
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (expanded) "Ocultar detalle" else "Ver detalle")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LabeledValue("Duración", formatDuration(m.durationSeconds), Modifier.weight(1f))
                        LabeledValue("Acompañados", "${m.companions}", Modifier.weight(1f))
                        LabeledValue("Alertas", "${m.alertCount}", Modifier.weight(1f))
                    }
                    AnimatedVisibility(expanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            HorizontalDivider()
                            Text("Comandos enviados", style = MaterialTheme.typography.titleSmall)
                            if (state.commands.isEmpty()) Text("—", style = MaterialTheme.typography.bodySmall)
                            state.commands.forEach { c ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${formatTime(c.sentAt)} ${c.type.label()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    CommandStatusPill(c.status)
                                }
                            }
                            HorizontalDivider()
                            Text("Eventos", style = MaterialTheme.typography.titleSmall)
                            state.events.forEach { e ->
                                Text("${formatTime(e.timestamp)}  ${e.message}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
