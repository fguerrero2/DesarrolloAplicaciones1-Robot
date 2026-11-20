package ar.edu.fguerrero.da1.robot_guia_universitario.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Destination
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.Position
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SafePoint
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.format1
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.theme.LocalStatusColors
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val MAP_W = 30f
private const val MAP_H = 20f

private data class Building(val name: String, val x1: Float, val y1: Float, val x2: Float, val y2: Float)

private val buildings = listOf(
    Building("Edificio A", 2f, 3f, 14f, 15f),
    Building("Edificio B", 19f, 3f, 28f, 15f),
    Building("Edificio C", 13f, 16.5f, 29f, 19.5f),
)

/**
 * Mapa 2D del campus (30 × 20 m). El eje Y crece hacia arriba, como en la telemetría del robot.
 * Muestra edificios, destinos, punto seguro, robot (con orientación) y ruta al destino.
 */
@Composable
fun CampusMap(
    destinations: List<Destination>,
    robotPosition: Position?,
    targetDestinationId: String?,
    modifier: Modifier = Modifier,
    selectedDestinationId: String? = null,
    onDestinationTap: ((Destination) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = colors.onSurfaceVariant)
    val target = destinations.firstOrNull { it.id == targetDestinationId }
    val description = buildString {
        append("Mapa del campus. ")
        robotPosition?.let { append("Robot en x ${format1(it.x)}, y ${format1(it.y)}. ") }
        target?.let { append("Destino: ${it.name}.") }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(MAP_W / MAP_H)
            .semantics { contentDescription = description }
            .pointerInput(destinations, onDestinationTap) {
                if (onDestinationTap == null) return@pointerInput
                detectTapGestures { tap ->
                    val scale = size.width / MAP_W
                    destinations
                        .minByOrNull { hypot(it.x * scale - tap.x.toDouble(), (MAP_H - it.y) * scale - tap.y.toDouble()) }
                        ?.takeIf { hypot(it.x * scale - tap.x.toDouble(), (MAP_H - it.y) * scale - tap.y.toDouble()) < 48.0 }
                        ?.let(onDestinationTap)
                }
            },
    ) {
        val s = size.width / MAP_W
        fun p(x: Double, y: Double) = Offset((x * s).toFloat(), ((MAP_H - y) * s).toFloat())

        drawRect(colors.surfaceContainerHigh)
        // Grilla cada 5 m
        for (gx in 0..MAP_W.toInt() step 5) drawLine(colors.outlineVariant, Offset(gx * s, 0f), Offset(gx * s, size.height), 1f)
        for (gy in 0..MAP_H.toInt() step 5) drawLine(colors.outlineVariant, Offset(0f, gy * s), Offset(size.width, gy * s), 1f)

        buildings.forEach { b ->
            val topLeft = Offset(b.x1 * s, (MAP_H - b.y2) * s)
            val sz = Size((b.x2 - b.x1) * s, (b.y2 - b.y1) * s)
            drawRect(colors.surface, topLeft, sz)
            drawRect(colors.outline, topLeft, sz, style = Stroke(2f))
            drawText(measurer, b.name, topLeft + Offset(6f, 4f), labelStyle)
        }

        // Punto seguro
        val safe = p(SafePoint.X, SafePoint.Y)
        drawCircle(status.success, 10f * density / 2, safe)
        drawText(measurer, "Punto seguro", safe + Offset(12f, -28f), labelStyle.copy(color = status.success))

        // Ruta
        if (robotPosition != null && target != null) {
            drawLine(
                colors.primary, p(robotPosition.x, robotPosition.y), p(target.x, target.y), 4f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f)),
            )
        }

        // Destinos
        destinations.forEach { d ->
            val c = p(d.x, d.y)
            val highlighted = d.id == targetDestinationId || d.id == selectedDestinationId
            drawCircle(if (highlighted) colors.tertiary else colors.secondary, if (highlighted) 14f else 9f, c)
            drawCircle(colors.surface, if (highlighted) 6f else 4f, c)
            // Si la etiqueta no entra a la derecha del pin, se dibuja a la izquierda.
            val label = measurer.measure(d.name, labelStyle)
            val dx = if (c.x + 12f + label.size.width > size.width) -12f - label.size.width else 12f
            drawText(label, topLeft = c + Offset(dx, 4f))
        }

        // Robot con orientación (yaw)
        if (robotPosition != null) {
            val c = p(robotPosition.x, robotPosition.y)
            drawCircle(colors.primary.copy(alpha = 0.2f), 30f, c)
            drawCircle(colors.primary, 16f, c)
            val head = Offset(c.x + (cos(robotPosition.yaw) * 30).toFloat(), c.y - (sin(robotPosition.yaw) * 30).toFloat())
            drawLine(colors.onPrimaryContainer, c, head, 6f)
        }
    }
}
