package ar.edu.fguerrero.da1.robot_guia_universitario.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ExecuteCommandUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ObserveRobotStatusUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ObserveSelectedRobotUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Robot
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotStatus
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SettingsRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.label
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val robots: List<Robot> = emptyList(),
    val selectedRobotId: String? = null,
    val status: RobotStatus? = null,
    val refreshing: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: RobotRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val executeCommand = ExecuteCommandUseCase(repository)
    private val observeStatus = ObserveRobotStatusUseCase(repository)
    private val transient = MutableStateFlow(HomeUiState())

    private val selected = ObserveSelectedRobotUseCase(repository, settings)()
    private val status = selected.flatMapLatest { id -> if (id == null) flowOf(null) else observeStatus(id).map<RobotStatus, RobotStatus?> { it } }

    val state: StateFlow<HomeUiState> = combine(repository.observeRobots(), selected, status, transient) { robots, id, st, t ->
        t.copy(robots = robots, selectedRobotId = id, status = st)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectRobot(robotId: String) {
        viewModelScope.launch { settings.selectRobot(robotId) }
    }

    fun refresh() {
        viewModelScope.launch {
            transient.update { it.copy(refreshing = true) }
            val result = repository.refreshRobots()
            transient.update {
                it.copy(refreshing = false, message = result.exceptionOrNull()?.let { e -> "No se pudo actualizar la lista de robots: ${e.message}" })
            }
        }
    }

    fun execute(command: CommandType) {
        val st = state.value.status ?: return
        viewModelScope.launch {
            val result = executeCommand(command, st.robotId, st.activeMission?.id, st.available)
            val msg = result.fold(
                onSuccess = { "${command.label()}: ${it.status.label()} — ${it.message}" },
                onFailure = { it.message ?: "Error" },
            )
            transient.update { it.copy(message = msg) }
        }
    }

    fun messageShown() = transient.update { it.copy(message = null) }
}
