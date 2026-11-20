package ar.edu.fguerrero.da1.robot_guia_universitario.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

fun formatTime(epochMs: Long): String = timeFmt.format(Instant.ofEpochMilli(epochMs))
fun formatDateTime(epochMs: Long): String = dateFmt.format(Instant.ofEpochMilli(epochMs))

fun formatDuration(seconds: Long?): String = when {
    seconds == null -> "—"
    seconds < 60 -> "${seconds} s"
    else -> "${seconds / 60} min ${seconds % 60} s"
}

fun formatAge(seconds: Long): String = when {
    seconds < 2 -> "ahora"
    seconds < 60 -> "hace $seconds s"
    seconds < 3600 -> "hace ${seconds / 60} min"
    else -> "hace ${seconds / 3600} h"
}

fun formatMeters(m: Double): String = String.format(Locale.US, "%.1f m", m)
fun format1(v: Double): String = String.format(Locale.US, "%.1f", v)
fun format2(v: Double): String = String.format(Locale.US, "%.2f", v)

/** Reloj que se actualiza cada segundo: permite mostrar "hace X s" sin lógica en el ViewModel. */
@Composable
fun rememberNow(intervalMs: Long = 1_000): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(intervalMs); value = System.currentTimeMillis() }
    }
    return now
}
