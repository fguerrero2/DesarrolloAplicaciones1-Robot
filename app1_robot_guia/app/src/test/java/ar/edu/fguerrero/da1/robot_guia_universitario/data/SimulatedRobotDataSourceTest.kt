package ar.edu.fguerrero.da1.robot_guia_universitario.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** CP-SIM-01..09: simulador embebido del G1 y escenarios de falla. */
class SimulatedRobotDataSourceTest {
    private val sim = SimulatedRobotDataSource(latencyMs = 0, stepMeters = 2.0)
    private val robot = "g1-01"
    private val aula = CampusCatalog.destinations.first { it.id == "aula-101" }
    private val request = GuideMissionRequestDto(robot, aula.id, aula.name, aula.x, aula.y, 3)

    @Test fun `estado inicial disponible y conectado`() = runTest {
        val t = sim.telemetry(robot)
        assertEquals("IDLE", t.operationalState)
        assertEquals("CONNECTED", t.connection)
        assertEquals("ok", sim.health().status)
        assertEquals(2, sim.robots().size)
    }

    @Test fun `mision de guia avanza hasta llegar a destino`() = runTest {
        val r = sim.startGuide(request)
        assertEquals("CONFIRMED", r.status)
        var t = sim.telemetry(robot)
        assertEquals("GUIDING", t.operationalState)
        assertEquals(3, t.companions)
        repeat(20) { t = sim.telemetry(robot) }
        assertEquals("ARRIVED", t.operationalState)
        assertEquals(100, t.missionProgress)
        assertEquals(0.0, t.remainingDistance, 0.001)
    }

    @Test fun `ids de mision no se repiten entre ejecuciones de la app`() = runTest {
        // Regresión: cada instancia (= cada arranque de la app) generaba "mis-1001".
        val first = SimulatedRobotDataSource(latencyMs = 0).startGuide(request).missionId
        val second = SimulatedRobotDataSource(latencyMs = 0).startGuide(request).missionId
        assertTrue(first != null && second != null && first != second)
    }

    @Test fun `no se aceptan dos misiones simultaneas`() = runTest {
        sim.startGuide(request)
        assertEquals("REJECTED", sim.startGuide(request).status)
    }

    @Test fun `pausar y reanudar`() = runTest {
        val id = sim.startGuide(request).missionId!!
        assertEquals("CONFIRMED", sim.pause(id).status)
        assertEquals("PAUSED", sim.telemetry(robot).operationalState)
        assertEquals("REJECTED", sim.pause(id).status) // ya está en pausa
        assertEquals("CONFIRMED", sim.resume(id).status)
    }

    @Test fun `rechazo forzado afecta solo al proximo comando`() = runTest {
        sim.scenario(robot, "REJECT_NEXT_COMMAND")
        assertEquals("REJECTED", sim.startGuide(request).status)
        assertEquals("CONFIRMED", sim.startGuide(request).status)
    }

    @Test fun `desconexion hace fallar las llamadas y recuperar la restablece`() = runTest {
        sim.scenario(robot, "DISCONNECT")
        try { sim.telemetry(robot); fail("Debía lanzar IOException") } catch (_: IOException) { }
        sim.scenario(robot, "RECOVER")
        assertEquals("CONNECTED", sim.telemetry(robot).connection)
    }

    @Test fun `bateria baja genera alerta critica`() = runTest {
        sim.scenario(robot, "LOW_BATTERY")
        val t = sim.telemetry(robot)
        assertTrue(t.battery < 20)
        assertTrue(t.alerts.any { it.type == "LOW_BATTERY" && it.severity == "CRITICAL" })
        assertEquals("REJECTED", sim.startGuide(request).status)
    }

    @Test fun `visitante rezagado deja al robot esperando hasta recuperar`() = runTest {
        sim.startGuide(request)
        sim.scenario(robot, "VISITOR_LAGGING")
        assertEquals("WAITING", sim.telemetry(robot).operationalState)
        assertEquals("WAITING", sim.telemetry(robot).operationalState)
        sim.scenario(robot, "RECOVER")
        assertEquals("GUIDING", sim.telemetry(robot).operationalState)
    }

    @Test fun `regreso a punto seguro termina disponible en recepcion`() = runTest {
        sim.startGuide(request)
        repeat(2) { sim.telemetry(robot) }
        assertEquals("CONFIRMED", sim.returnSafe(robot).status)
        var t = sim.telemetry(robot)
        repeat(20) { t = sim.telemetry(robot) }
        assertEquals("IDLE", t.operationalState)
        assertEquals(CampusCatalog.SAFE_X, t.position.x, 0.001)
        assertEquals(null, t.missionId)
    }

    @Test fun `reconocer alerta la marca como reconocida`() = runTest {
        sim.scenario(robot, "ROUTE_BLOCKED")
        val alert = sim.telemetry(robot).alerts.first()
        assertEquals("CONFIRMED", sim.ackAlert(alert.id).status)
        assertTrue(sim.telemetry(robot).alerts.first { it.id == alert.id }.acknowledged)
    }
}
