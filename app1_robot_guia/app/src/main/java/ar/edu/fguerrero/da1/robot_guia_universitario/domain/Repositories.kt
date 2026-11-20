package ar.edu.fguerrero.da1.robot_guia_universitario.domain

import kotlinx.coroutines.flow.Flow

/*
 * Contratos (puertos) de la capa de dominio. La UI sólo conoce estas interfaces;
 * la implementación real (OfflineFirstRobotRepository) o la de pruebas
 * (MockRobotRepository) se inyectan desde AppContainer o desde el test.
 *
 * Offline-first: los métodos observe* leen SIEMPRE de la base local (Room).
 * La sincronización con el robot/backend escribe en la base local y la UI se
 * actualiza sola. Si no hay conexión, se sigue mostrando el último dato conocido.
 */
interface RobotRepository {
    fun observeRobots(): Flow<List<Robot>>
    fun observeDestinations(): Flow<List<Destination>>

    /** Telemetría cacheada. Mientras haya suscriptores, el repositorio sondea al robot cada ~1 s. */
    fun observeTelemetry(robotId: String): Flow<RobotTelemetry?>
    fun observeAlerts(robotId: String): Flow<List<RobotAlert>>
    fun observeActiveMission(robotId: String): Flow<MissionRecord?>
    fun observeMissionHistory(): Flow<List<MissionRecord>>
    fun observeMissionEvents(missionId: String): Flow<List<MissionEvent>>
    fun observeCommands(robotId: String): Flow<List<CommandRecord>>

    suspend fun refreshRobots(): Result<Unit>
    suspend fun checkHealth(): HealthResult

    suspend fun startMission(request: MissionRequest): Result<CommandResponse>
    suspend fun pauseMission(robotId: String, missionId: String): Result<CommandResponse>
    suspend fun resumeMission(robotId: String, missionId: String): Result<CommandResponse>
    suspend fun cancelMission(robotId: String, missionId: String): Result<CommandResponse>
    suspend fun returnToSafePoint(robotId: String): Result<CommandResponse>
    suspend fun operationalStop(robotId: String): Result<CommandResponse>
    suspend fun acknowledgeAlert(robotId: String, alertId: String): Result<CommandResponse>

    /** Sólo para demostración: fuerza un escenario en el simulador/backend. */
    suspend fun triggerScenario(robotId: String, scenario: SimScenario): Result<Unit>
}

interface SettingsRepository {
    val config: Flow<BackendConfig>
    val selectedRobotId: Flow<String?>
    suspend fun saveConfig(config: BackendConfig)
    suspend fun restoreDefaults()
    suspend fun selectRobot(robotId: String)
}
