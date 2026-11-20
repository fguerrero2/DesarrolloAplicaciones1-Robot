package ar.edu.fguerrero.da1.robot_guia_universitario.ui.destination

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.CommandRules
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.DestinationType
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.label
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.AppViewModelProvider
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.EmptyState
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.TestTags
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.formatMeters

@Composable
fun DestinationScreen(
    preselectedId: String?,
    onMissionStarted: () -> Unit,
    viewModel: DestinationViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(preselectedId) { viewModel.preselect(preselectedId) }
    LaunchedEffect(state.form.missionStarted) {
        if (state.form.missionStarted) { viewModel.navigationHandled(); onMissionStarted() }
    }
    DestinationContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onTypeFilter = viewModel::onTypeFilter,
        onSelect = viewModel::select,
        onIncrement = viewModel::incrementCompanions,
        onDecrement = viewModel::decrementCompanions,
        onStart = viewModel::start,
    )
}

fun DestinationType.icon() = when (this) {
    DestinationType.AULA -> Icons.Filled.MeetingRoom
    DestinationType.LABORATORIO -> Icons.Filled.Science
    DestinationType.AUDITORIO -> Icons.Filled.TheaterComedy
    DestinationType.OFICINA -> Icons.Filled.Business
}

@Composable
fun DestinationContent(
    state: DestinationUiState,
    onQueryChange: (String) -> Unit,
    onTypeFilter: (DestinationType?) -> Unit,
    onSelect: (String) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.form.query,
                onValueChange = onQueryChange,
                label = { Text("Buscar aula, laboratorio u oficina") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.form.query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Borrar búsqueda")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = state.form.typeFilter == null, onClick = { onTypeFilter(null) }, label = { Text("Todos") }) }
                items(DestinationType.entries) { t ->
                    FilterChip(
                        selected = state.form.typeFilter == t,
                        onClick = { onTypeFilter(t) },
                        label = { Text(t.label()) },
                        leadingIcon = { Icon(t.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }

        if (state.items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.SearchOff,
                title = "Sin resultados",
                message = "No hay destinos que coincidan con \"${state.form.query}\". Probá con otro nombre o quitá el filtro.",
                actionLabel = "Limpiar búsqueda",
                onAction = { onQueryChange(""); onTypeFilter(null) },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(state.items, key = { it.destination.id }) { item ->
                    val d = item.destination
                    val selected = d.id == state.form.selectedId
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .selectable(selected, role = Role.RadioButton) { onSelect(d.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Icon(d.type.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${d.type.label()} · ${d.building} · Piso ${d.floor}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        item.distanceMeters?.let { Text(formatMeters(it), style = MaterialTheme.typography.labelMedium) }
                    }
                    HorizontalDivider()
                }
            }
        }

        // Resumen + confirmación (siempre visible abajo)
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Apartment, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(state.selected?.destination?.name ?: "Elegí un destino", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Acompañados", modifier = Modifier.weight(1f))
                    FilledTonalIconButton(onClick = onDecrement, enabled = state.form.companions > 1) {
                        Icon(Icons.Filled.Remove, contentDescription = "Menos acompañados")
                    }
                    Text(
                        "${state.form.companions}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp).testTag(TestTags.COMPANIONS_VALUE),
                    )
                    FilledTonalIconButton(onClick = onIncrement, enabled = state.form.companions < CommandRules.MAX_COMPANIONS) {
                        Icon(Icons.Filled.Add, contentDescription = "Más acompañados")
                    }
                }
                val reason = state.status?.startBlockedReason
                val error = state.form.error ?: reason
                if (error != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(error, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                Button(
                    onClick = onStart,
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(TestTags.START_BUTTON),
                ) {
                    if (state.form.starting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Enviando al robot…")
                    } else Text("Iniciar guía")
                }
            }
        }
    }
}
