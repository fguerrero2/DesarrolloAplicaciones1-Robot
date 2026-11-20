package ar.edu.fguerrero.da1.robot_guia_universitario.data

import ar.edu.fguerrero.da1.robot_guia_universitario.domain.AlertSeverity
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.AlertType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandResponse
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandStatus
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ConnectionState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Destination
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.HealthResult
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionFinalState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionRequest
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.OperationalState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SettingsRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SimScenario
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ValidationException
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Repositorio offline-first:
 *  - La UI observa SIEMPRE Room (fuente de verdad).
 *  - Mientras alguien observa la telemetría, una corrutina sondea al robot cada [pollIntervalMs]
 *    y escribe el resultado en Room.
 *  - Si se pierde la conexión, se conserva el último dato y se marca DISCONNECTED.
 *  - Cada comando se persiste como PENDING antes de enviarse y luego se actualiza a
 *    CONFIRMED / REJECTED / NO_RESPONSE.
 */
class OfflineFirstRobotRepository(
    private val dao: RobotDao,
    private val settings: SettingsRepository,
    private val remoteProvider: RemoteDataSourceProvider,
    private val scope: CoroutineScope,
    private val destinations: List<Destination> = CampusCatalog.destinations,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pollIntervalMs: Long = 1_000,
) : RobotRepository {

    private val pollers = ConcurrentHashMap<String, SharedFlow<Unit>>()

    // ---------- Lecturas (siempre desde Room) ----------

    override fun observeRobots() = dao.observeRobots().map { list -> list.map { it.toDomain() } }
    override fun observeDestinations(): Flow<List<Destination>> = flowOf(destinations)

    override fun observeTelemetry(robotId: String): Flow<ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotTelemetry?> = channelFlow {
        launch { poller(robotId).collect { } }
        dao.observeTelemetry(robotId).collect { send(it?.toDomain()) }
    }

    override fun observeAlerts(robotId: String) = dao.observeAlerts(robotId).map { l -> l.map { it.toDomain() } }
    override fun observeActiveMission(robotId: String) = dao.observeActiveMission(robotId).map { it?.toDomain() }
    override fun observeMissionHistory() = dao.observeHistory().map { l -> l.map { it.mission.toDomain(it.alertCount) } }
    override fun observeMissionEvents(missionId: String) = dao.observeEvents(missionId).map { l -> l.map { it.toDomain() } }
    override fun observeCommands(robotId: String) = dao.observeCommands(robotId).map { l -> l.map { it.toDomain() } }

    // ---------- Sincronización ----------

    /** Un único sondeo por robot, compartido por todos los observadores; se detiene sin suscriptores. */
    private fun poller(robotId: String): SharedFlow<Unit> = pollers.getOrPut(robotId) {
        flow<Unit> {
            while (true) {
                syncTelemetry(robotId)
                delay(pollIntervalMs)
            }
        }.shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)
    }

    private suspend fun <T> callRemote(block: suspend RobotRemoteDataSource.() -> T): Result<T> {
        val config = settings.config.first()
        val remote = remoteProvider.get(config)
        return try {
            Result.success(withTimeout(config.timeoutSeconds * 1_000L) { remote.block() })
        } catch (e: TimeoutCancellationException) {
            Result.failure(NoResponseException("Sin respuesta en ${config.timeoutSeconds} s"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncTelemetry(robotId: String) {
        val previous = dao.getTelemetry(robotId)
        callRemote { telemetry(robotId) }
            .onSuccess { dto ->
                val now = clock()
                dao.upsertTelemetry(dto.toEntity(now))
                val active = dao.getActiveMission(robotId)
                if (previous?.connection == ConnectionState.DISCONNECTED.name) {
                    active?.let { event(it.id, "Conexión restablecida") }
                }
                dto.alerts.forEach { alert ->
                    val existing = dao.getAlert(alert.id)
                    dao.upsertAlert(
                        alert.toEntity(
                            robotId = robotId,
                            missionId = existing?.missionId ?: active?.id,
                            // Las alertas informativas no requieren acción del operador (Nielsen #8).
                            acknowledged = alert.acknowledged || existing?.acknowledged == true || alert.severity == AlertSeverity.INFO.name,
                            receivedAt = now,
                        ),
                    )
                    if (existing == null && active != null) event(active.id, "Alerta: ${alert.message}")
                }
                if (active != null && dto.missionId == active.id && dto.operationalState == OperationalState.ARRIVED.name) {
                    dao.finishMission(active.id, MissionFinalState.COMPLETED.name, now)
                    event(active.id, "Llegó a destino: misión completada")
                }
            }
            .onFailure {
                val now = clock()
                if (previous == null) {
                    // Nunca recibimos datos: guardamos un registro "vacío" para poder mostrar el estado de conexión.
                    dao.upsertTelemetry(emptyTelemetry(robotId))
                } else if (previous.connection != ConnectionState.DISCONNECTED.name) {
                    dao.updateConnection(robotId, ConnectionState.DISCONNECTED.name)
                    val active = dao.getActiveMission(robotId)
                    localAlert(robotId, active?.id, AlertType.DISCONNECTED, AlertSeverity.CRITICAL,
                        "Se perdió la conexión con el robot. Se muestran los últimos datos conocidos.", now)
                    active?.let { m -> event(m.id, "Conexión perdida: ${it.message}") }
                }
            }
    }

    private fun emptyTelemetry(robotId: String) = TelemetryEntity(
        robotId, ConnectionState.DISCONNECTED.name, 0, OperationalState.IDLE.name, null, 0,
        0.0, 0.0, 0.0, 0.0, 0.0, 0.0, null, null, 0.0, 0, updatedAt = 0,
    )

    override suspend fun refreshRobots(): Result<Unit> =
        callRemote { robots() }.map { list -> dao.upsertRobots(list.map { it.toEntity() }) }

    override suspend fun checkHealth(): HealthResult {
        val start = clock()
        val result = callRemote { health() }
        val end = clock()
        return result.fold(
            onSuccess = { HealthResult(true, "${it.status} · ${it.service}", end - start, end) },
            onFailure = { HealthResult(false, it.message ?: it::class.simpleName.orEmpty(), end - start, end) },
        )
    }

    // ---------- Comandos ----------

    private suspend fun sendCommand(
        robotId: String,
        missionId: String?,
        type: CommandType,
        call: suspend RobotRemoteDataSource.() -> CommandResponseDto,
        onConfirmed: suspend (CommandResponse) -> Unit = {},
    ): Result<CommandResponse> {
        val localId = "cmd-local-" + UUID.randomUUID().toString().take(8)
        dao.upsertCommand(CommandEntity(localId, robotId, missionId, type.name, CommandStatus.PENDING.name, "Enviado", clock(), null))
        missionId?.let { event(it, "${type.label()}: enviado") }

        val response = callRemote(call).fold(
            onSuccess = { it.toDomain(clock()) },
            onFailure = { e ->
                val status = if (e is RemoteRejectedException) CommandStatus.REJECTED else CommandStatus.NO_RESPONSE
                CommandResponse(localId, status, e.message ?: "Sin respuesta del robot", clock())
            },
        )
        val effectiveMission = response.missionId ?: missionId
        dao.updateCommand(localId, response.status.name, response.message, clock(), effectiveMission)

        when (response.status) {
            CommandStatus.CONFIRMED -> onConfirmed(response)
            CommandStatus.REJECTED -> localAlert(robotId, effectiveMission, AlertType.COMMAND_REJECTED, AlertSeverity.WARNING,
                "${type.label()} rechazado: ${response.message}", clock())
            CommandStatus.NO_RESPONSE -> localAlert(robotId, effectiveMission, AlertType.DISCONNECTED, AlertSeverity.WARNING,
                "${type.label()} sin respuesta: ${response.message}", clock())
            CommandStatus.PENDING -> Unit
        }
        effectiveMission?.let { event(it, "${type.label()}: ${response.status.label()} — ${response.message}") }
        if (response.status == CommandStatus.CONFIRMED) syncTelemetry(robotId) // feedback inmediato
        return Result.success(response)
    }

    override suspend fun startMission(request: MissionRequest): Result<CommandResponse> {
        val destination = destinations.firstOrNull { it.id == request.destinationId }
            ?: return Result.failure(ValidationException("Destino inexistente"))
        if (dao.getActiveMission(request.robotId) != null) {
            return Result.failure(ValidationException("Ya hay una misión en curso para este robot"))
        }
        val dto = GuideMissionRequestDto(request.robotId, destination.id, destination.name, destination.x, destination.y, request.companions)
        return sendCommand(request.robotId, null, CommandType.START_MISSION, { startGuide(dto) }) { response ->
            val missionId = response.missionId ?: ("mis-local-" + UUID.randomUUID().toString().take(8))
            dao.insertMission(MissionEntity(missionId, request.robotId, destination.id, destination.name, request.companions, clock(), null, MissionFinalState.IN_PROGRESS.name))
            event(missionId, "Misión creada hacia ${destination.name} con ${request.companions} acompañado(s)")
        }
    }

    override suspend fun pauseMission(robotId: String, missionId: String) =
        sendCommand(robotId, missionId, CommandType.PAUSE, { pause(missionId) })

    override suspend fun resumeMission(robotId: String, missionId: String) =
        sendCommand(robotId, missionId, CommandType.RESUME, { resume(missionId) })

    override suspend fun cancelMission(robotId: String, missionId: String) =
        sendCommand(robotId, missionId, CommandType.CANCEL, { cancel(missionId) }) {
            dao.finishMission(missionId, MissionFinalState.CANCELLED.name, clock())
        }

    override suspend fun returnToSafePoint(robotId: String): Result<CommandResponse> {
        val active = dao.getActiveMission(robotId)
        return sendCommand(robotId, active?.id, CommandType.RETURN_SAFE, { returnSafe(robotId) }) {
            active?.let { dao.finishMission(it.id, MissionFinalState.RETURNED_SAFE.name, clock()) }
        }
    }

    override suspend fun operationalStop(robotId: String): Result<CommandResponse> {
        val active = dao.getActiveMission(robotId)
        return sendCommand(robotId, active?.id, CommandType.STOP, { stop(robotId) })
    }

    override suspend fun acknowledgeAlert(robotId: String, alertId: String): Result<CommandResponse> {
        if (alertId.startsWith(LOCAL_ALERT_PREFIX)) {
            // Alertas generadas por la app (p. ej. desconexión): se reconocen localmente, sin red.
            dao.acknowledgeAlert(alertId)
            return Result.success(CommandResponse(alertId, CommandStatus.CONFIRMED, "Alerta local reconocida", clock()))
        }
        val missionId = dao.getAlert(alertId)?.missionId
        return sendCommand(robotId, missionId, CommandType.ACK_ALERT, { ackAlert(alertId) }) {
            dao.acknowledgeAlert(alertId)
        }
    }

    override suspend fun triggerScenario(robotId: String, scenario: SimScenario): Result<Unit> =
        callRemote { scenario(robotId, scenario.name) }

    // ---------- Helpers ----------

    private suspend fun event(missionId: String, message: String) =
        dao.insertEvent(MissionEventEntity(missionId = missionId, timestamp = clock(), message = message))

    private suspend fun localAlert(robotId: String, missionId: String?, type: AlertType, severity: AlertSeverity, message: String, now: Long) =
        dao.upsertAlert(AlertEntity("$LOCAL_ALERT_PREFIX${UUID.randomUUID().toString().take(8)}", robotId, missionId, type.name, severity.name, message, now, false))

    companion object { const val LOCAL_ALERT_PREFIX = "local-" }
}
