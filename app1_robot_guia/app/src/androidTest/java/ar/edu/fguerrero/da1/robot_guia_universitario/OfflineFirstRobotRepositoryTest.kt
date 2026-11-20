package ar.edu.fguerrero.da1.robot_guia_universitario

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.edu.fguerrero.da1.robot_guia_universitario.data.OfflineFirstRobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.data.RemoteDataSourceProvider
import ar.edu.fguerrero.da1.robot_guia_universitario.data.RobotDatabase
import ar.edu.fguerrero.da1.robot_guia_universitario.data.SimulatedRobotDataSource
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.AlertType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandStatus
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ConnectionState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionFinalState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionRequest
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.OperationalState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SimScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CP-REPO-01..08: repositorio offline-first con Room en memoria + simulador embebido.
 * Verifica persistencia de comandos/misiones/alertas y comportamiento sin conexión.
 */
@RunWith(AndroidJUnit4::class)
class OfflineFirstRobotRepositoryTest {
    private lateinit var db: RobotDatabase
    private lateinit var sim: SimulatedRobotDataSource
    private lateinit var repo: OfflineFirstRobotRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before fun setUp() {
        db = RobotDatabase.inMemory(ApplicationProvider.getApplicationContext())
        sim = SimulatedRobotDataSource(latencyMs = 0, stepMeters = 3.0)
        repo = OfflineFirstRobotRepository(db.dao(), MockSettingsRepository(), RemoteDataSourceProvider(sim), scope)
    }

    @After fun tearDown() { scope.cancel(); db.close() }

    private suspend fun telemetry() = db.dao().getTelemetry(TEST_ROBOT)!!

    @Test fun refrescarRobotsLosGuardaLocalmente() = runBlocking {
        repo.refreshRobots().getOrThrow()
        assertEquals(2, repo.observeRobots().first().size)
    }

    @Test fun iniciarMisionPersisteComandoConfirmadoYMision() = runBlocking {
        val r = repo.startMission(MissionRequest(TEST_ROBOT, "aula-101", 2)).getOrThrow()
        assertEquals(CommandStatus.CONFIRMED, r.status)
        val mission = repo.observeActiveMission(TEST_ROBOT).first()
        assertNotNull(mission)
        assertEquals("Aula 101", mission!!.destinationName)
        val cmd = repo.observeCommands(TEST_ROBOT).first().first()
        assertEquals(CommandType.START_MISSION, cmd.type)
        assertEquals(CommandStatus.CONFIRMED, cmd.status)
        assertEquals(mission.id, cmd.missionId)
        assertTrue(repo.observeMissionEvents(mission.id).first().isNotEmpty())
    }

    @Test fun noSePermitenDosMisionesSimultaneas() = runBlocking {
        repo.startMission(MissionRequest(TEST_ROBOT, "aula-101", 1))
        assertTrue(repo.startMission(MissionRequest(TEST_ROBOT, "aula-204", 1)).isFailure)
    }

    @Test fun llegarADestinoCompletaLaMision() = runBlocking {
        repo.startMission(MissionRequest(TEST_ROBOT, "aula-101", 1))
        repeat(10) { repo.syncTelemetry(TEST_ROBOT) }
        assertNull(repo.observeActiveMission(TEST_ROBOT).first())
        assertEquals(MissionFinalState.COMPLETED, repo.observeMissionHistory().first().first().finalState)
    }

    @Test fun desconexionConservaUltimoDatoYGeneraAlerta() = runBlocking {
        repo.syncTelemetry(TEST_ROBOT)
        val before = telemetry()
        repo.triggerScenario(TEST_ROBOT, SimScenario.DISCONNECT)
        repo.syncTelemetry(TEST_ROBOT)
        val after = repo.observeTelemetry(TEST_ROBOT).firstOrNull { it != null }!!
        assertEquals(ConnectionState.DISCONNECTED, after.connection)
        assertEquals(before.battery, after.battery)
        assertEquals(before.updatedAt, after.updatedAt)
        assertTrue(repo.observeAlerts(TEST_ROBOT).first().any { it.type == AlertType.DISCONNECTED })
    }

    @Test fun comandoSinConexionQuedaComoNoResponse() = runBlocking {
        repo.triggerScenario(TEST_ROBOT, SimScenario.DISCONNECT)
        val r = repo.operationalStop(TEST_ROBOT).getOrThrow()
        assertEquals(CommandStatus.NO_RESPONSE, r.status)
        assertEquals(CommandStatus.NO_RESPONSE, repo.observeCommands(TEST_ROBOT).first().first().status)
    }

    @Test fun comandoRechazadoQuedaRegistradoConAlerta() = runBlocking {
        repo.triggerScenario(TEST_ROBOT, SimScenario.REJECT_NEXT_COMMAND)
        val r = repo.startMission(MissionRequest(TEST_ROBOT, "aula-101", 1)).getOrThrow()
        assertEquals(CommandStatus.REJECTED, r.status)
        assertNull(repo.observeActiveMission(TEST_ROBOT).first())
        assertTrue(repo.observeAlerts(TEST_ROBOT).first().any { it.type == AlertType.COMMAND_REJECTED })
    }

    @Test fun cancelarYRegresarCierranLaMisionEnHistorial() = runBlocking {
        repo.startMission(MissionRequest(TEST_ROBOT, "lab-robotica", 1))
        val id = repo.observeActiveMission(TEST_ROBOT).first()!!.id
        repo.cancelMission(TEST_ROBOT, id)
        assertEquals(MissionFinalState.CANCELLED, repo.observeMissionHistory().first().first().finalState)

        repo.startMission(MissionRequest(TEST_ROBOT, "aula-101", 1))
        repo.returnToSafePoint(TEST_ROBOT)
        assertEquals(MissionFinalState.RETURNED_SAFE, repo.observeMissionHistory().first().first().finalState)
        assertEquals(OperationalState.RETURNING.name, telemetry().operationalState)
    }

    @Test fun reconocerAlertaLocalNoRequiereRed() = runBlocking {
        repo.syncTelemetry(TEST_ROBOT)
        repo.triggerScenario(TEST_ROBOT, SimScenario.DISCONNECT)
        repo.syncTelemetry(TEST_ROBOT)
        val alert = repo.observeAlerts(TEST_ROBOT).first().first { it.type == AlertType.DISCONNECTED }
        assertEquals(CommandStatus.CONFIRMED, repo.acknowledgeAlert(TEST_ROBOT, alert.id).getOrThrow().status)
        assertTrue(repo.observeAlerts(TEST_ROBOT).first().first { it.id == alert.id }.acknowledged)
    }
}
