package ar.edu.fguerrero.da1.robot_guia_universitario.ui

import ar.edu.fguerrero.da1.robot_guia_universitario.MainDispatcherRule
import ar.edu.fguerrero.da1.robot_guia_universitario.MockRobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.MockSettingsRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendConfig
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendMode
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandRules
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandStatus
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ConnectionState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.HealthResult
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.OperationalState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Robot
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SimScenario
import ar.edu.fguerrero.da1.robot_guia_universitario.sampleMission
import ar.edu.fguerrero.da1.robot_guia_universitario.sampleTelemetry
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.alerts.AlertsViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.destination.DestinationViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.history.HistoryViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.home.HomeViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.mission.MissionViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** CP-VM-01..20: ViewModels probados con MockRobotRepository (sin Android, sin red). */
class ViewModelsTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = MockRobotRepository()
    private val settings = MockSettingsRepository()

    /** stateIn(WhileSubscribed) necesita un suscriptor para emitir. */
    private fun <T> TestScope.collect(flow: StateFlow<T>) = backgroundScope.launch(main.dispatcher) { flow.collect { } }

    // ---------- Inicio ----------

    @Test fun `home muestra robots y robot seleccionado`() = runTest {
        val vm = HomeViewModel(repo, settings); collect(vm.state)
        assertEquals(1, vm.state.value.robots.size)
        assertEquals("g1-01", vm.state.value.selectedRobotId)
        assertNotNull(vm.state.value.status?.telemetry)
    }

    @Test fun `home cambia de robot y lo persiste en configuracion`() = runTest {
        repo.robots.value = repo.robots.value + Robot("g1-02", "Otro", "B")
        val vm = HomeViewModel(repo, settings); collect(vm.state)
        vm.selectRobot("g1-02")
        assertEquals("g1-02", settings.selectedRobotId.first())
        assertEquals("g1-02", vm.state.value.selectedRobotId)
    }

    @Test fun `home detencion operativa envia STOP e informa el resultado`() = runTest {
        val vm = HomeViewModel(repo, settings); collect(vm.state)
        vm.execute(CommandType.STOP)
        assertEquals(listOf(CommandType.STOP), repo.calls)
        assertTrue(vm.state.value.message!!.contains("Confirmado"))
        vm.messageShown()
        assertNull(vm.state.value.message)
    }

    @Test fun `home informa error al refrescar sin backend`() = runTest {
        repo.refreshResult = Result.failure(Exception("timeout"))
        val vm = HomeViewModel(repo, settings); collect(vm.state)
        vm.refresh()
        assertTrue(vm.state.value.message!!.contains("timeout"))
        assertFalse(vm.state.value.refreshing)
    }

    // ---------- Selección de destino ----------

    @Test fun `destino busqueda ignora tildes y mayusculas`() = runTest {
        val vm = DestinationViewModel(repo, settings); collect(vm.state)
        vm.onQueryChange("ROBOTICA")
        assertEquals(listOf("lab-robotica"), vm.state.value.items.map { it.destination.id })
    }

    @Test fun `destino acompanados entre 1 y el maximo`() = runTest {
        val vm = DestinationViewModel(repo, settings); collect(vm.state)
        vm.decrementCompanions()
        assertEquals(1, vm.state.value.form.companions)
        repeat(20) { vm.incrementCompanions() }
        assertEquals(CommandRules.MAX_COMPANIONS, vm.state.value.form.companions)
    }

    @Test fun `destino sin seleccion no permite iniciar`() = runTest {
        val vm = DestinationViewModel(repo, settings); collect(vm.state)
        assertFalse(vm.state.value.canStart)
        vm.start()
        assertNotNull(vm.state.value.form.error)
        assertTrue(repo.calls.isEmpty())
    }

    @Test fun `destino iniciar mision confirmada navega a mision`() = runTest {
        val vm = DestinationViewModel(repo, settings); collect(vm.state)
        vm.preselect("aula-204")
        vm.incrementCompanions()
        vm.start()
        assertTrue(vm.state.value.form.missionStarted)
        assertEquals("Aula 204", repo.activeMission.value?.destinationName)
        assertEquals(2, repo.activeMission.value?.companions)
    }

    @Test fun `destino mision rechazada muestra el motivo y no navega`() = runTest {
        repo.nextStatus = CommandStatus.REJECTED
        val vm = DestinationViewModel(repo, settings); collect(vm.state)
        vm.select("aula-101")
        vm.start()
        assertFalse(vm.state.value.form.missionStarted)
        assertTrue(vm.state.value.form.error!!.startsWith("Rechazado"))
    }

    @Test fun `destino sin conexion queda bloqueado con explicacion`() = runTest {
        repo.telemetry.value = sampleTelemetry(connection = ConnectionState.DISCONNECTED)
        val vm = DestinationViewModel(repo, settings); collect(vm.state)
        vm.select("aula-101")
        assertFalse(vm.state.value.canStart)
        assertTrue(vm.state.value.status!!.startBlockedReason!!.contains("conexión"))
    }

    // ---------- Misión en curso ----------

    private fun guiding() {
        repo.activeMission.value = sampleMission()
        repo.telemetry.value = sampleTelemetry(OperationalState.GUIDING, missionId = "mis-1", destinationId = "aula-101")
    }

    @Test fun `mision pausar y luego reanudar`() = runTest {
        guiding()
        val vm = MissionViewModel(repo, settings); collect(vm.state)
        vm.execute(CommandType.PAUSE)
        assertEquals(OperationalState.PAUSED, vm.state.value.status?.telemetry?.operationalState)
        assertTrue(CommandType.RESUME in vm.state.value.status!!.available)
        vm.execute(CommandType.RESUME)
        assertEquals(listOf(CommandType.PAUSE, CommandType.RESUME), repo.calls)
    }

    @Test fun `mision comando sin respuesta queda registrado como NO_RESPONSE`() = runTest {
        guiding()
        repo.nextStatus = CommandStatus.NO_RESPONSE
        val vm = MissionViewModel(repo, settings); collect(vm.state)
        vm.execute(CommandType.PAUSE)
        assertEquals(CommandStatus.NO_RESPONSE, vm.state.value.status?.lastCommand?.status)
        // La acción no se ejecutó: el estado real sigue siendo GUIDING
        assertEquals(OperationalState.GUIDING, vm.state.value.status?.telemetry?.operationalState)
    }

    @Test fun `mision cancelar la mueve al historial`() = runTest {
        guiding()
        val vm = MissionViewModel(repo, settings); collect(vm.state)
        vm.execute(CommandType.CANCEL)
        assertNull(vm.state.value.status?.activeMission)
        assertEquals(1, repo.history.value.size)
    }

    @Test fun `mision comando no habilitado no se envia`() = runTest {
        guiding()
        val vm = MissionViewModel(repo, settings); collect(vm.state)
        vm.execute(CommandType.RESUME) // está guiando, no pausada
        assertTrue(repo.calls.isEmpty())
        assertNotNull(vm.state.value.message)
    }

    @Test fun `mision forzar escenario delega en el repositorio`() = runTest {
        guiding()
        val vm = MissionViewModel(repo, settings); collect(vm.state)
        vm.trigger(SimScenario.ROUTE_BLOCKED)
        assertEquals(listOf(SimScenario.ROUTE_BLOCKED), repo.scenarios)
    }

    @Test fun `mision calcula tiempo estimado de llegada`() = runTest {
        guiding()
        val vm = MissionViewModel(repo, settings); collect(vm.state)
        assertEquals(20L, vm.state.value.etaSeconds) // 10 m / 0.5 m/s
    }

    // ---------- Alertas ----------

    @Test fun `alertas reconocer marca la alerta y filtra pendientes`() = runTest {
        repo.addAlert("a1"); repo.addAlert("a2")
        val vm = AlertsViewModel(repo, settings); collect(vm.state)
        assertEquals(2, vm.state.value.visible.size)
        vm.acknowledge("a1")
        assertEquals(listOf("a2"), vm.state.value.visible.map { it.id })
        vm.setPendingOnly(false)
        assertEquals(2, vm.state.value.visible.size)
    }

    // ---------- Historial ----------

    @Test fun `historial expande y colapsa una mision`() = runTest {
        repo.history.value = listOf(sampleMission("m1", ar.edu.fguerrero.da1.robot_guia_universitario.domain.MissionFinalState.COMPLETED))
        val vm = HistoryViewModel(repo); collect(vm.state)
        assertEquals(1, vm.state.value.completed)
        vm.toggle("m1"); assertEquals("m1", vm.state.value.expandedId)
        vm.toggle("m1"); assertNull(vm.state.value.expandedId)
    }

    // ---------- Configuración ----------

    @Test fun `configuracion url invalida no se guarda`() = runTest {
        val vm = SettingsViewModel(repo, settings); collect(vm.state)
        vm.onModeChange(BackendMode.REST)
        vm.onUrlChange("10.0.2.2:8000")
        assertNotNull(vm.state.value.urlError)
        vm.save()
        assertEquals(BackendMode.SIMULATED, settings.current.mode)
    }

    @Test fun `configuracion probar conexion guarda y consulta health`() = runTest {
        repo.health = HealthResult(false, "Connection refused", 12, 0)
        val vm = SettingsViewModel(repo, settings); collect(vm.state)
        vm.onModeChange(BackendMode.REST)
        vm.onUrlChange("http://192.168.1.100:8000/")
        vm.testConnection()
        assertEquals("http://192.168.1.100:8000/", settings.current.baseUrl)
        assertEquals(1, repo.healthChecks)
        assertFalse(vm.state.value.form.lastHealth!!.ok)
    }

    @Test fun `configuracion restaurar valores por defecto`() = runTest {
        settings.saveConfig(BackendConfig(BackendMode.REST, "http://x:1/", 9))
        val vm = SettingsViewModel(repo, settings); collect(vm.state)
        vm.restoreDefaults()
        assertEquals(BackendConfig(), settings.current)
        assertEquals(BackendConfig.DEFAULT_URL, vm.state.value.form.baseUrl)
        assertFalse(vm.state.value.hasUnsavedChanges)
    }
}
