package ar.edu.fguerrero.da1.robot_guia_universitario.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendConfig
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendMode
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.HealthResult
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.ObserveSelectedRobotUseCase
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.RobotRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SettingsRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SimScenario
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.mission.label
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsForm(
    val mode: BackendMode = BackendMode.SIMULATED,
    val baseUrl: String = BackendConfig.DEFAULT_URL,
    val timeoutText: String = BackendConfig.DEFAULT_TIMEOUT.toString(),
    val loaded: Boolean = false,
    val testing: Boolean = false,
    val lastHealth: HealthResult? = null,
    val message: String? = null,
)

data class SettingsUiState(
    val form: SettingsForm = SettingsForm(),
    val saved: BackendConfig = BackendConfig(),
    val robotId: String? = null,
) {
    val urlError: String? get() = validateUrl(form.baseUrl)
    val timeoutError: String? get() = validateTimeout(form.timeoutText)
    val isValid: Boolean get() = form.mode == BackendMode.SIMULATED || (urlError == null && timeoutError == null)
    val draft: BackendConfig get() = BackendConfig(form.mode, form.baseUrl.trim(), form.timeoutText.toIntOrNull() ?: saved.timeoutSeconds)
    val hasUnsavedChanges: Boolean get() = form.loaded && draft != saved

    companion object {
        private val URL_REGEX = Regex("^https?://[A-Za-z0-9.\\-]+(:\\d{1,5})?(/.*)?$")
        fun validateUrl(url: String): String? = when {
            url.isBlank() -> "Ingresá la URL del backend."
            !URL_REGEX.matches(url.trim()) -> "Formato inválido. Ejemplo: http://10.0.2.2:8000/"
            else -> null
        }
        fun validateTimeout(text: String): String? = text.toIntOrNull().let {
            if (it == null || it !in 1..30) "Entre 1 y 30 segundos." else null
        }
    }
}

class SettingsViewModel(
    private val repository: RobotRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val form = MutableStateFlow(SettingsForm())

    val state: StateFlow<SettingsUiState> = combine(form, settings.config, ObserveSelectedRobotUseCase(repository, settings)()) { f, saved, robot ->
        SettingsUiState(f, saved, robot)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch { loadFrom(settings.config.first()) }
    }

    private fun loadFrom(c: BackendConfig) = form.update {
        it.copy(mode = c.mode, baseUrl = c.baseUrl, timeoutText = c.timeoutSeconds.toString(), loaded = true)
    }

    fun onModeChange(mode: BackendMode) = form.update { it.copy(mode = mode) }
    fun onUrlChange(url: String) = form.update { it.copy(baseUrl = url) }
    fun onTimeoutChange(t: String) = form.update { it.copy(timeoutText = t.filter(Char::isDigit).take(2)) }
    fun usePreset(url: String) = form.update { it.copy(baseUrl = url) }

    fun save() {
        val st = state.value
        if (!st.isValid) { form.update { it.copy(message = "Corregí los campos marcados antes de guardar.") }; return }
        viewModelScope.launch {
            settings.saveConfig(st.draft)
            repository.refreshRobots()
            form.update { it.copy(message = "Configuración guardada") }
        }
    }

    /** Guarda y prueba la conexión con GET /health. */
    fun testConnection() {
        val st = state.value
        if (!st.isValid) { form.update { it.copy(message = "Corregí los campos marcados antes de probar.") }; return }
        viewModelScope.launch {
            form.update { it.copy(testing = true) }
            settings.saveConfig(st.draft)
            val result = repository.checkHealth()
            if (result.ok) repository.refreshRobots()
            form.update { it.copy(testing = false, lastHealth = result) }
        }
    }

    fun restoreDefaults() {
        viewModelScope.launch {
            settings.restoreDefaults()
            loadFrom(BackendConfig())
            form.update { it.copy(message = "Valores por defecto restaurados", lastHealth = null) }
        }
    }

    fun trigger(scenario: SimScenario) {
        val robotId = state.value.robotId ?: return
        viewModelScope.launch {
            val r = repository.triggerScenario(robotId, scenario)
            form.update { it.copy(message = r.fold({ "Escenario forzado: ${scenario.label()}" }, { e -> "No se pudo forzar: ${e.message}" })) }
        }
    }

    fun messageShown() = form.update { it.copy(message = null) }
}
