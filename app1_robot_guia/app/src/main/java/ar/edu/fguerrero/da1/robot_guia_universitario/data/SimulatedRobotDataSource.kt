package ar.edu.fguerrero.da1.robot_guia_universitario.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Simulador embebido del Unitree G1 en modo guía. Implementa el mismo contrato que el
 * backend REST, así la app funciona sin backend (criterio "APK inicia sin backend")
 * y se pueden forzar los escenarios de falla de la demostración.
 *
 * Cada llamada a telemetry() avanza la simulación un paso (≈1 s de tiempo real).
 */
class SimulatedRobotDataSource(
    private val clock: () -> Long = System::currentTimeMillis,
    private val stepMeters: Double = 0.5,
    private val latencyMs: Long = 120,
) : RobotRemoteDataSource {

    private class SimRobot(val id: String, val name: String, val location: String) {
        var battery = 86.0
        var state = "IDLE"
        var x = CampusCatalog.SAFE_X
        var y = CampusCatalog.SAFE_Y
        var yaw = 0.0
        var vx = 0.0
        var vy = 0.0
        var omega = 0.0
        var missionId: String? = null
        var destId: String? = null
        var destName: String? = null
        var destX = 0.0
        var destY = 0.0
        var totalDistance = 0.0
        var companions = 0
        var visitorLagging = false
        var routeBlocked = false
        var destinationUnavailable = false
        var lowBatteryAlerted = false
        val alerts = mutableListOf<AlertDto>()
    }

    private val mutex = Mutex()
    private val robots = linkedMapOf(
        "g1-01" to SimRobot("g1-01", "Unitree G1 · Recepción", "Hall principal"),
        "g1-02" to SimRobot("g1-02", "Unitree G1 · Edificio B", "Planta baja B"),
    )
    private var connected = true
    private var rejectNext = false

    private fun now() = Instant.ofEpochMilli(clock()).toString()
    /**
     * Ids únicos entre ejecuciones: Room guarda misiones/comandos/alertas por id, y un
     * contador que reinicia con la app repetía "mis-1001" y rompía el insert (PK duplicada).
     */
    private fun nextId(prefix: String) = "$prefix-${UUID.randomUUID().toString().replace("-", "").take(10)}"

    /** Simula latencia y desconexión. */
    private suspend fun link() {
        if (latencyMs > 0) delay(latencyMs)
        if (!connected) throw IOException("El robot no responde (desconexión simulada)")
    }

    private fun robot(id: String) = robots[id] ?: throw IOException("Robot $id inexistente")
    private fun robotByMission(missionId: String) = robots.values.firstOrNull { it.missionId == missionId }

    private fun ok(message: String, missionId: String? = null) =
        CommandResponseDto(nextId("cmd"), "CONFIRMED", message, now(), missionId)

    private fun rejected(message: String) = CommandResponseDto(nextId("cmd"), "REJECTED", message, now())

    /** Si se forzó "rechazo de comando", el próximo comando se rechaza. */
    private fun consumeForcedRejection(): CommandResponseDto? =
        if (rejectNext) { rejectNext = false; rejected("Comando rechazado por el robot (escenario forzado)") } else null

    private fun SimRobot.alert(type: String, severity: String, message: String) {
        alerts.add(0, AlertDto(nextId("al"), type, severity, message, now()))
    }

    override suspend fun health(): HealthDto { link(); return HealthDto("ok", "simulador-embebido") }

    override suspend fun robots(): List<RobotDto> = mutex.withLock {
        link(); robots.values.map { RobotDto(it.id, it.name, it.location) }
    }

    override suspend fun telemetry(robotId: String): RobotTelemetryDto = mutex.withLock {
        link()
        val r = robot(robotId)
        step(r)
        val remaining = if (r.destId != null && r.state != "RETURNING") hypot(r.destX - r.x, r.destY - r.y) else 0.0
        val progress = when {
            r.missionId == null -> 0
            r.state == "ARRIVED" -> 100
            r.totalDistance <= 0.0 -> 0
            else -> (((r.totalDistance - remaining) / r.totalDistance) * 100).toInt().coerceIn(0, 99)
        }
        RobotTelemetryDto(
            robotId = r.id, connection = "CONNECTED", battery = r.battery.toInt(),
            operationalState = r.state, missionId = r.missionId, missionProgress = progress,
            position = PositionDto(r.x, r.y, r.yaw), velocity = VelocityDto(r.vx, r.vy, r.omega),
            alerts = r.alerts.toList(), updatedAt = now(),
            destinationId = r.destId, destinationName = r.destName,
            remainingDistance = remaining, companions = r.companions,
        )
    }

    private fun step(r: SimRobot) {
        r.vx = 0.0; r.vy = 0.0; r.omega = 0.0
        when (r.state) {
            "GUIDING" -> if (r.visitorLagging || r.routeBlocked || r.destinationUnavailable) {
                r.state = "WAITING"
            } else if (moveToward(r, r.destX, r.destY)) {
                r.state = "ARRIVED"
                r.alert("INFO", "INFO", "Llegada a ${r.destName}. Misión completada.")
            }
            "WAITING" -> if (!r.visitorLagging && !r.routeBlocked && !r.destinationUnavailable) r.state = "GUIDING"
            "RETURNING" -> if (moveToward(r, CampusCatalog.SAFE_X, CampusCatalog.SAFE_Y)) {
                r.state = "IDLE"; r.missionId = null; r.destId = null; r.destName = null; r.companions = 0
            }
        }
        val moving = r.vx != 0.0 || r.vy != 0.0
        r.battery = (r.battery - if (moving) 0.25 else 0.02).coerceAtLeast(0.0)
        if (r.battery < 20 && !r.lowBatteryAlerted) {
            r.lowBatteryAlerted = true
            r.alert("LOW_BATTERY", "CRITICAL", "Batería baja (${r.battery.toInt()}%). Se recomienda regresar al punto seguro.")
        }
    }

    /** Avanza un paso hacia (tx, ty). Devuelve true si llegó. */
    private fun moveToward(r: SimRobot, tx: Double, ty: Double): Boolean {
        val dx = tx - r.x
        val dy = ty - r.y
        val dist = hypot(dx, dy)
        if (dist <= stepMeters) {
            r.x = tx; r.y = ty
            return true
        }
        val newYaw = atan2(dy, dx)
        r.omega = newYaw - r.yaw
        r.yaw = newYaw
        r.vx = stepMeters * cos(newYaw)
        r.vy = stepMeters * sin(newYaw)
        r.x += r.vx
        r.y += r.vy
        return false
    }

    override suspend fun startGuide(request: GuideMissionRequestDto): CommandResponseDto = mutex.withLock {
        link()
        consumeForcedRejection()?.let { return it }
        val r = robot(request.robotId)
        if (r.state !in setOf("IDLE", "ARRIVED")) return rejected("El robot ya tiene una misión en curso")
        if (r.battery < 20) return rejected("Batería insuficiente para iniciar la guía")
        r.missionId = nextId("mis")
        r.destId = request.destinationId
        r.destName = request.destinationName
        r.destX = request.x; r.destY = request.y
        r.totalDistance = hypot(request.x - r.x, request.y - r.y)
        r.companions = request.companions
        r.state = "GUIDING"
        ok("Misión de guía hacia ${request.destinationName} aceptada", r.missionId)
    }

    override suspend fun pause(missionId: String) = missionCommand(missionId, setOf("GUIDING", "WAITING"), "PAUSED", "Misión pausada")
    override suspend fun resume(missionId: String) = missionCommand(missionId, setOf("PAUSED", "STOPPED"), "GUIDING", "Misión reanudada")

    override suspend fun cancel(missionId: String): CommandResponseDto = mutex.withLock {
        link()
        consumeForcedRejection()?.let { return it }
        val r = robotByMission(missionId) ?: return rejected("Misión $missionId inexistente")
        r.state = "IDLE"; r.missionId = null; r.destId = null; r.destName = null; r.companions = 0
        ok("Misión cancelada. El robot queda disponible en su posición actual.")
    }

    private suspend fun missionCommand(missionId: String, from: Set<String>, to: String, msg: String): CommandResponseDto = mutex.withLock {
        link()
        consumeForcedRejection()?.let { return it }
        val r = robotByMission(missionId) ?: return rejected("Misión $missionId inexistente")
        if (r.state !in from) return rejected("No se puede ejecutar en estado ${r.state}")
        r.state = to
        ok(msg, missionId)
    }

    override suspend fun returnSafe(robotId: String): CommandResponseDto = mutex.withLock {
        link()
        consumeForcedRejection()?.let { return it }
        val r = robot(robotId)
        r.missionId = null; r.destId = null; r.destName = null
        r.state = "RETURNING"
        ok("Regresando a ${CampusCatalog.SAFE_POINT_NAME}")
    }

    override suspend fun stop(robotId: String): CommandResponseDto = mutex.withLock {
        link()
        consumeForcedRejection()?.let { return it }
        val r = robot(robotId)
        r.state = "STOPPED"
        ok("Detención operativa por software ejecutada", r.missionId)
    }

    override suspend fun ackAlert(alertId: String): CommandResponseDto = mutex.withLock {
        link()
        consumeForcedRejection()?.let { return it }
        for (r in robots.values) {
            val i = r.alerts.indexOfFirst { it.id == alertId }
            if (i >= 0) {
                r.alerts[i] = r.alerts[i].copy(acknowledged = true)
                return ok("Alerta reconocida")
            }
        }
        rejected("Alerta $alertId inexistente")
    }

    /** No pasa por link(): los escenarios deben poder forzarse aun "desconectado". */
    override suspend fun scenario(robotId: String, scenario: String): Unit = mutex.withLock {
        val r = robot(robotId)
        when (scenario) {
            "LOW_BATTERY" -> { r.battery = 12.0; r.lowBatteryAlerted = false }
            "DISCONNECT" -> connected = false
            "REJECT_NEXT_COMMAND" -> rejectNext = true
            "VISITOR_LAGGING" -> { r.visitorLagging = true; r.alert("VISITOR_LAGGING", "WARNING", "Visitante rezagado: el robot espera al grupo.") }
            "DESTINATION_UNAVAILABLE" -> { r.destinationUnavailable = true; r.alert("DESTINATION_UNAVAILABLE", "WARNING", "Destino temporalmente inaccesible (aula cerrada).") }
            "ROUTE_BLOCKED" -> { r.routeBlocked = true; r.alert("ROUTE_BLOCKED", "CRITICAL", "Ruta bloqueada por un obstáculo en el pasillo.") }
            "RECOVER" -> {
                connected = true; rejectNext = false
                r.visitorLagging = false; r.routeBlocked = false; r.destinationUnavailable = false
                if (r.battery < 60) { r.battery = 85.0; r.lowBatteryAlerted = false }
                r.alert("INFO", "INFO", "Situación normalizada.")
            }
        }
    }
}
