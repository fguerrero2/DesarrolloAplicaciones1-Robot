package ar.edu.fguerrero.da1.robot_guia_universitario.ui.mission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.AlertType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.OperationalState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SimScenario
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.label
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.AppViewModelProvider
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.CommandStatusPill
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.ConfirmDialog
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.EmptyState
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.LabeledValue
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.LocalSnackbarHostState
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.OperationalStopButton
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.SectionCard
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.StaleDataBanner
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.StatePill
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.TestTags
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.formatDuration
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.formatMeters
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.formatTime
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.rememberNow
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.severityColor
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.map.CampusMap

@Composable
fun MissionScreen(
    onNewMission: () -> Unit,
    viewModel: MissionViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }
    MissionContent(state, rememberNow(), viewModel::execute, viewModel::trigger, onNewMission)
}

private enum class PendingConfirm { CANCEL, RETURN }

/** Guía de recuperación para cada incidente (Nielsen #9: ayudar a reconocer y recuperarse de errores). */
private fun recoveryHint(type: AlertType): String? = when (type) {
    AlertType.VISITOR_LAGGING -> "El robot espera al grupo. Podés esperar, pausar la misión o reanudarla cuando el visitante se reincorpore."
    AlertType.ROUTE_BLOCKED -> "El robot no puede avanzar. Despejá el pasillo o cancelá y regresá a punto seguro."
    AlertType.DESTINATION_UNAVAILABLE -> "El destino está inaccesible. Cancelá la misión y elegí un destino alternativo."
    AlertType.LOW_BATTERY -> "Batería baja. Se recomienda regresar a punto seguro para cargar."
    AlertType.DISCONNECTED -> "Sin conexión: los comandos pueden no llegar. Verificá la red o el backend."
    AlertType.COMMAND_REJECTED -> "El robot rechazó la orden. Revisá su estado actual antes de reintentar."
    AlertType.INFO -> null
}

@Composable
fun MissionContent(
    state: MissionUiState,
    now: Long,
    onCommand: (CommandType) -> Unit,
    onScenario: (SimScenario) -> Unit,
    onNewMission: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf<PendingConfirm?>(null) }
    val status = state.status
    val telemetry = status?.telemetry
    val mission = status?.activeMission
    val available = status?.available.orEmpty()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StaleDataBanner(telemetry, now)

        when {
            mission != null -> {
                // Encabezado + progreso
                SectionCard(title = null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Guiando hacia", style = MaterialTheme.typography.labelMedium)
                            Text(mission.destinationName, style = MaterialTheme.typography.titleLarge)
                        }
                        telemetry?.let { StatePill(it.operationalState) }
                    }
                    LinearProgressIndicator(progress = { (telemetry?.missionProgress ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabeledValue("Progreso", "${telemetry?.missionProgress ?: 0}%", Modifier.weight(1f))
                        LabeledValue("Distancia restante", formatMeters(telemetry?.guide?.remainingDistance ?: 0.0), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabeledValue("Acompañados", "${telemetry?.guide?.companions ?: mission.companions}", Modifier.weight(1f))
                        LabeledValue("Llegada estimada", formatDuration(state.etaSeconds), Modifier.weight(1f))
                    }
                }
            }
            telemetry?.operationalState == OperationalState.ARRIVED -> SectionCard(title = null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Misión completada: el grupo llegó a ${telemetry.guide.destinationName ?: "destino"}.", style = MaterialTheme.typography.titleMedium)
                }
                Text("Podés enviar el robot de regreso al punto seguro o iniciar una nueva misión.")
                Button(onClick = onNewMission, enabled = status.startBlockedReason == null) { Text("Nueva misión") }
            }
            telemetry?.operationalState == OperationalState.RETURNING -> SectionCard(title = "Regresando a punto seguro") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("El robot vuelve a recepción. Al llegar quedará disponible.")
            }
            else -> EmptyState(
                icon = Icons.Filled.Flag,
                title = "No hay misión en curso",
                message = "Elegí un destino para que el robot guíe a los visitantes.",
                actionLabel = "Nueva misión",
                onAction = onNewMission,
            )
        }

        // Incidentes activos con guía de recuperación
        status?.unacknowledgedAlerts?.take(2)?.forEach { alert ->
            val c = severityColor(alert.severity)
            Card(colors = CardDefaults.cardColors(containerColor = c.copy(alpha = 0.12f))) {
                Column(Modifier.padding(12.dp)) {
                    Text(alert.message, style = MaterialTheme.typography.titleSmall, color = c)
                    recoveryHint(alert.type)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        // Orden solicitada vs. acción ejecutada
        status?.lastCommand?.let { cmd ->
            SectionCard(title = "Última orden vs. estado real") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LabeledValue("Orden solicitada", cmd.type.label(), Modifier.weight(1f))
                    CommandStatusPill(cmd.status)
                }
                Text("${formatTime(cmd.sentAt)} · ${cmd.message}", style = MaterialTheme.typography.bodySmall)
                telemetry?.let { LabeledValue("Estado real del robot", it.operationalState.label()) }
            }
        }

        // Controles (deshabilitados según el estado: Nielsen #5)
        if (mission != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (CommandType.RESUME in available) {
                    FilledTonalButton(
                        onClick = { onCommand(CommandType.RESUME) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(TestTags.RESUME_BUTTON),
                    ) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Reanudar") }
                } else {
                    FilledTonalButton(
                        onClick = { onCommand(CommandType.PAUSE) },
                        enabled = CommandType.PAUSE in available,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(TestTags.PAUSE_BUTTON),
                    ) { Icon(Icons.Filled.Pause, null); Spacer(Modifier.width(4.dp)); Text("Pausar") }
                }
                OutlinedButton(
                    onClick = { confirm = PendingConfirm.CANCEL },
                    enabled = CommandType.CANCEL in available,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(TestTags.CANCEL_BUTTON),
                ) { Icon(Icons.Filled.Close, null); Spacer(Modifier.width(4.dp)); Text("Cancelar") }
            }
        }
        OutlinedButton(
            onClick = { if (mission != null) confirm = PendingConfirm.RETURN else onCommand(CommandType.RETURN_SAFE) },
            enabled = CommandType.RETURN_SAFE in available,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(TestTags.RETURN_BUTTON),
        ) { Icon(Icons.Filled.Home, null); Spacer(Modifier.width(8.dp)); Text("Regresar a punto seguro") }
        OperationalStopButton(enabled = CommandType.STOP in available, onClick = { onCommand(CommandType.STOP) })

        // Mapa
        if (telemetry != null && telemetry.updatedAt != 0L) {
            CampusMap(state.destinations, telemetry.position, telemetry.guide.destinationId)
        }

        // Bitácora de la misión
        if (state.events.isNotEmpty()) {
            SectionCard(title = "Bitácora") {
                state.events.takeLast(8).reversed().forEach {
                    Text("${formatTime(it.timestamp)}  ${it.message}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        ScenarioMenu(onScenario)
    }

    when (confirm) {
        PendingConfirm.CANCEL -> ConfirmDialog(
            title = "¿Cancelar la misión?",
            message = "El robot dejará de guiar a los visitantes y quedará detenido en su posición actual. Esta acción no se puede deshacer.",
            confirmLabel = "Cancelar misión",
            destructive = true,
            onConfirm = { confirm = null; onCommand(CommandType.CANCEL) },
            onDismiss = { confirm = null },
        )
        PendingConfirm.RETURN -> ConfirmDialog(
            title = "¿Regresar a punto seguro?",
            message = "La misión se dará por finalizada y el robot volverá a recepción.",
            confirmLabel = "Regresar",
            onConfirm = { confirm = null; onCommand(CommandType.RETURN_SAFE) },
            onDismiss = { confirm = null },
        )
        null -> Unit
    }
}

/** Menú de demostración para forzar escenarios de falla (Fase 6). */
@Composable
private fun ScenarioMenu(onScenario: (SimScenario) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Filled.Science, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Simular incidente (demo)")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SimScenario.entries.forEach { s ->
                DropdownMenuItem(text = { Text(s.label()) }, onClick = { open = false; onScenario(s) })
            }
        }
    }
}
