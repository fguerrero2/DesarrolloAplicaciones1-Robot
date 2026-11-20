package ar.edu.fguerrero.da1.robot_guia_universitario.ui.destination

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandRules
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandStatus
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Destination
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.DestinationType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionRequest
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ObserveRobotStatusUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ObserveSelectedRobotUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotStatus
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SettingsRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.StartGuideMissionUseCase
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
import java.text.Normalizer
import kotlin.math.hypot

data class DestinationItem(val destination: Destination, val distanceMeters: Double?)

data class DestinationForm(
    val query: String = "",
    val typeFilter: DestinationType? = null,
    val selectedId: String? = null,
    val companions: Int = 1,
    val starting: Boolean = false,
    val error: String? = null,
    val missionStarted: Boolean = false,
)

data class DestinationUiState(
    val form: DestinationForm = DestinationForm(),
    val items: List<DestinationItem> = emptyList(),
    val status: RobotStatus? = null,
) {
    val selected: DestinationItem? get() = items.firstOrNull { it.destination.id == form.selectedId }
    val canStart: Boolean get() = selected != null && !form.starting && status?.startBlockedReason == null
}

@OptIn(ExperimentalCoroutinesApi::class)
class DestinationViewModel(
    private val repository: RobotRepository,
    settings: SettingsRepository,
) : ViewModel() {
    private val startMission = StartGuideMissionUseCase(repository)
    private val observeStatus = ObserveRobotStatusUseCase(repository)
    private val form = MutableStateFlow(DestinationForm())
    private val status = ObserveSelectedRobotUseCase(repository, settings)().flatMapLatest { id ->
        if (id == null) flowOf(null) else observeStatus(id).map<RobotStatus, RobotStatus?> { it }
    }

    val state: StateFlow<DestinationUiState> = combine(form, repository.observeDestinations(), status) { f, all, st ->
        val pos = st?.telemetry?.takeIf { it.updatedAt != 0L }?.position
        val items = all
            .filter { f.typeFilter == null || it.type == f.typeFilter }
            .filter { matches(it, f.query) }
            .map { DestinationItem(it, pos?.let { p -> hypot(it.x - p.x, it.y - p.y) }) }
        DestinationUiState(f, items, st)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DestinationUiState())

    /** Búsqueda tolerante: ignora mayúsculas y tildes ("robotica" encuentra "Robótica"). */
    private fun matches(d: Destination, query: String): Boolean {
        if (query.isBlank()) return true
        val q = normalize(query)
        return listOf(d.name, d.building, d.type.label()).any { normalize(it).contains(q) }
    }

    private fun normalize(s: String) =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "").lowercase().trim()

    fun onQueryChange(q: String) = form.update { it.copy(query = q, error = null) }
    fun onTypeFilter(type: DestinationType?) = form.update { it.copy(typeFilter = type) }
    fun select(id: String) = form.update { it.copy(selectedId = id, error = null) }
    fun preselect(id: String?) { if (id != null && form.value.selectedId == null) select(id) }
    fun incrementCompanions() = form.update { it.copy(companions = (it.companions + 1).coerceAtMost(CommandRules.MAX_COMPANIONS)) }
    fun decrementCompanions() = form.update { it.copy(companions = (it.companions - 1).coerceAtLeast(1)) }

    fun start() {
        val st = state.value
        val dest = st.selected?.destination ?: run { form.update { it.copy(error = "Elegí un destino.") }; return }
        val robotId = st.status?.robotId ?: run { form.update { it.copy(error = "No hay robot seleccionado.") }; return }
        viewModelScope.launch {
            form.update { it.copy(starting = true, error = null) }
            val result = startMission(
                MissionRequest(robotId, dest.id, st.form.companions),
                st.status.telemetry, st.status.activeMission != null, st.status.pendingCommand != null,
            )
            form.update { f ->
                result.fold(
                    onSuccess = { r ->
                        if (r.status == CommandStatus.CONFIRMED) f.copy(starting = false, missionStarted = true)
                        else f.copy(starting = false, error = "${r.status.label()}: ${r.message}")
                    },
                    onFailure = { e -> f.copy(starting = false, error = e.message) },
                )
            }
        }
    }

    fun navigationHandled() = form.update { it.copy(missionStarted = false) }
}
