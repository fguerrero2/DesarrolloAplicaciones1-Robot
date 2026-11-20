package ar.edu.fguerrero.da1.robot_guia_universitario.data

import kotlinx.serialization.Serializable

/*
 * Fuente de datos remota: contrato común para el backend REST real y el simulador
 * embebido. Trabaja con DTOs (forma del JSON); los mappers los convierten a dominio.
 */
interface RobotRemoteDataSource {
    suspend fun health(): HealthDto
    suspend fun robots(): List<RobotDto>
    suspend fun telemetry(robotId: String): RobotTelemetryDto
    suspend fun startGuide(request: GuideMissionRequestDto): CommandResponseDto
    suspend fun pause(missionId: String): CommandResponseDto
    suspend fun resume(missionId: String): CommandResponseDto
    suspend fun cancel(missionId: String): CommandResponseDto
    suspend fun returnSafe(robotId: String): CommandResponseDto
    suspend fun stop(robotId: String): CommandResponseDto
    suspend fun ackAlert(alertId: String): CommandResponseDto
    suspend fun scenario(robotId: String, scenario: String)
}

/** El backend respondió, pero rechazó la orden (HTTP 4xx/5xx). */
class RemoteRejectedException(message: String) : Exception(message)

/** El backend no respondió a tiempo. */
class NoResponseException(message: String) : Exception(message)

@Serializable
data class HealthDto(val status: String, val service: String = "")

@Serializable
data class RobotDto(val robotId: String, val name: String, val location: String = "")

@Serializable
data class PositionDto(val x: Double = 0.0, val y: Double = 0.0, val yaw: Double = 0.0)

@Serializable
data class VelocityDto(val vx: Double = 0.0, val vy: Double = 0.0, val omega: Double = 0.0)

@Serializable
data class AlertDto(
    val id: String,
    val type: String,
    val severity: String,
    val message: String,
    val timestamp: String,
    val acknowledged: Boolean = false,
)

@Serializable
data class RobotTelemetryDto(
    val robotId: String,
    val connection: String = "CONNECTED",
    val battery: Int,
    val operationalState: String,
    val missionId: String? = null,
    val missionProgress: Int = 0,
    val position: PositionDto = PositionDto(),
    val velocity: VelocityDto = VelocityDto(),
    val alerts: List<AlertDto> = emptyList(),
    val updatedAt: String,
    // Variables específicas del servicio de guía
    val destinationId: String? = null,
    val destinationName: String? = null,
    val remainingDistance: Double = 0.0,
    val companions: Int = 0,
)

@Serializable
data class GuideMissionRequestDto(
    val robotId: String,
    val destinationId: String,
    val destinationName: String,
    val x: Double,
    val y: Double,
    val companions: Int,
)

@Serializable
data class CommandResponseDto(
    val commandId: String,
    val status: String,
    val message: String,
    val timestamp: String,
    val missionId: String? = null,
)
