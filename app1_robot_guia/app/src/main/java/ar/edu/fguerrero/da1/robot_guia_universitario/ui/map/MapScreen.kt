package ar.edu.fguerrero.da1.robot_guia_universitario.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Destination
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ObserveRobotStatusUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ObserveSelectedRobotUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotStatus
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SettingsRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.label
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.AppViewModelProvider
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.SectionCard
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.StaleDataBanner
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.format1
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.rememberNow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MapUiState(val destinations: List<Destination> = emptyList(), val status: RobotStatus? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(repository: RobotRepository, settings: SettingsRepository) : ViewModel() {
    private val observeStatus = ObserveRobotStatusUseCase(repository)
    val state: StateFlow<MapUiState> = combine(
        repository.observeDestinations(),
        ObserveSelectedRobotUseCase(repository, settings)().flatMapLatest { id ->
            if (id == null) flowOf(null) else observeStatus(id).map<RobotStatus, RobotStatus?> { it }
        },
    ) { d, s -> MapUiState(d, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())
}

@Composable
fun MapScreen(
    onGuideTo: (String) -> Unit,
    viewModel: MapViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val telemetry = state.status?.telemetry
    val canStart = state.status?.startBlockedReason == null
    val selected = state.destinations.firstOrNull { it.id == selectedId }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StaleDataBanner(telemetry, rememberNow())
        CampusMap(
            destinations = state.destinations,
            robotPosition = telemetry?.takeIf { it.updatedAt != 0L }?.position,
            targetDestinationId = telemetry?.guide?.destinationId,
            selectedDestinationId = selectedId,
            onDestinationTap = { selectedId = it.id },
        )
        Text(
            "Tocá un destino en el mapa o en la lista para ver el detalle.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selected != null) {
            SectionCard(title = selected.name) {
                Text("${selected.type.label()} · ${selected.building} · Piso ${selected.floor}")
                Text("Coordenadas: ${format1(selected.x)}, ${format1(selected.y)} m", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { onGuideTo(selected.id) },
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.NearMe, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Guiar hasta aquí")
                }
                state.status?.startBlockedReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        SectionCard(title = "Destinos") {
            state.destinations.forEach { d ->
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp)) {
                    androidx.compose.material3.TextButton(onClick = { selectedId = d.id }) {
                        Text("${d.name} · ${d.building}")
                    }
                }
            }
        }
    }
}
