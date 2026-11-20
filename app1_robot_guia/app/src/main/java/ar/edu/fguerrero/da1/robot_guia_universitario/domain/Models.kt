package ar.edu.fguerrero.da1.robot_guia_universitario.domain

/*
 * Modelos de dominio: Kotlin puro, sin dependencias de Android, Room ni Retrofit.
 * Todas las capas superiores (UI) e inferiores (datos) hablan en estos términos.
 */

enum class ConnectionState { CONNECTED, DISCONNECTED, UNKNOWN }

enum class OperationalState {
    IDLE,       // Disponible en recepción / punto seguro
    GUIDING,    // Guiando visitantes hacia el destino
    PAUSED,     // Misión pausada por el operador
    WAITING,    // Detenido por incidente (visitante rezagado, ruta bloqueada…)
    ARRIVED,    // Llegó al destino: misión completada
    RETURNING,  // Regresando al punto seguro
    STOPPED,    // Detención operativa por software (NO es parada de emergencia física)
}

enum class CommandStatus { PENDING, CONFIRMED, REJECTED, NO_RESPONSE }

enum class CommandType { START_MISSION, PAUSE, RESUME, CANCEL, RETURN_SAFE, STOP, ACK_ALERT }

enum class AlertSeverity { INFO, WARNING, CRITICAL }

enum class AlertType {
    LOW_BATTERY, DISCONNECTED, COMMAND_REJECTED,
    VISITOR_LAGGING, DESTINATION_UNAVAILABLE, ROUTE_BLOCKED, INFO,
}

enum class DestinationType { AULA, LABORATORIO, AUDITORIO, OFICINA }

enum class MissionFinalState { IN_PROGRESS, COMPLETED, CANCELLED, RETURNED_SAFE, STOPPED }

enum class BackendMode { SIMULATED, REST }

/** Escenarios que se pueden forzar para la demostración (Fase 6). */
enum class SimScenario { LOW_BATTERY, DISCONNECT, REJECT_NEXT_COMMAND, VISITOR_LAGGING, DESTINATION_UNAVAILABLE, ROUTE_BLOCKED, RECOVER }

/** Punto seguro del campus (recepción), en metros. */
object SafePoint {
    const val X = 1.0
    const val Y = 1.0
    const val NAME = "Recepción (punto seguro)"
    fun isAt(position: Position, tolerance: Double = 0.5) =
        kotlin.math.hypot(position.x - X, position.y - Y) <= tolerance
}

data class Position(val x: Double, val y: Double, val yaw: Double)

data class Velocity(val vx: Double, val vy: Double, val omega: Double)

data class Robot(val id: String, val name: String, val location: String)

data class Destination(
    val id: String,
    val name: String,
    val type: DestinationType,
    val building: String,
    val floor: Int,
    val x: Double,
    val y: Double,
)

/** Variables específicas del servicio de guía. */
data class GuideInfo(
    val destinationId: String?,
    val destinationName: String?,
    val remainingDistance: Double,
    val companions: Int,
)

data class RobotAlert(
    val id: String,
    val robotId: String,
    val type: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val timestamp: Long,
    val acknowledged: Boolean,
)

data class RobotTelemetry(
    val robotId: String,
    val connection: ConnectionState,
    val battery: Int,
    val operationalState: OperationalState,
    val missionId: String?,
    val missionProgress: Int,
    val position: Position,
    val velocity: Velocity,
    val guide: GuideInfo,
    /** Momento (epoch ms) en que el robot generó estos datos. */
    val updatedAt: Long,
) {
    fun ageSeconds(now: Long): Long = ((now - updatedAt) / 1000).coerceAtLeast(0)
    fun isStale(now: Long, thresholdSeconds: Long = STALE_SECONDS): Boolean =
        connection != ConnectionState.CONNECTED || ageSeconds(now) > thresholdSeconds

    companion object { const val STALE_SECONDS = 5L }
}

data class MissionRequest(
    val robotId: String,
    val destinationId: String,
    val companions: Int,
)

/** Respuesta del robot/backend a un comando. */
data class CommandResponse(
    val commandId: String,
    val status: CommandStatus,
    val message: String,
    val timestamp: Long,
    val missionId: String? = null,
)

/** Registro local de un comando: orden solicitada + respuesta obtenida. */
data class CommandRecord(
    val id: String,
    val robotId: String,
    val missionId: String?,
    val type: CommandType,
    val status: CommandStatus,
    val message: String,
    val sentAt: Long,
    val respondedAt: Long?,
)

data class MissionRecord(
    val id: String,
    val robotId: String,
    val destinationId: String,
    val destinationName: String,
    val companions: Int,
    val startedAt: Long,
    val endedAt: Long?,
    val finalState: MissionFinalState,
    val alertCount: Int,
) {
    val durationSeconds: Long? get() = endedAt?.let { (it - startedAt) / 1000 }
    val isActive: Boolean get() = finalState == MissionFinalState.IN_PROGRESS
}

data class MissionEvent(val missionId: String, val timestamp: Long, val message: String)

data class BackendConfig(
    val mode: BackendMode = BackendMode.SIMULATED,
    val baseUrl: String = DEFAULT_URL,
    val timeoutSeconds: Int = DEFAULT_TIMEOUT,
) {
    companion object {
        const val DEFAULT_URL = "http://10.0.2.2:8000/"
        const val DEFAULT_TIMEOUT = 3
    }
}

data class HealthResult(val ok: Boolean, val message: String, val latencyMs: Long, val checkedAt: Long)
