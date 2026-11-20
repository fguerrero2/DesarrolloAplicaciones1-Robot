package ar.edu.fguerrero.da1.robot_guia_universitario.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.OperationalState
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.AppViewModelProvider
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.ConfirmDialog
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.EmptyState
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.LabeledValue
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.LocalSnackbarHostState
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.OperationalStopButton
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.SectionCard
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.StaleDataBanner
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.TestTags
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.format1
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.format2
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.formatMeters
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.formatTime
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.rememberNow

@Composable
fun HomeScreen(
    onNewMission: () -> Unit,
    onOpenMission: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }
    HomeContent(
        state = state,
        now = rememberNow(),
        onSelectRobot = viewModel::selectRobot,
        onRefresh = viewModel::refresh,
        onCommand = viewModel::execute,
        onNewMission = onNewMission,
        onOpenMission = onOpenMission,
        onOpenSettings = onOpenSettings,
    )
}

/** Contenido sin estado propio: recibe todo por parámetro → fácil de previsualizar y testear. */
@Composable
fun HomeContent(
    state: HomeUiState,
    now: Long,
    onSelectRobot: (String) -> Unit,
    onRefresh: () -> Unit,
    onCommand: (CommandType) -> Unit,
    onNewMission: () -> Unit,
    onOpenMission: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var confirmReturn by rememberSaveable { mutableStateOf(false) }
    val status = state.status
    val telemetry = status?.telemetry

    if (state.robots.isEmpty() && state.selectedRobotId == null) {
        EmptyState(
            icon = Icons.Filled.SmartToy,
            title = "No hay robots disponibles",
            message = "No se pudo obtener la lista de robots. Verificá que el backend esté encendido o usá el simulador embebido.",
            actionLabel = "Configurar backend",
            onAction = onOpenSettings,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StaleDataBanner(telemetry, now)

        // 1. Selección de robot
        SectionCard(title = "Robot") {
            state.robots.forEach { robot ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(robot.id == state.selectedRobotId, role = Role.RadioButton) { onSelectRobot(robot.id) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = robot.id == state.selectedRobotId, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(robot.name, style = MaterialTheme.typography.bodyLarge)
                        Text(robot.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = onRefresh, enabled = !state.refreshing) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(if (state.refreshing) "Actualizando…" else "Actualizar lista")
            }
        }

        // 2. Misión en curso (si hay)
        status?.activeMission?.let { mission ->
            Card(
                onClick = onOpenMission,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Misión en curso → ${mission.destinationName}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Ver misión")
                    }
                    LinearProgressIndicator(progress = { (telemetry?.missionProgress ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${telemetry?.missionProgress ?: 0}% · faltan ${formatMeters(telemetry?.guide?.remainingDistance ?: 0.0)} · ${mission.companions} acompañado(s)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        // 3. Telemetría
        if (telemetry != null && telemetry.updatedAt != 0L) {
            SectionCard(title = "Telemetría") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabeledValue("Posición (x, y)", "${format1(telemetry.position.x)}, ${format1(telemetry.position.y)} m", Modifier.weight(1f))
                    LabeledValue("Orientación", "${format2(telemetry.position.yaw)} rad", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LabeledValue("Velocidad (vx, vy)", "${format2(telemetry.velocity.vx)}, ${format2(telemetry.velocity.vy)} m/s", Modifier.weight(1f))
                    LabeledValue("Giro ω", "${format2(telemetry.velocity.omega)} rad/s", Modifier.weight(1f))
                }
                LabeledValue("Último dato", formatTime(telemetry.updatedAt))
            }
        }

        // 4. Acciones
        Button(
            onClick = onNewMission,
            enabled = status?.startBlockedReason == null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(TestTags.START_BUTTON),
        ) {
            Icon(Icons.Filled.NearMe, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Nueva misión de guía")
        }
        status?.startBlockedReason?.takeIf { status.activeMission == null }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(
            onClick = { if (status?.activeMission != null) confirmReturn = true else onCommand(CommandType.RETURN_SAFE) },
            enabled = status?.available?.contains(CommandType.RETURN_SAFE) == true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(TestTags.RETURN_BUTTON),
        ) {
            Icon(Icons.Filled.Home, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Regresar a punto seguro")
        }
        OperationalStopButton(
            enabled = status?.available?.contains(CommandType.STOP) == true,
            onClick = { onCommand(CommandType.STOP) },
        )
        if (telemetry?.operationalState == OperationalState.STOPPED) {
            Text("Robot detenido por software. Podés reanudar la misión, cancelarla o regresar a punto seguro.", style = MaterialTheme.typography.bodySmall)
        }
    }

    if (confirmReturn) {
        ConfirmDialog(
            title = "¿Regresar a punto seguro?",
            message = "La misión en curso se dará por finalizada y el robot volverá a recepción. Los visitantes deberán continuar por su cuenta.",
            confirmLabel = "Regresar",
            onConfirm = { confirmReturn = false; onCommand(CommandType.RETURN_SAFE) },
            onDismiss = { confirmReturn = false },
        )
    }
}
