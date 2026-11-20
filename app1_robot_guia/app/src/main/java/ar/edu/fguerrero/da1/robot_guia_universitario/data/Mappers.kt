package ar.edu.fguerrero.da1.robot_guia_universitario.data

import ar.edu.fguerrero.da1.robot_guia_universitario.domain.AlertSeverity
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.AlertType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandRecord
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandResponse
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandStatus
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ConnectionState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.GuideInfo
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionEvent
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionFinalState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionRecord
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.OperationalState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Position
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Robot
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotAlert
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotTelemetry
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Velocity
import java.time.Instant

/* Conversiones DTO (remoto) → Entity (local) → Modelo de dominio. */

/** Enum seguro: si el backend envía un valor desconocido no rompe la app. */
inline fun <reified T : Enum<T>> safeEnum(value: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: default

fun parseInstant(value: String?, fallback: Long): Long =
    runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(fallback)

// DTO → Entity
fun RobotDto.toEntity() = RobotEntity(robotId, name, location)

fun RobotTelemetryDto.toEntity(receivedAt: Long) = TelemetryEntity(
    robotId = robotId,
    connection = safeEnum(connection, ConnectionState.UNKNOWN).name,
    battery = battery.coerceIn(0, 100),
    operationalState = safeEnum(operationalState, OperationalState.IDLE).name,
    missionId = missionId,
    missionProgress = missionProgress.coerceIn(0, 100),
    x = position.x, y = position.y, yaw = position.yaw,
    vx = velocity.vx, vy = velocity.vy, omega = velocity.omega,
    destinationId = destinationId, destinationName = destinationName,
    remainingDistance = remainingDistance, companions = companions,
    updatedAt = parseInstant(updatedAt, receivedAt),
)

fun AlertDto.toEntity(robotId: String, missionId: String?, acknowledged: Boolean, receivedAt: Long) = AlertEntity(
    id = id, robotId = robotId, missionId = missionId,
    type = safeEnum(type, AlertType.INFO).name,
    severity = safeEnum(severity, AlertSeverity.INFO).name,
    message = message, timestamp = parseInstant(timestamp, receivedAt), acknowledged = acknowledged,
)

fun CommandResponseDto.toDomain(receivedAt: Long) = CommandResponse(
    commandId = commandId,
    status = safeEnum(status, CommandStatus.NO_RESPONSE),
    message = message,
    timestamp = parseInstant(timestamp, receivedAt),
    missionId = missionId,
)

// Entity → Dominio
fun RobotEntity.toDomain() = Robot(id, name, location)

fun TelemetryEntity.toDomain() = RobotTelemetry(
    robotId = robotId,
    connection = safeEnum(connection, ConnectionState.UNKNOWN),
    battery = battery,
    operationalState = safeEnum(operationalState, OperationalState.IDLE),
    missionId = missionId,
    missionProgress = missionProgress,
    position = Position(x, y, yaw),
    velocity = Velocity(vx, vy, omega),
    guide = GuideInfo(destinationId, destinationName, remainingDistance, companions),
    updatedAt = updatedAt,
)

fun AlertEntity.toDomain() = RobotAlert(
    id, robotId, safeEnum(type, AlertType.INFO), safeEnum(severity, AlertSeverity.INFO), message, timestamp, acknowledged,
)

fun MissionEntity.toDomain(alertCount: Int = 0) = MissionRecord(
    id, robotId, destinationId, destinationName, companions, startedAt, endedAt,
    safeEnum(finalState, MissionFinalState.IN_PROGRESS), alertCount,
)

fun MissionEventEntity.toDomain() = MissionEvent(missionId, timestamp, message)

fun CommandEntity.toDomain() = CommandRecord(
    id, robotId, missionId, safeEnum(type, CommandType.STOP), safeEnum(status, CommandStatus.PENDING), message, sentAt, respondedAt,
)
