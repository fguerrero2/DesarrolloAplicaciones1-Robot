package ar.edu.fguerrero.da1.robot_guia_universitario.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Casos de uso: concentran validaciones de negocio antes de delegar en el repositorio.
 */
class StartGuideMissionUseCase(private val repository: RobotRepository) {
    suspend operator fun invoke(
        request: MissionRequest,
        telemetry: RobotTelemetry?,
        hasActiveMission: Boolean,
        hasPendingCommand: Boolean,
    ): Result<CommandResponse> {
        CommandRules.startBlockedReason(telemetry, hasActiveMission, hasPendingCommand)?.let {
            return Result.failure(ValidationException(it))
        }
        if (request.companions !in 1..CommandRules.MAX_COMPANIONS) {
            return Result.failure(ValidationException("La cantidad de acompañados debe estar entre 1 y ${CommandRules.MAX_COMPANIONS}."))
        }
        return repository.startMission(request)
    }
}

/** Ejecuta un comando de control validando que esté habilitado en el estado actual. */
class ExecuteCommandUseCase(private val repository: RobotRepository) {
    suspend operator fun invoke(
        command: CommandType,
        robotId: String,
        missionId: String?,
        available: Set<CommandType>,
    ): Result<CommandResponse> {
        if (command !in available) {
            return Result.failure(ValidationException("El comando ${command.label()} no está disponible en este estado."))
        }
        val noMission = Result.failure<CommandResponse>(ValidationException("No hay misión activa."))
        return when (command) {
            CommandType.PAUSE -> missionId?.let { repository.pauseMission(robotId, it) } ?: noMission
            CommandType.RESUME -> missionId?.let { repository.resumeMission(robotId, it) } ?: noMission
            CommandType.CANCEL -> missionId?.let { repository.cancelMission(robotId, it) } ?: noMission
            CommandType.RETURN_SAFE -> repository.returnToSafePoint(robotId)
            CommandType.STOP -> repository.operationalStop(robotId)
            CommandType.START_MISSION, CommandType.ACK_ALERT ->
                Result.failure(ValidationException("Usar el caso de uso específico."))
        }
    }
}

class ValidationException(message: String) : Exception(message)

fun CommandType.label(): String = when (this) {
    CommandType.START_MISSION -> "Iniciar misión"
    CommandType.PAUSE -> "Pausar"
    CommandType.RESUME -> "Reanudar"
    CommandType.CANCEL -> "Cancelar misión"
    CommandType.RETURN_SAFE -> "Regresar a punto seguro"
    CommandType.STOP -> "Detención operativa"
    CommandType.ACK_ALERT -> "Reconocer alerta"
}

fun OperationalState.label(): String = when (this) {
    OperationalState.IDLE -> "Disponible"
    OperationalState.GUIDING -> "Guiando"
    OperationalState.PAUSED -> "En pausa"
    OperationalState.WAITING -> "Esperando (incidente)"
    OperationalState.ARRIVED -> "Llegó a destino"
    OperationalState.RETURNING -> "Regresando a punto seguro"
    OperationalState.STOPPED -> "Detenido (software)"
}

fun CommandStatus.label(): String = when (this) {
    CommandStatus.PENDING -> "Enviado · esperando"
    CommandStatus.CONFIRMED -> "Confirmado"
    CommandStatus.REJECTED -> "Rechazado"
    CommandStatus.NO_RESPONSE -> "Sin respuesta"
}

fun MissionFinalState.label(): String = when (this) {
    MissionFinalState.IN_PROGRESS -> "En curso"
    MissionFinalState.COMPLETED -> "Completada"
    MissionFinalState.CANCELLED -> "Cancelada"
    MissionFinalState.RETURNED_SAFE -> "Regresó a punto seguro"
    MissionFinalState.STOPPED -> "Detenida"
}

fun DestinationType.label(): String = when (this) {
    DestinationType.AULA -> "Aula"
    DestinationType.LABORATORIO -> "Laboratorio"
    DestinationType.AUDITORIO -> "Auditorio"
    DestinationType.OFICINA -> "Oficina"
}

/** Estado consolidado de un robot para la UI: telemetría + misión + comandos + alertas + reglas. */
data class RobotStatus(
    val robotId: String,
    val telemetry: RobotTelemetry?,
    val activeMission: MissionRecord?,
    val commands: List<CommandRecord>,
    val alerts: List<RobotAlert>,
    val now: Long,
) {
    /** Un PENDING más viejo que 30 s se considera abandonado (p. ej. la app se cerró) y no bloquea. */
    val pendingCommand: CommandRecord? =
        commands.firstOrNull { it.status == CommandStatus.PENDING && now - it.sentAt < 30_000 }
    val lastCommand: CommandRecord? = commands.firstOrNull()
    val available: Set<CommandType> =
        CommandRules.availableCommands(telemetry, pendingCommand != null, activeMission != null)
    val startBlockedReason: String? =
        CommandRules.startBlockedReason(telemetry, activeMission != null, pendingCommand != null)
    val unacknowledgedAlerts: List<RobotAlert> = alerts.filter { !it.acknowledged }
}

class ObserveRobotStatusUseCase(
    private val repository: RobotRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    operator fun invoke(robotId: String): Flow<RobotStatus> =
        combine(
            repository.observeTelemetry(robotId),
            repository.observeActiveMission(robotId),
            repository.observeCommands(robotId),
            repository.observeAlerts(robotId),
        ) { telemetry, mission, commands, alerts ->
            RobotStatus(robotId, telemetry, mission, commands, alerts, clock())
        }
}

/** Robot seleccionado: el guardado en configuración o, si no hay, el primero disponible. */
class ObserveSelectedRobotUseCase(
    private val repository: RobotRepository,
    private val settings: SettingsRepository,
) {
    operator fun invoke(): Flow<String?> =
        combine(settings.selectedRobotId, repository.observeRobots()) { saved, robots ->
            saved ?: robots.firstOrNull()?.id
        }.distinctUntilChanged()
}
