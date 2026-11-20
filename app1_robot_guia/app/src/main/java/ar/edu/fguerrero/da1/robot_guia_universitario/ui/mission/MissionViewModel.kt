package ar.edu.fguerrero.da1.robot_guia_universitario.ui.mission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Destination
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ExecuteCommandUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionEvent
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ObserveRobotStatusUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ObserveSelectedRobotUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotStatus
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SettingsRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SimScenario
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.label
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MissionUiState(
    val status: RobotStatus? = null,
    val destinations: List<Destination> = emptyList(),
    val events: List<MissionEvent> = emptyList(),
    val message: String? = null,
) {
    /** Velocidad media de guía (paso a visitante) ≈ 0.5 m/s → tiempo estimado de llegada. */
    val etaSeconds: Long? get() = status?.telemetry?.guide?.remainingDistance?.takeIf { it > 0 }?.let { (it / GUIDE_SPEED_MPS).toLong() }

    companion object { const val GUIDE_SPEED_MPS = 0.5 }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MissionViewModel(
    private val repository: RobotRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val executeCommand = ExecuteCommandUseCase(repository)
    private val observeStatus = ObserveRobotStatusUseCase(repository)
    private val message = MutableStateFlow<String?>(null)

    private val status: Flow<RobotStatus?> = ObserveSelectedRobotUseCase(repository, settings)().flatMapLatest { id ->
        if (id == null) flowOf(null) else observeStatus(id).map<RobotStatus, RobotStatus?> { it }
    }

    private val events: Flow<List<MissionEvent>> = status
        .map { it?.activeMission?.id }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeMissionEvents(id) }

    val state: StateFlow<MissionUiState> = combine(status, repository.observeDestinations(), events, message) { s, d, e, m ->
        MissionUiState(s, d, e, m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MissionUiState())

    fun execute(command: CommandType) {
        val st = state.value.status ?: return
        viewModelScope.launch {
            val result = executeCommand(command, st.robotId, st.activeMission?.id, st.available)
            message.value = result.fold(
                onSuccess = { "${command.label()}: ${it.status.label()}" },
                onFailure = { it.message },
            )
        }
    }

    fun trigger(scenario: SimScenario) {
        val robotId = state.value.status?.robotId ?: return
        viewModelScope.launch {
            message.value = repository.triggerScenario(robotId, scenario).fold(
                onSuccess = { "Escenario forzado: ${scenario.label()}" },
                onFailure = { "No se pudo forzar el escenario: ${it.message}" },
            )
        }
    }

    fun messageShown() = message.update { null }
}

fun SimScenario.label(): String = when (this) {
    SimScenario.LOW_BATTERY -> "Batería baja"
    SimScenario.DISCONNECT -> "Desconexión"
    SimScenario.REJECT_NEXT_COMMAND -> "Rechazar próximo comando"
    SimScenario.VISITOR_LAGGING -> "Visitante rezagado"
    SimScenario.DESTINATION_UNAVAILABLE -> "Destino inaccesible"
    SimScenario.ROUTE_BLOCKED -> "Ruta bloqueada"
    SimScenario.RECOVER -> "Recuperar / normalizar"
}
