package ar.edu.fguerrero.da1.robot_guia_universitario

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendMode
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ConnectionState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.OperationalState
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.TestTags
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.destination.DestinationScreen
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.destination.DestinationViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.home.HomeScreen
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.home.HomeViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.mission.MissionScreen
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.mission.MissionViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.settings.SettingsScreen
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.settings.SettingsViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.theme.RobotGuiaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CP-UI-01..08: pantallas reales + ViewModels reales + MockRobotRepository. */
@RunWith(AndroidJUnit4::class)
class ScreensUiTest {
    @get:Rule val compose = createComposeRule()
    private val repo = MockRobotRepository()
    private val settings = MockSettingsRepository()

    private fun guiding() {
        repo.activeMission.value = sampleMission()
        repo.telemetry.value = sampleTelemetry(OperationalState.GUIDING, missionId = "mis-1", destinationId = "aula-101")
    }

    @Test fun inicioMuestraRobotYPermiteNuevaMision() {
        compose.setContent { RobotGuiaTheme { HomeScreen({}, {}, {}, HomeViewModel(repo, settings)) } }
        compose.onNodeWithText("Unitree G1 · Test").assertIsDisplayed()
        compose.onNodeWithTag(TestTags.START_BUTTON).performScrollTo().assertIsEnabled()
        compose.onNodeWithTag(TestTags.STOP_BUTTON).performScrollTo().assertIsEnabled()
    }

    @Test fun inicioSinConexionMuestraAvisoYBloqueaInicio() {
        repo.telemetry.value = sampleTelemetry(connection = ConnectionState.DISCONNECTED, updatedAt = System.currentTimeMillis() - 30_000)
        compose.setContent { RobotGuiaTheme { HomeScreen({}, {}, {}, HomeViewModel(repo, settings)) } }
        compose.onNodeWithTag(TestTags.STALE_BANNER).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.START_BUTTON).performScrollTo().assertIsNotEnabled()
        // La detención operativa sigue disponible aun sin conexión
        compose.onNodeWithTag(TestTags.STOP_BUTTON).performScrollTo().assertIsEnabled()
    }

    @Test fun misionCancelarPideConfirmacionAntesDeEnviar() {
        guiding()
        compose.setContent { RobotGuiaTheme { MissionScreen({}, MissionViewModel(repo, settings)) } }
        compose.onNodeWithTag(TestTags.CANCEL_BUTTON).performScrollTo().performClick()
        compose.onNodeWithText("¿Cancelar la misión?").assertIsDisplayed()
        assertTrue(repo.calls.isEmpty()) // todavía no se envió
        compose.onNodeWithTag(TestTags.CONFIRM_DIALOG_OK).performClick()
        compose.waitForIdle()
        assertEquals(listOf(CommandType.CANCEL), repo.calls)
    }

    @Test fun misionPausarCambiaElBotonAReanudar() {
        guiding()
        compose.setContent { RobotGuiaTheme { MissionScreen({}, MissionViewModel(repo, settings)) } }
        compose.onNodeWithTag(TestTags.PAUSE_BUTTON).performScrollTo().performClick()
        compose.onNodeWithTag(TestTags.RESUME_BUTTON).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(TestTags.COMMAND_STATUS).performScrollTo().assertIsDisplayed()
    }

    @Test fun misionMuestraIncidenteConGuiaDeRecuperacion() {
        guiding()
        repo.addAlert("a1", message = "Ruta bloqueada por un obstáculo")
        compose.setContent { RobotGuiaTheme { MissionScreen({}, MissionViewModel(repo, settings)) } }
        compose.onNodeWithText("Ruta bloqueada por un obstáculo").assertIsDisplayed()
        compose.onNodeWithText("El robot no puede avanzar", substring = true).assertIsDisplayed()
    }

    @Test fun destinoAcompanadosYConfirmacion() {
        var started = false
        compose.setContent { RobotGuiaTheme { DestinationScreen("aula-101", { started = true }, DestinationViewModel(repo, settings)) } }
        compose.onNodeWithContentDescription("Más acompañados").performClick()
        compose.onNodeWithTag(TestTags.COMPANIONS_VALUE).assertTextEquals("2")
        compose.onNodeWithTag(TestTags.START_BUTTON).performClick()
        compose.waitUntil(3_000) { started }
        assertEquals(2, repo.activeMission.value?.companions)
    }

    @Test fun destinoBusquedaSinResultadosOfreceLimpiar() {
        compose.setContent { RobotGuiaTheme { DestinationScreen(null, {}, DestinationViewModel(repo, settings)) } }
        compose.onNodeWithText("Buscar aula, laboratorio u oficina").performTextReplacement("zzz")
        compose.onNodeWithText("Sin resultados").assertIsDisplayed()
        compose.onNodeWithText("Limpiar búsqueda").performClick()
        compose.onNodeWithText("Aula 101").assertIsDisplayed()
    }

    @Test fun configuracionValidaUrlYPruebaConexion() {
        compose.setContent { RobotGuiaTheme { SettingsScreen(SettingsViewModel(repo, settings)) } }
        compose.onNodeWithText("Backend REST").performClick()
        compose.onNodeWithTag(TestTags.URL_FIELD).performTextReplacement("sin-protocolo")
        compose.onNodeWithText("Formato inválido", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.URL_FIELD).performTextReplacement("http://10.0.2.2:8000/")
        compose.onNodeWithTag(TestTags.TEST_CONNECTION).performClick()
        compose.waitUntil(3_000) { repo.healthChecks == 1 }
        compose.onNodeWithText("Última respuesta").performScrollTo().assertIsDisplayed()
        assertEquals(BackendMode.REST, settings.current.mode)
    }
}
