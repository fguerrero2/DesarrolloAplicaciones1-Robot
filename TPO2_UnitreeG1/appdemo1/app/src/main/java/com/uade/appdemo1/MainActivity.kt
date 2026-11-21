package com.uade.appdemo1

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF101A1B)
private val Panel = Color(0xFF1B2929)
private val PanelRaised = Color(0xFF243535)
private val Paper = Color(0xFFE6EEE8)
private val Muted = Color(0xFF9BADAA)
private val Mint = Color(0xFF9FE0B6)
private val Amber = Color(0xFFFFC66D)
private val Red = Color(0xFFFF8178)
private val Blue = Color(0xFF80D7E8)

internal data class InspectionPoint(
    val id: String,
    val name: String,
    val detail: String,
    val x: Double,
    val y: Double,
    val sampleTemperature: Double,
)

internal data class RobotSnapshot(
    val mode: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val length: Double,
    val simulator: String,
)

private data class InspectionRecord(
    val condition: String,
    val temperature: Double,
    val time: String,
)

private val InspectionPoints = listOf(
    InspectionPoint("P1", "Acceso norte", "Portón y cerramiento", 1.0, 1.0, 21.8),
    InspectionPoint("P2", "Depósito", "Puerta y perímetro", 5.0, 1.5, 22.4),
    InspectionPoint("P3", "Tablero", "Indicadores exteriores", 4.6, 4.0, 24.1),
    InspectionPoint("P4", "Pasillo oeste", "Pared y circulación", 1.2, 6.2, 22.0),
    InspectionPoint("P5", "Salida sur", "Puerta de emergencia", 5.0, 7.0, 23.3),
)

private val Conditions = listOf("Sin novedad", "Revisar", "Riesgo")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val photos = remember { mutableStateMapOf<String, Bitmap>() }
            var photoTarget by remember { mutableStateOf<String?>(null) }
            val photoLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.TakePicturePreview(),
            ) { bitmap ->
                val pointId = photoTarget
                if (bitmap != null && pointId != null) photos[pointId] = bitmap
                photoTarget = null
            }

            InspectionApp(
                photos = photos,
                onTakePhoto = { pointId ->
                    photoTarget = pointId
                    photoLauncher.launch(null)
                },
            )
        }
    }
}

@Composable
private fun InspectionApp(
    photos: Map<String, Bitmap>,
    onTakePhoto: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val records = remember { mutableStateMapOf<String, InspectionRecord>() }
    var robot by remember { mutableStateOf<RobotSnapshot?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var selectedPoint by remember { mutableStateOf(InspectionPoints.first().id) }
    var conditionMenu by remember { mutableStateOf<String?>(null) }
    var tourJob by remember { mutableStateOf<Job?>(null) }
    var isTourRunning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                robot = BackendClient.readRobot()
                connectionError = null
            } catch (error: Exception) {
                connectionError = error.message ?: "No se pudo contactar al backend"
            }
            delay(4_000)
        }
    }

    val startTour: () -> Unit = {
        if (tourJob == null) {
            tourJob = scope.launch {
                isTourRunning = true
                try {
                    robot = BackendClient.configureArea()
                    InspectionPoints.forEach { point ->
                        selectedPoint = point.id
                        robot = BackendClient.moveTo(point)
                        delay(900)
                        records[point.id] = InspectionRecord(
                            condition = records[point.id]?.condition ?: "Sin novedad",
                            temperature = point.sampleTemperature,
                            time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                .format(java.util.Date()),
                        )
                    }
                    robot = BackendClient.stop()
                    connectionError = null
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    connectionError = error.message ?: "Falló el recorrido"
                } finally {
                    isTourRunning = false
                    tourJob = null
                }
            }
        }
    }

    val stopTour: () -> Unit = {
        tourJob?.cancel()
        tourJob = null
        isTourRunning = false
        scope.launch {
            try {
                robot = BackendClient.stop()
            } catch (error: Exception) {
                connectionError = error.message ?: "No se pudo detener el robot"
            }
        }
        Unit
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Ink,
            surface = Panel,
            onBackground = Paper,
            onSurface = Paper,
            primary = Mint,
            secondary = Amber,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp,
                    top = 18.dp,
                    end = 18.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Header(
                        robot = robot,
                        connectionError = connectionError,
                        onRefresh = {
                            scope.launch {
                                try {
                                    robot = BackendClient.readRobot()
                                    connectionError = null
                                } catch (error: Exception) {
                                    connectionError = error.message ?: "Sin conexión"
                                }
                            }
                        },
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column {
                            Text("Ronda perimetral", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Text("Inspección de instalaciones · G1", color = Muted, fontSize = 14.sp)
                        }
                        Text(
                            "${records.size}/${InspectionPoints.size}",
                            color = Mint,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    }
                }
                item {
                    MapPanel(
                        selectedPoint = selectedPoint,
                        completedIds = records.keys,
                        robot = robot,
                        onSelect = { selectedPoint = it },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = startTour,
                            enabled = !isTourRunning && connectionError == null,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Mint,
                                contentColor = Ink,
                                disabledContainerColor = PanelRaised,
                                disabledContentColor = Muted,
                            ),
                        ) {
                            Text(if (isTourRunning) "Recorrido en curso" else "Iniciar recorrido", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = stopTour,
                            enabled = isTourRunning || robot != null,
                            modifier = Modifier.size(width = 58.dp, height = 52.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "Detener recorrido")
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Puntos de inspección", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text("6 × 8 m · ${robot?.simulator ?: "backend"}", color = Muted, fontSize = 12.sp)
                    }
                }
                items(InspectionPoints, key = { it.id }) { point ->
                    InspectionRow(
                        point = point,
                        record = records[point.id],
                        photo = photos[point.id],
                        selected = selectedPoint == point.id,
                        conditionMenuOpen = conditionMenu == point.id,
                        onSelect = { selectedPoint = point.id },
                        onPhoto = { onTakePhoto(point.id) },
                        onOpenConditions = { conditionMenu = point.id },
                        onDismissConditions = { conditionMenu = null },
                        onCondition = { condition ->
                            val previous = records[point.id]
                            if (previous != null) records[point.id] = previous.copy(condition = condition)
                            else records[point.id] = InspectionRecord(condition, point.sampleTemperature, "--:--")
                            conditionMenu = null
                        },
                    )
                }
                item {
                    Text(
                        "Temperatura y estado de ejemplo · la API actual no entrega sensores ni imágenes.",
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    robot: RobotSnapshot?,
    connectionError: String?,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("UADE  /  TPO 2", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("G1 INSPECCIÓN", color = Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (connectionError == null && robot != null) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                contentDescription = null,
                tint = if (connectionError == null && robot != null) Mint else Red,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (connectionError == null && robot != null) "API conectada" else "Sin backend",
                color = if (connectionError == null && robot != null) Mint else Red,
                fontSize = 12.sp,
            )
            IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Actualizar estado", tint = Muted)
            }
        }
    }
    if (connectionError != null) {
        Text(
            "${connectionError.take(120)} · iniciá docker compose en TPO2_UnitreeG1",
            color = Red,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun MapPanel(
    selectedPoint: String,
    completedIds: Set<String>,
    robot: RobotSnapshot?,
    onSelect: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("PLANO DE RECORRIDO", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("N ↑", color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(1.58f)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xFF142020)).padding(12.dp),
            ) {
                val inset = 12.dp.toPx()
                val mapWidth = size.width - inset * 2
                val mapHeight = size.height - inset * 2
                val xAt: (Double) -> Float = { (inset + it.toFloat() / 6f * mapWidth) }
                val yAt: (Double) -> Float = { (inset + (1f - it.toFloat() / 8f) * mapHeight) }

                for (column in 0..6) {
                    val x = inset + mapWidth * column / 6f
                    drawLine(Color(0xFF2B3D3D), Offset(x, inset), Offset(x, inset + mapHeight), 1.dp.toPx())
                }
                for (row in 0..8) {
                    val y = inset + mapHeight * row / 8f
                    drawLine(Color(0xFF2B3D3D), Offset(inset, y), Offset(inset + mapWidth, y), 1.dp.toPx())
                }

                val route = Path()
                InspectionPoints.forEachIndexed { index, point ->
                    val center = Offset(xAt(point.x), yAt(point.y))
                    if (index == 0) route.moveTo(center.x, center.y) else route.lineTo(center.x, center.y)
                }
                drawPath(route, Amber.copy(alpha = 0.7f), style = Stroke(width = 2.dp.toPx()))

                InspectionPoints.forEach { point ->
                    val center = Offset(xAt(point.x), yAt(point.y))
                    val color = when {
                        point.id == selectedPoint -> Amber
                        point.id in completedIds -> Mint
                        else -> Paper
                    }
                    drawCircle(color.copy(alpha = 0.2f), 12.dp.toPx(), center)
                    drawCircle(color, 6.dp.toPx(), center)
                }

                if (robot != null) {
                    val center = Offset(xAt(robot.x.coerceIn(0.0, 6.0)), yAt(robot.y.coerceIn(0.0, 8.0)))
                    drawCircle(Blue, 9.dp.toPx(), center, style = Stroke(width = 2.dp.toPx()))
                    drawCircle(Blue, 3.dp.toPx(), center)
                }
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(Mint, "Completo")
                LegendDot(Paper, "Pendiente")
                LegendDot(Blue, "G1")
                Text("X ${robot?.x?.format1() ?: "-"} · Y ${robot?.y?.format1() ?: "-"} m", color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                InspectionPoints.forEach { point ->
                    val active = point.id == selectedPoint
                    Surface(
                        modifier = Modifier.weight(1f).clickable { onSelect(point.id) },
                        shape = RoundedCornerShape(7.dp),
                        color = if (active) PanelRaised else Color.Transparent,
                    ) {
                        Text(
                            point.id,
                            modifier = Modifier.padding(vertical = 7.dp),
                            color = if (point.id in completedIds) Mint else if (active) Amber else Muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.Circle, contentDescription = null, tint = color, modifier = Modifier.size(7.dp))
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun InspectionRow(
    point: InspectionPoint,
    record: InspectionRecord?,
    photo: Bitmap?,
    selected: Boolean,
    conditionMenuOpen: Boolean,
    onSelect: () -> Unit,
    onPhoto: () -> Unit,
    onOpenConditions: () -> Unit,
    onDismissConditions: () -> Unit,
    onCondition: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = if (selected) PanelRaised else Panel),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape)
                    .background(if (record != null) Mint.copy(alpha = 0.16f) else Color(0xFF314141)),
                contentAlignment = Alignment.Center,
            ) {
                if (record != null) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Inspeccionado", tint = Mint)
                } else {
                    Text(point.id.removePrefix("P"), color = Paper, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(point.name, color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(point.detail, color = Muted, fontSize = 12.sp)
                if (record != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Thermostat, contentDescription = null, tint = Amber, modifier = Modifier.size(14.dp))
                        Text("${record.temperature} °C · ${record.time}", color = Amber, fontSize = 11.sp)
                    }
                }
                Box {
                    FilledTonalButton(
                        onClick = onOpenConditions,
                        enabled = record != null,
                        modifier = Modifier.padding(top = 4.dp).height(30.dp),
                        shape = RoundedCornerShape(7.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = when (record?.condition) {
                                "Riesgo" -> Red.copy(alpha = 0.16f)
                                "Revisar" -> Amber.copy(alpha = 0.16f)
                                else -> Color(0xFF314141)
                            },
                            contentColor = when (record?.condition) {
                                "Riesgo" -> Red
                                "Revisar" -> Amber
                                else -> Muted
                            },
                        ),
                    ) {
                        Text(record?.condition ?: "Pendiente", fontSize = 11.sp)
                    }
                    DropdownMenu(expanded = conditionMenuOpen, onDismissRequest = onDismissConditions) {
                        Conditions.forEach { condition ->
                            DropdownMenuItem(text = { Text(condition) }, onClick = { onCondition(condition) })
                        }
                    }
                }
            }
            if (photo != null) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "Foto de ${point.name}",
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                IconButton(onClick = onPhoto, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Tomar foto de ${point.name}", tint = Mint)
                }
            }
        }
    }
}

private fun Double.format1(): String = "%.1f".format(java.util.Locale.US, this)