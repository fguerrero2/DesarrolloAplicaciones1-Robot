package ar.edu.fguerrero.da1.robot_guia_universitario

import ar.edu.fguerrero.da1.robot_guia_universitario.data.CampusCatalog
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.AlertSeverity
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.AlertType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendConfig
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandRecord
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandResponse
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandStatus
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ConnectionState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Destination
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.GuideInfo
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.HealthResult
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionEvent
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionFinalState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionRecord
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionRequest
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.OperationalState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Position
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Robot
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotAlert
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotTelemetry
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SettingsRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SimScenario
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Velocity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/*
 * Dobles de prueba compartidos por tests unitarios (src/test) e instrumentados (src/androidTest).
 * Mantienen todo en memoria, permiten fijar el estado a mano y registran las llamadas recibidas.
 */

const val TEST_ROBOT = "g1-01"

fun sampleTelemetry(
    state: OperationalState = OperationalState.IDLE,
    battery: Int = 80,
    connection: ConnectionState = ConnectionState.CONNECTED,
    missionId: String? = null,
    progress: Int = 0,
    position: Position = Position(5.0, 5.0, 0.0),
    updatedAt: Long = System.currentTimeMillis(),
    destinationId: String? = null,
) = RobotTelemetry(
    robotId = TEST_ROBOT, connection = connection, battery = battery, operationalState = state,
    missionId = missionId, missionProgress = progress, position = position,
    velocity = Velocity(0.0, 0.0, 0.0),
    guide = GuideInfo(destinationId, destinationId?.let { "Destino $it" }, 10.0, if (missionId != null) 2 else 0),
    updatedAt = updatedAt,
)

fun sampleMission(id: String = "mis-1", state: MissionFinalState = MissionFinalState.IN_PROGRESS) =
    MissionRecord(id, TEST_ROBOT, "aula-101", "Aula 101", 2, 1_000L, if (state == MissionFinalState.IN_PROGRESS) null else 61_000L, state, 0)

class MockRobotRepository(
    robots: List<Robot> = listOf(Robot(TEST_ROBOT, "Unitree G1 · Test", "Hall")),
    telemetry: RobotTelemetry? = sampleTelemetry(),
    private val destinations: List<Destination> = CampusCatalog.destinations,
) : RobotRepository {
    val robots = MutableStateFlow(robots)
    val telemetry = MutableStateFlow(telemetry)
    val alerts = MutableStateFlow<List<RobotAlert>>(emptyList())
    val activeMission = MutableStateFlow<MissionRecord?>(null)
    val history = MutableStateFlow<List<MissionRecord>>(emptyList())
    val events = MutableStateFlow<List<MissionEvent>>(emptyList())
    val commands = MutableStateFlow<List<CommandRecord>>(emptyList())

    /** Estado con el que responderá el próximo comando. */
    var nextStatus: CommandStatus = CommandStatus.CONFIRMED
    var health = HealthResult(true, "ok · mock", 5, 0)
    var refreshResult: Result<Unit> = Result.success(Unit)

    /** Registro de comandos recibidos, en orden. */
    val calls = mutableListOf<CommandType>()
    val scenarios = mutableListOf<SimScenario>()
    var healthChecks = 0

    override fun observeRobots(): Flow<List<Robot>> = this.robots
    override fun observeDestinations(): Flow<List<Destination>> = flowOf(destinations)
    override fun observeTelemetry(robotId: String): Flow<RobotTelemetry?> = telemetry
    override fun observeAlerts(robotId: String): Flow<List<RobotAlert>> = alerts
    override fun observeActiveMission(robotId: String): Flow<MissionRecord?> = activeMission
    override fun observeMissionHistory(): Flow<List<MissionRecord>> = history
    override fun observeMissionEvents(missionId: String): Flow<List<MissionEvent>> = events.map { l -> l.filter { it.missionId == missionId } }
    override fun observeCommands(robotId: String): Flow<List<CommandRecord>> = commands

    override suspend fun refreshRobots() = refreshResult
    override suspend fun checkHealth(): HealthResult { healthChecks++; return health }

    private fun respond(type: CommandType, missionId: String?, onConfirmed: () -> Unit = {}): Result<CommandResponse> {
        calls += type
        val response = CommandResponse("cmd-${calls.size}", nextStatus, "mock ${nextStatus.name}", System.currentTimeMillis(), missionId)
        commands.update { listOf(CommandRecord(response.commandId, TEST_ROBOT, missionId, type, nextStatus, response.message, response.timestamp, response.timestamp)) + it }
        if (nextStatus == CommandStatus.CONFIRMED) onConfirmed()
        return Result.success(response)
    }

    private fun setState(state: OperationalState) = telemetry.update { it?.copy(operationalState = state) }

    override suspend fun startMission(request: MissionRequest) = respond(CommandType.START_MISSION, "mis-${calls.size + 1}") {
        val dest = destinations.first { it.id == request.destinationId }
        val id = "mis-${calls.size}"
        activeMission.value = MissionRecord(id, request.robotId, dest.id, dest.name, request.companions, System.currentTimeMillis(), null, MissionFinalState.IN_PROGRESS, 0)
        telemetry.update { it?.copy(operationalState = OperationalState.GUIDING, missionId = id) }
    }

    override suspend fun pauseMission(robotId: String, missionId: String) = respond(CommandType.PAUSE, missionId) { setState(OperationalState.PAUSED) }
    override suspend fun resumeMission(robotId: String, missionId: String) = respond(CommandType.RESUME, missionId) { setState(OperationalState.GUIDING) }

    override suspend fun cancelMission(robotId: String, missionId: String) = respond(CommandType.CANCEL, missionId) {
        activeMission.value?.let { m -> history.update { listOf(m.copy(finalState = MissionFinalState.CANCELLED, endedAt = System.currentTimeMillis())) + it } }
        activeMission.value = null
        telemetry.update { it?.copy(operationalState = OperationalState.IDLE, missionId = null) }
    }

    override suspend fun returnToSafePoint(robotId: String) = respond(CommandType.RETURN_SAFE, activeMission.value?.id) {
        activeMission.value = null
        setState(OperationalState.RETURNING)
    }

    override suspend fun operationalStop(robotId: String) = respond(CommandType.STOP, activeMission.value?.id) { setState(OperationalState.STOPPED) }

    override suspend fun acknowledgeAlert(robotId: String, alertId: String) = respond(CommandType.ACK_ALERT, null) {
        alerts.update { l -> l.map { if (it.id == alertId) it.copy(acknowledged = true) else it } }
    }

    override suspend fun triggerScenario(robotId: String, scenario: SimScenario): Result<Unit> {
        scenarios += scenario
        return Result.success(Unit)
    }

    fun addAlert(id: String, type: AlertType = AlertType.ROUTE_BLOCKED, severity: AlertSeverity = AlertSeverity.CRITICAL, message: String = "Ruta bloqueada") =
        alerts.update { it + RobotAlert(id, TEST_ROBOT, type, severity, message, System.currentTimeMillis(), false) }
}

class MockSettingsRepository(
    initial: BackendConfig = BackendConfig(),
    selected: String? = TEST_ROBOT,
) : SettingsRepository {
    private val configFlow = MutableStateFlow(initial)
    private val selectedFlow = MutableStateFlow(selected)
    override val config: Flow<BackendConfig> = configFlow
    override val selectedRobotId: Flow<String?> = selectedFlow
    val current: BackendConfig get() = configFlow.value
    override suspend fun saveConfig(config: BackendConfig) { configFlow.value = config }
    override suspend fun restoreDefaults() { configFlow.value = BackendConfig() }
    override suspend fun selectRobot(robotId: String) { selectedFlow.value = robotId }
}
