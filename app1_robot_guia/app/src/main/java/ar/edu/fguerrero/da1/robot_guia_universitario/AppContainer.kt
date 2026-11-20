package ar.edu.fguerrero.da1.robot_guia_universitario

import android.app.Application
import android.content.Context
import ar.edu.fguerrero.da1.robot_guia_universitario.data.DataStoreSettingsRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.data.OfflineFirstRobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.data.RemoteDataSourceProvider
import ar.edu.fguerrero.da1.robot_guia_universitario.data.RobotDatabase
import ar.edu.fguerrero.da1.robot_guia_universitario.data.SimulatedRobotDataSource
import ar.edu.fguerrero.da1.robot_guia_universitario.data.settingsDataStore
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Inyección de dependencias manual (sin Hilt para mantener el proyecto simple y explícito).
 * Es el ÚNICO lugar donde se elige qué implementación concreta usa la app.
 * En los tests se reemplaza por MockRobotRepository / MockSettingsRepository.
 */
class AppContainer(context: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = RobotDatabase.build(context)

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context.settingsDataStore)

    val robotRepository: RobotRepository = OfflineFirstRobotRepository(
        dao = database.dao(),
        settings = settingsRepository,
        remoteProvider = RemoteDataSourceProvider(simulator = SimulatedRobotDataSource()),
        scope = appScope,
    )
}

class RobotGuiaApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
