package ar.edu.fguerrero.da1.robot_guia_universitario.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.AlertSeverity
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandStatus
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandRules
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ConnectionState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.OperationalState
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotTelemetry
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.label
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.theme.LocalStatusColors

/** Tags para encontrar elementos desde los tests de Compose. */
object TestTags {
    const val STATUS_BAR = "status_bar"
    const val STALE_BANNER = "stale_banner"
    const val STOP_BUTTON = "stop_button"
    const val START_BUTTON = "start_mission_button"
    const val PAUSE_BUTTON = "pause_button"
    const val RESUME_BUTTON = "resume_button"
    const val CANCEL_BUTTON = "cancel_button"
    const val RETURN_BUTTON = "return_safe_button"
    const val CONFIRM_DIALOG_OK = "confirm_ok"
    const val COMMAND_STATUS = "command_status"
    const val COMPANIONS_VALUE = "companions_value"
    const val URL_FIELD = "url_field"
    const val TEST_CONNECTION = "test_connection"
    const val SAVE_SETTINGS = "save_settings"
}

/* ---------- Colores semánticos ---------- */

@Composable
fun connectionColor(state: ConnectionState): Color = when (state) {
    ConnectionState.CONNECTED -> LocalStatusColors.current.success
    ConnectionState.DISCONNECTED -> LocalStatusColors.current.danger
    ConnectionState.UNKNOWN -> MaterialTheme.colorScheme.outline
}

@Composable
fun batteryColor(battery: Int): Color = when {
    battery < CommandRules.LOW_BATTERY -> LocalStatusColors.current.danger
    battery < 40 -> LocalStatusColors.current.warning
    else -> LocalStatusColors.current.success
}

@Composable
fun stateColor(state: OperationalState): Color = when (state) {
    OperationalState.IDLE, OperationalState.ARRIVED -> LocalStatusColors.current.success
    OperationalState.GUIDING, OperationalState.RETURNING -> LocalStatusColors.current.info
    OperationalState.PAUSED, OperationalState.WAITING -> LocalStatusColors.current.warning
    OperationalState.STOPPED -> LocalStatusColors.current.danger
}

@Composable
fun severityColor(severity: AlertSeverity): Color = when (severity) {
    AlertSeverity.INFO -> LocalStatusColors.current.info
    AlertSeverity.WARNING -> LocalStatusColors.current.warning
    AlertSeverity.CRITICAL -> LocalStatusColors.current.danger
}

fun severityIcon(severity: AlertSeverity): ImageVector = when (severity) {
    AlertSeverity.INFO -> Icons.Filled.Info
    AlertSeverity.WARNING -> Icons.Filled.Warning
    AlertSeverity.CRITICAL -> Icons.Filled.Error
}

@Composable
fun commandStatusColor(status: CommandStatus): Color = when (status) {
    CommandStatus.PENDING -> LocalStatusColors.current.info
    CommandStatus.CONFIRMED -> LocalStatusColors.current.success
    CommandStatus.REJECTED -> LocalStatusColors.current.danger
    CommandStatus.NO_RESPONSE -> LocalStatusColors.current.warning
}

fun commandStatusIcon(status: CommandStatus): ImageVector = when (status) {
    CommandStatus.PENDING -> Icons.Filled.HourglassTop
    CommandStatus.CONFIRMED -> Icons.Filled.CheckCircle
    CommandStatus.REJECTED -> Icons.Filled.Error
    CommandStatus.NO_RESPONSE -> Icons.Filled.CloudOff
}

/* ---------- Componentes ---------- */

/** Etiqueta de estado: SIEMPRE ícono + texto + color (no depender sólo del color, accesibilidad). */
@Composable
fun StatusPill(text: String, color: Color, icon: ImageVector?, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.14f), contentColor = color, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ConnectionPill(state: ConnectionState) = StatusPill(
    text = when (state) {
        ConnectionState.CONNECTED -> "Conectado"
        ConnectionState.DISCONNECTED -> "Desconectado"
        ConnectionState.UNKNOWN -> "Sin datos"
    },
    color = connectionColor(state),
    icon = if (state == ConnectionState.CONNECTED) Icons.Filled.Wifi else Icons.Filled.WifiOff,
)

@Composable
fun BatteryPill(battery: Int) = StatusPill(
    text = "$battery%",
    color = batteryColor(battery),
    icon = when {
        battery < CommandRules.LOW_BATTERY -> Icons.Filled.Battery2Bar
        battery < 70 -> Icons.Filled.Battery5Bar
        else -> Icons.Filled.BatteryFull
    },
    modifier = Modifier.semantics { contentDescription = "Batería $battery por ciento" },
)

@Composable
fun StatePill(state: OperationalState) = StatusPill(state.label(), stateColor(state), null)

@Composable
fun CommandStatusPill(status: CommandStatus, modifier: Modifier = Modifier) =
    StatusPill(status.label(), commandStatusColor(status), commandStatusIcon(status), modifier.testTag(TestTags.COMMAND_STATUS))

/**
 * Barra de estado persistente en todas las pantallas (Nielsen #1: visibilidad del estado del sistema).
 */
@Composable
fun RobotStatusBar(robotName: String?, telemetry: RobotTelemetry?, now: Long, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(TestTags.STATUS_BAR),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    robotName ?: "Sin robot seleccionado",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                telemetry?.let { ConnectionPill(it.connection) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val subtitle = when {
                    telemetry == null -> "Esperando datos…"
                    telemetry.updatedAt == 0L -> "Nunca se recibieron datos"
                    else -> "Actualizado ${formatAge(telemetry.ageSeconds(now))}"
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (telemetry != null && telemetry.updatedAt != 0L) {
                    StatePill(telemetry.operationalState)
                    BatteryPill(telemetry.battery)
                }
            }
        }
    }
}

/** Aviso de datos desactualizados: se muestra el último dato conocido (offline-first). */
@Composable
fun StaleDataBanner(telemetry: RobotTelemetry?, now: Long) {
    if (telemetry == null || !telemetry.isStale(now)) return
    val danger = LocalStatusColors.current.danger
    Card(
        colors = CardDefaults.cardColors(containerColor = danger.copy(alpha = 0.12f), contentColor = danger),
        modifier = Modifier.fillMaxWidth().testTag(TestTags.STALE_BANNER),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CloudOff, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Datos desactualizados", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (telemetry.updatedAt == 0L) "No se pudo contactar al robot. Revisá Configuración → Backend."
                    else "Último dato ${formatAge(telemetry.ageSeconds(now))}. Los comandos pueden no llegar al robot.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * Botón de detención operativa: destacado, siempre visible y aclarando que es por software
 * (no reemplaza la parada de emergencia física del robot).
 */
@Composable
fun OperationalStopButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(TestTags.STOP_BUTTON),
    ) {
        Icon(Icons.Filled.PanTool, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DETENCIÓN OPERATIVA", style = MaterialTheme.typography.labelLarge)
            Text("Por software · no reemplaza la parada de emergencia física", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors(),
                modifier = Modifier.testTag(TestTags.CONFIRM_DIALOG_OK),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Volver") } },
    )
}

@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null) OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}
