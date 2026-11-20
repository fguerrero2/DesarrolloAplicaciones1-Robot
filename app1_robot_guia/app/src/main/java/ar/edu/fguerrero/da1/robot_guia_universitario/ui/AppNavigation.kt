package ar.edu.fguerrero.da1.robot_guia_universitario.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.alerts.AlertsScreen
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.AppViewModelProvider
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.LocalSnackbarHostState
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.RobotStatusBar
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.common.rememberNow
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.destination.DestinationScreen
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.history.HistoryScreen
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.home.HomeScreen
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.map.MapScreen
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.mission.MissionScreen
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

// Rutas type-safe de Navigation Compose
@Serializable object HomeRoute
@Serializable object MapRoute
@Serializable data class DestinationRoute(val preselectedId: String? = null)
@Serializable object MissionRoute
@Serializable object AlertsRoute
@Serializable object HistoryRoute
@Serializable object SettingsRoute

private data class TopLevel(val label: String, val icon: ImageVector, val route: Any, val routeClass: KClass<*>)

private val topLevel = listOf(
    TopLevel("Inicio", Icons.Filled.Home, HomeRoute, HomeRoute::class),
    TopLevel("Mapa", Icons.Filled.Map, MapRoute, MapRoute::class),
    TopLevel("Misión", Icons.Filled.Flag, MissionRoute, MissionRoute::class),
    TopLevel("Alertas", Icons.Filled.Notifications, AlertsRoute, AlertsRoute::class),
    TopLevel("Historial", Icons.Filled.History, HistoryRoute, HistoryRoute::class),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RobotGuiaApp(appViewModel: AppViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val nav = rememberNavController()
    val appState by appViewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination

    val title = when {
        current?.hasRoute(DestinationRoute::class) == true -> "Seleccionar destino"
        current?.hasRoute(SettingsRoute::class) == true -> "Configuración"
        else -> topLevel.firstOrNull { current?.hasRoute(it.routeClass) == true }?.label ?: "Robot guía"
    }
    val isTopLevel = topLevel.any { current?.hasRoute(it.routeClass) == true }

    fun goTo(route: Any) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    CompositionLocalProvider(LocalSnackbarHostState provides snackbar) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            if (!isTopLevel && current != null) IconButton(onClick = { nav.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                            }
                        },
                        actions = {
                            IconButton(onClick = { nav.navigate(SettingsRoute) { launchSingleTop = true } }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Configuración")
                            }
                        },
                    )
                    RobotStatusBar(appState.robotName, appState.telemetry, rememberNow()) { goTo(HomeRoute) }
                }
            },
            bottomBar = {
                NavigationBar {
                    topLevel.forEach { item ->
                        NavigationBarItem(
                            selected = current?.hasRoute(item.routeClass) == true,
                            onClick = { goTo(item.route) },
                            label = { Text(item.label) },
                            icon = {
                                when {
                                    item.route == AlertsRoute && appState.unacknowledgedAlerts > 0 ->
                                        BadgedBox(badge = { Badge { Text("${appState.unacknowledgedAlerts}") } }) { Icon(item.icon, null) }
                                    item.route == MissionRoute && appState.hasActiveMission ->
                                        BadgedBox(badge = { Badge() }) { Icon(item.icon, null) }
                                    else -> Icon(item.icon, null)
                                }
                            },
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
                composable<HomeRoute> {
                    HomeScreen(
                        onNewMission = { nav.navigate(DestinationRoute()) },
                        onOpenMission = { goTo(MissionRoute) },
                        onOpenSettings = { nav.navigate(SettingsRoute) },
                    )
                }
                composable<MapRoute> { MapScreen(onGuideTo = { nav.navigate(DestinationRoute(it)) }) }
                composable<DestinationRoute> { entry ->
                    DestinationScreen(
                        preselectedId = entry.toRoute<DestinationRoute>().preselectedId,
                        onMissionStarted = {
                            nav.navigate(MissionRoute) { popUpTo(HomeRoute); launchSingleTop = true }
                        },
                    )
                }
                composable<MissionRoute> { MissionScreen(onNewMission = { nav.navigate(DestinationRoute()) }) }
                composable<AlertsRoute> { AlertsScreen() }
                composable<HistoryRoute> { HistoryScreen() }
                composable<SettingsRoute> { SettingsScreen() }
            }
        }
    }
}
