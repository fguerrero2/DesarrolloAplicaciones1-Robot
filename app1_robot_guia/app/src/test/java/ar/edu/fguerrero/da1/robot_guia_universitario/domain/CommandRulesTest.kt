package ar.edu.fguerrero.da1.robot_guia_universitario.domain

import ar.edu.fguerrero.da1.robot_guia_universitario.sampleTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CP-DOM-01..10: reglas de habilitación de comandos. */
class CommandRulesTest {
    private fun available(t: RobotTelemetry?, pending: Boolean = false, mission: Boolean = false) =
        CommandRules.availableCommands(t, pending, mission)

    @Test fun `sin telemetria no hay comandos`() {
        assertTrue(available(null).isEmpty())
    }

    @Test fun `robot disponible permite iniciar mision y detener`() {
        val cmds = available(sampleTelemetry(OperationalState.IDLE))
        assertTrue(CommandType.START_MISSION in cmds)
        assertTrue(CommandType.STOP in cmds)
        assertTrue(CommandType.PAUSE !in cmds)
    }

    @Test fun `en punto seguro no se ofrece regresar a punto seguro`() {
        val atSafe = sampleTelemetry(position = Position(SafePoint.X, SafePoint.Y, 0.0))
        assertTrue(CommandType.RETURN_SAFE !in available(atSafe))
        assertTrue(CommandType.RETURN_SAFE in available(sampleTelemetry(position = Position(10.0, 10.0, 0.0))))
    }

    @Test fun `guiando permite pausar cancelar regresar y detener`() {
        assertEquals(
            setOf(CommandType.PAUSE, CommandType.CANCEL, CommandType.RETURN_SAFE, CommandType.STOP),
            available(sampleTelemetry(OperationalState.GUIDING, missionId = "m"), mission = true),
        )
    }

    @Test fun `en pausa se reanuda en lugar de pausar`() {
        val cmds = available(sampleTelemetry(OperationalState.PAUSED), mission = true)
        assertTrue(CommandType.RESUME in cmds)
        assertTrue(CommandType.PAUSE !in cmds)
    }

    @Test fun `no se pueden iniciar dos misiones simultaneas`() {
        assertTrue(CommandType.START_MISSION !in available(sampleTelemetry(OperationalState.IDLE), mission = true))
    }

    @Test fun `bateria baja impide iniciar mision`() {
        assertTrue(CommandType.START_MISSION !in available(sampleTelemetry(battery = 15)))
        assertNotNull(CommandRules.startBlockedReason(sampleTelemetry(battery = 15), false, false))
    }

    @Test fun `desconectado solo deja la detencion operativa`() {
        val t = sampleTelemetry(OperationalState.GUIDING, connection = ConnectionState.DISCONNECTED)
        assertEquals(setOf(CommandType.STOP), available(t, mission = true))
    }

    @Test fun `con comando pendiente solo deja la detencion operativa`() {
        assertEquals(setOf(CommandType.STOP), available(sampleTelemetry(OperationalState.GUIDING), pending = true, mission = true))
    }

    @Test fun `detenido no ofrece detener otra vez pero si reanudar`() {
        val cmds = available(sampleTelemetry(OperationalState.STOPPED), mission = true)
        assertTrue(CommandType.STOP !in cmds)
        assertTrue(CommandType.RESUME in cmds)
    }

    @Test fun `regresando no admite comandos de mision`() {
        assertEquals(setOf(CommandType.STOP), available(sampleTelemetry(OperationalState.RETURNING)))
    }

    @Test fun `sin bloqueos el motivo es nulo`() {
        assertNull(CommandRules.startBlockedReason(sampleTelemetry(), false, false))
    }
}
