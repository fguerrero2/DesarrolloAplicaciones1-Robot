package ar.edu.fguerrero.da1.robot_guia_universitario.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.edu.fguerrero.da1.robot_guia_universitario.AppContainer
import ar.edu.fguerrero.da1.robot_guia_universitario.RobotGuiaApplication
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.AppViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.alerts.AlertsViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.destination.DestinationViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.history.HistoryViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.home.HomeViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.map.MapViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.mission.MissionViewModel
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.settings.SettingsViewModel

/** Fábrica de ViewModels: toma las dependencias del AppContainer. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { AppViewModel(container().robotRepository, container().settingsRepository) }
        initializer { HomeViewModel(container().robotRepository, container().settingsRepository) }
        initializer { MapViewModel(container().robotRepository, container().settingsRepository) }
        initializer { DestinationViewModel(container().robotRepository, container().settingsRepository) }
        initializer { MissionViewModel(container().robotRepository, container().settingsRepository) }
        initializer { AlertsViewModel(container().robotRepository, container().settingsRepository) }
        initializer { HistoryViewModel(container().robotRepository) }
        initializer { SettingsViewModel(container().robotRepository, container().settingsRepository) }
    }
}

private fun CreationExtras.container(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RobotGuiaApplication).container

/** Snackbar compartido para que cada pantalla informe resultados de acciones. */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }
