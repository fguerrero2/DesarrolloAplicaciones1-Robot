package ar.edu.fguerrero.da1.robot_guia_universitario.domain

/**
 * Reglas de habilitación de comandos (Fase 4). Funciones puras → fáciles de testear.
 * La UI deshabilita los botones que no figuran en el resultado (heurística
 * de Nielsen #5: prevención de errores).
 */
object CommandRules {
    const val MIN_BATTERY_TO_START = 20
    const val LOW_BATTERY = 20
    const val MAX_COMPANIONS = 8

    fun availableCommands(
        telemetry: RobotTelemetry?,
        hasPendingCommand: Boolean,
        hasActiveMission: Boolean,
    ): Set<CommandType> {
        if (telemetry == null) return emptySet()
        // La detención operativa siempre se ofrece (salvo que ya esté detenido): es la acción de seguridad.
        val stop = if (telemetry.operationalState != OperationalState.STOPPED) setOf(CommandType.STOP) else emptySet()
        if (hasPendingCommand || telemetry.connection != ConnectionState.CONNECTED) return stop

        val byState = when (telemetry.operationalState) {
            OperationalState.IDLE, OperationalState.ARRIVED ->
                buildSet {
                    if (!hasActiveMission && telemetry.battery >= MIN_BATTERY_TO_START) add(CommandType.START_MISSION)
                    if (!SafePoint.isAt(telemetry.position)) add(CommandType.RETURN_SAFE)
                }
            OperationalState.GUIDING, OperationalState.WAITING ->
                setOf(CommandType.PAUSE, CommandType.CANCEL, CommandType.RETURN_SAFE)
            OperationalState.PAUSED ->
                setOf(CommandType.RESUME, CommandType.CANCEL, CommandType.RETURN_SAFE)
            OperationalState.RETURNING -> emptySet()
            OperationalState.STOPPED ->
                buildSet {
                    add(CommandType.RETURN_SAFE)
                    if (hasActiveMission) { add(CommandType.RESUME); add(CommandType.CANCEL) }
                }
        }
        return byState + stop
    }

    /** Motivo legible de por qué no se puede iniciar una misión, o null si se puede. */
    fun startBlockedReason(telemetry: RobotTelemetry?, hasActiveMission: Boolean, hasPendingCommand: Boolean): String? = when {
        telemetry == null -> "Seleccioná un robot para comenzar."
        telemetry.connection != ConnectionState.CONNECTED -> "Sin conexión con el robot. Revisá Configuración → Backend."
        hasPendingCommand -> "Esperando la respuesta del comando anterior."
        hasActiveMission -> "Ya hay una misión en curso. Finalizala o cancelala antes de iniciar otra."
        telemetry.battery < MIN_BATTERY_TO_START -> "Batería insuficiente (${telemetry.battery}%). Mínimo $MIN_BATTERY_TO_START%."
        telemetry.operationalState !in setOf(OperationalState.IDLE, OperationalState.ARRIVED) ->
            "El robot está ocupado (${telemetry.operationalState.name})."
        else -> null
    }
}
