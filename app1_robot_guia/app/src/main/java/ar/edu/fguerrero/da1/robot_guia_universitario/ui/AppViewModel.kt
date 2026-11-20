package ar.edu.fguerrero.da1.robot_guia_universitario.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ObserveRobotStatusUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ObserveSelectedRobotUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotTelemetry
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val robotName: String? = null,
    val telemetry: RobotTelemetry? = null,
    val unacknowledgedAlerts: Int = 0,
    val hasActiveMission: Boolean = false,
)

/** Estado global (barra de estado persistente y badge de alertas). */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val repository: RobotRepository,
    settings: SettingsRepository,
) : ViewModel() {

    init {
        viewModelScope.launch { repository.refreshRobots() }
    }

    val state: StateFlow<AppUiState> = ObserveSelectedRobotUseCase(repository, settings)()
        .flatMapLatest { robotId ->
            if (robotId == null) flowOf(AppUiState())
            else combine(ObserveRobotStatusUseCase(repository)(robotId), repository.observeRobots()) { status, robots ->
                AppUiState(
                    robotName = robots.firstOrNull { it.id == robotId }?.name ?: robotId,
                    telemetry = status.telemetry,
                    unacknowledgedAlerts = status.unacknowledgedAlerts.size,
                    hasActiveMission = status.activeMission != null,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())
}
