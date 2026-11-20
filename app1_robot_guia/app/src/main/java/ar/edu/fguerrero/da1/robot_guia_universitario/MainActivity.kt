package ar.edu.fguerrero.da1.robot_guia_universitario

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.BackendMode
import ar.edu.fguerrero.da1.robot_guia_universitario.domain.SettingsRepository
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.RobotGuiaApp
import ar.edu.fguerrero.da1.robot_guia_universitario.ui.theme.RobotGuiaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = (application as RobotGuiaApplication).container.settingsRepository
        setContent {
            RobotGuiaTheme {
                LocalNetworkPermissionRequest(settings)
                RobotGuiaApp()
            }
        }
    }
}

private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
private const val API_LOCAL_NETWORK_PROTECTION = 37 // Android 17

/**
 * Android 17+ bloquea las conexiones a IPs privadas (10.0.2.2, 192.168.x.x) sin el permiso
 * runtime ACCESS_LOCAL_NETWORK: el backend REST queda "Sin conexión" aunque la red funcione.
 * Se pide al abrir la app en modo REST y al guardar ese modo en Configuración.
 */
@Composable
private fun LocalNetworkPermissionRequest(settings: SettingsRepository) {
    if (Build.VERSION.SDK_INT < API_LOCAL_NETWORK_PROTECTION) return
    val context = LocalContext.current
    val config by settings.config.collectAsStateWithLifecycle(initialValue = null)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(config?.mode) {
        val granted = ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK) ==
            PackageManager.PERMISSION_GRANTED
        if (config?.mode == BackendMode.REST && !granted) launcher.launch(ACCESS_LOCAL_NETWORK)
    }
}
