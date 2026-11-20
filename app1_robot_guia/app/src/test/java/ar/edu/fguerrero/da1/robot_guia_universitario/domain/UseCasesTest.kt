package ar.edu.fguerrero.da1.robot_guia_universitario.domain

import ar.edu.fguerrero.da1.robot_guia_universitario.MockRobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.TEST_ROBOT
import ar.edu.fguerrero.da1.robot_guia_universitario.sampleTelemetry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CP-DOM-11..17: casos de uso. */
class UseCasesTest {
    private val repo = MockRobotRepository()
    private val start = StartGuideMissionUseCase(repo)
    private val execute = ExecuteCommandUseCase(repo)
    private val request = MissionRequest(TEST_ROBOT, "aula-101", 2)

    @Test fun `iniciar mision valida delega en el repositorio`() = runTest {
        val r = start(request, sampleTelemetry(), hasActiveMission = false, hasPendingCommand = false)
        assertEquals(CommandStatus.CONFIRMED, r.getOrThrow().status)
        assertEquals(listOf(CommandType.START_MISSION), repo.calls)
    }

    @Test fun `iniciar con mision activa falla sin llamar al repositorio`() = runTest {
        val r = start(request, sampleTelemetry(), hasActiveMission = true, hasPendingCommand = false)
        assertTrue(r.exceptionOrNull() is ValidationException)
        assertTrue(repo.calls.isEmpty())
    }

    @Test fun `acompanados fuera de rango es invalido`() = runTest {
        assertTrue(start(request.copy(companions = 0), sampleTelemetry(), false, false).isFailure)
        assertTrue(start(request.copy(companions = CommandRules.MAX_COMPANIONS + 1), sampleTelemetry(), false, false).isFailure)
        assertTrue(repo.calls.isEmpty())
    }

    @Test fun `iniciar desconectado es invalido`() = runTest {
        val r = start(request, sampleTelemetry(connection = ConnectionState.DISCONNECTED), false, false)
        assertTrue(r.isFailure)
    }

    @Test fun `comando no disponible no se envia`() = runTest {
        val r = execute(CommandType.PAUSE, TEST_ROBOT, "m", available = setOf(CommandType.STOP))
        assertTrue(r.isFailure)
        assertTrue(repo.calls.isEmpty())
    }

    @Test fun `comando de mision sin mision activa falla`() = runTest {
        assertTrue(execute(CommandType.CANCEL, TEST_ROBOT, null, setOf(CommandType.CANCEL)).isFailure)
    }

    @Test fun `comando disponible se enruta al metodo correcto`() = runTest {
        execute(CommandType.PAUSE, TEST_ROBOT, "m", setOf(CommandType.PAUSE))
        execute(CommandType.STOP, TEST_ROBOT, null, setOf(CommandType.STOP))
        execute(CommandType.RETURN_SAFE, TEST_ROBOT, null, setOf(CommandType.RETURN_SAFE))
        assertEquals(listOf(CommandType.PAUSE, CommandType.STOP, CommandType.RETURN_SAFE), repo.calls)
    }

    @Test fun `estado del robot combina telemetria mision y comandos`() = runTest {
        repo.activeMission.value = ar.edu.fguerrero.da1.robot_guia_universitario.sampleMission()
        repo.telemetry.value = sampleTelemetry(OperationalState.GUIDING, missionId = "mis-1")
        val status = ObserveRobotStatusUseCase(repo)(TEST_ROBOT).first()
        assertEquals("mis-1", status.activeMission?.id)
        assertTrue(CommandType.PAUSE in status.available)
        assertTrue(status.startBlockedReason != null)
    }

    @Test fun `comando pendiente reciente bloquea pero uno viejo no`() {
        val now = 100_000L
        fun cmd(sentAt: Long) = CommandRecord("c", TEST_ROBOT, null, CommandType.PAUSE, CommandStatus.PENDING, "", sentAt, null)
        val recent = RobotStatus(TEST_ROBOT, sampleTelemetry(), null, listOf(cmd(now - 1_000)), emptyList(), now)
        val stale = RobotStatus(TEST_ROBOT, sampleTelemetry(), null, listOf(cmd(now - 60_000)), emptyList(), now)
        assertTrue(recent.pendingCommand != null)
        assertTrue(stale.pendingCommand == null)
    }
}
