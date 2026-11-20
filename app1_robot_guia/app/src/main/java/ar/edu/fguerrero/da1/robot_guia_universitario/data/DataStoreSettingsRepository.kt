package ar.edu.fguerrero.da1.robot_guia_universitario.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendConfig
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendMode
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Configuración del backend (URL, timeout, modo) y robot seleccionado, persistidos con DataStore. */
class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private object Keys {
        val MODE = stringPreferencesKey("backend_mode")
        val URL = stringPreferencesKey("base_url")
        val TIMEOUT = intPreferencesKey("timeout_seconds")
        val ROBOT = stringPreferencesKey("selected_robot")
    }

    override val config: Flow<BackendConfig> = store.data.map { p ->
        BackendConfig(
            mode = safeEnum(p[Keys.MODE], BackendMode.SIMULATED),
            baseUrl = p[Keys.URL] ?: BackendConfig.DEFAULT_URL,
            timeoutSeconds = p[Keys.TIMEOUT] ?: BackendConfig.DEFAULT_TIMEOUT,
        )
    }

    override val selectedRobotId: Flow<String?> = store.data.map { it[Keys.ROBOT] }

    override suspend fun saveConfig(config: BackendConfig) {
        store.edit {
            it[Keys.MODE] = config.mode.name
            it[Keys.URL] = config.baseUrl
            it[Keys.TIMEOUT] = config.timeoutSeconds
        }
    }

    override suspend fun restoreDefaults() {
        store.edit { it.remove(Keys.MODE); it.remove(Keys.URL); it.remove(Keys.TIMEOUT) }
    }

    override suspend fun selectRobot(robotId: String) {
        store.edit { it[Keys.ROBOT] = robotId }
    }
}
