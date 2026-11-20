package ar.edu.fguerrero.da1.robot_guia_universitario.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendMode
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ConnectionState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SimScenario
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.AppViewModelProvider
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.ConnectionPill
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.LabeledValue
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.LocalSnackbarHostState
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.SectionCard
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.TestTags
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.formatTime
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.mission.label

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(state.form.message) { state.form.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() } }
    SettingsContent(
        state = state,
        onMode = viewModel::onModeChange,
        onUrl = viewModel::onUrlChange,
        onTimeout = viewModel::onTimeoutChange,
        onPreset = viewModel::usePreset,
        onTest = viewModel::testConnection,
        onSave = viewModel::save,
        onRestore = viewModel::restoreDefaults,
        onScenario = viewModel::trigger,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onMode: (BackendMode) -> Unit,
    onUrl: (String) -> Unit,
    onTimeout: (String) -> Unit,
    onPreset: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onRestore: () -> Unit,
    onScenario: (SimScenario) -> Unit,
) {
    val f = state.form
    val rest = f.mode == BackendMode.REST
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = "Backend") {
            Text("Origen de datos", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !rest, onClick = { onMode(BackendMode.SIMULATED) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Filled.Computer, null, Modifier.size(18.dp)) },
                ) { Text("Simulador embebido") }
                SegmentedButton(
                    selected = rest, onClick = { onMode(BackendMode.REST) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Filled.Cloud, null, Modifier.size(18.dp)) },
                ) { Text("Backend REST") }
            }
            Text(
                if (rest) "La app se conecta al backend FastAPI / gemelo digital por HTTP."
                else "La app usa un simulador interno: funciona sin red ni backend (ideal para demos).",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = f.baseUrl, onValueChange = onUrl, enabled = rest,
                label = { Text("URL base") },
                isError = rest && state.urlError != null,
                supportingText = { Text(if (rest && state.urlError != null) state.urlError!! else "Emulador: 10.0.2.2 · Teléfono: IP LAN de la PC") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth().testTag(TestTags.URL_FIELD),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(enabled = rest, onClick = { onPreset("http://10.0.2.2:8000/") }, label = { Text("Emulador") })
                AssistChip(enabled = rest, onClick = { onPreset("http://192.168.1.100:8000/") }, label = { Text("Red local") })
                AssistChip(enabled = rest, onClick = { onPreset("http://localhost:8000/") }, label = { Text("localhost") })
            }
            OutlinedTextField(
                value = f.timeoutText, onValueChange = onTimeout, enabled = rest,
                label = { Text("Timeout (segundos)") },
                isError = rest && state.timeoutError != null,
                supportingText = { state.timeoutError?.takeIf { rest }?.let { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.hasUnsavedChanges) {
                Text("Hay cambios sin guardar", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onTest, enabled = !f.testing,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(TestTags.TEST_CONNECTION),
                ) {
                    if (f.testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.NetworkCheck, null)
                    Spacer(Modifier.width(6.dp)); Text("Probar conexión")
                }
                Button(
                    onClick = onSave, enabled = state.hasUnsavedChanges,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(TestTags.SAVE_SETTINGS),
                ) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(6.dp)); Text("Guardar") }
            }
            TextButton(onClick = onRestore) { Icon(Icons.Filled.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text("Restaurar valores por defecto") }
        }

        f.lastHealth?.let { h ->
            SectionCard(title = "Última respuesta") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ConnectionPill(if (h.ok) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED)
                    Text("${h.latencyMs} ms · ${formatTime(h.checkedAt)}", style = MaterialTheme.typography.bodySmall)
                }
                LabeledValue("GET /health", h.message)
            }
        }

        SectionCard(title = "Simulación de escenarios (demo)") {
            Text("Fuerza situaciones de falla para demostrar cómo responde la app.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SimScenario.entries.forEach { s -> AssistChip(onClick = { onScenario(s) }, label = { Text(s.label()) }) }
            }
        }

        SectionCard(title = "Seguridad") {
            Text(
                "La \"Detención operativa\" es una orden por software enviada al robot. NO reemplaza la parada de emergencia física del Unitree G1, que debe estar siempre al alcance del personal.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
