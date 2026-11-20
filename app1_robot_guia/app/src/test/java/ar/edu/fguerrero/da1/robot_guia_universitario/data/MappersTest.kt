package ar.edu.fguerrero.da1.robot_guia_universitario.data

import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandStatus
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ConnectionState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.OperationalState
import org.junit.Assert.assertEquals
import org.junit.Test

/** CP-MAP-01..04: conversiones DTO → Entity → Dominio. */
class MappersTest {
    @Test fun `enum desconocido usa el valor por defecto`() {
        assertEquals(OperationalState.IDLE, safeEnum("BAILANDO", OperationalState.IDLE))
        assertEquals(CommandStatus.CONFIRMED, safeEnum("confirmed", CommandStatus.NO_RESPONSE))
    }

    @Test fun `fecha ISO invalida usa el fallback`() {
        assertEquals(1_763_294_400_000L, parseInstant("2025-11-16T12:00:00Z", 0))
        assertEquals(42L, parseInstant("no-es-fecha", 42))
    }

    @Test fun `telemetria dto se convierte a dominio conservando los datos`() {
        val dto = RobotTelemetryDto(
            robotId = "g1-01", connection = "CONNECTED", battery = 150, operationalState = "GUIDING",
            missionId = "m1", missionProgress = 40, position = PositionDto(1.0, 2.0, 0.5),
            updatedAt = "2025-11-16T12:00:00Z", destinationName = "Aula 101", remainingDistance = 3.5, companions = 2,
        )
        val t = dto.toEntity(receivedAt = 0).toDomain()
        assertEquals(100, t.battery) // se acota a 0..100
        assertEquals(OperationalState.GUIDING, t.operationalState)
        assertEquals(ConnectionState.CONNECTED, t.connection)
        assertEquals(2.0, t.position.y, 0.0)
        assertEquals("Aula 101", t.guide.destinationName)
        assertEquals(2, t.guide.companions)
    }

    @Test fun `respuesta de comando con estado desconocido se trata como sin respuesta`() {
        val r = CommandResponseDto("c1", "???", "x", "2025-11-16T12:00:02Z").toDomain(0)
        assertEquals(CommandStatus.NO_RESPONSE, r.status)
    }
}
