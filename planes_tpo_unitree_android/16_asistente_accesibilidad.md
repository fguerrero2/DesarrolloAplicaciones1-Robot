# Plan de desarrollo e implementación — Asistente para personas con movilidad reducida

## 1. Objetivo

Desarrollar una aplicación Android para **orientar hacia rutas, ascensores, rampas y servicios accesibles.** usando como plataforma robótica principal **Unitree G1**.

La aplicación funcionará como HMI/HRI para supervisar el robot, iniciar o controlar misiones, visualizar telemetría, gestionar alertas y consultar historial. El robot podrá ser simulado por un backend REST local o remoto.

## 2. Problema y caso de uso

**Problema:** Orientar hacia rutas, ascensores, rampas y servicios accesibles.

**Actores:** Operador, personal de asistencia, usuario.

**Misión principal:** Guiar por una ruta accesible previamente configurada.

El objetivo del MVP es demostrar el flujo completo:

```text
Seleccionar robot
→ configurar misión
→ iniciar
→ visualizar progreso
→ recibir telemetría
→ manejar alerta o contingencia
→ finalizar o regresar a punto seguro
→ persistir resultado
```

## 3. Alcance del MVP

El MVP debe incluir:

1. Selección de robot.
2. Conexión configurable al backend REST.
3. Dashboard de estado.
4. Creación o selección de misión.
5. Inicio, pausa, reanudación y cancelación.
6. Solicitud de regreso a punto seguro.
7. Detención operativa simulada.
8. Telemetría actualizada periódicamente.
9. Alertas con reconocimiento.
10. Persistencia local de historial.
11. Simulación de batería baja, desconexión, rechazo de comandos e incidente propio.

## 4. Telemetría

Telemetría común:

- conectividad;
- batería;
- estado operativo;
- misión y progreso;
- ubicación `(x, y, yaw)`;
- movimiento `(vx, vy, omega)`;
- alertas;
- última actualización.

Variables específicas del servicio:

- Destino accesible.
- Distancia restante.
- Estado de accesibilidad de ruta.

## 5. Escenarios de falla propios

Además de batería baja y desconexión:

- Ascensor fuera de servicio.
- Ruta accesible bloqueada.
- Usuario deja de seguir.

La app debe diferenciar claramente:

- comando enviado;
- comando confirmado;
- comando rechazado;
- comando sin respuesta;
- orden solicitada versus acción realmente ejecutada.

## Arquitectura propuesta

Se recomienda una arquitectura Android basada en **Kotlin + Jetpack Compose + MVVM**, separando presentación, dominio y datos.

```text
UI Compose
   ↓ eventos
ViewModel
   ↓
UseCases / lógica de dominio
   ↓
Repository
   ├── RemoteDataSource → Retrofit/OkHttp → API REST
   └── LocalDataSource  → Room
```

### Tecnologías sugeridas

- **Android Studio** actualizado.
- **Kotlin**.
- **Jetpack Compose** para UI.
- **Navigation Compose** para navegación.
- **MVVM** con `ViewModel`.
- **StateFlow` / `Flow`** para estado reactivo.
- **Retrofit 2 + OkHttp** para REST.
- **kotlinx.serialization** o Moshi para JSON.
- **Room** para historial de misiones, alertas y eventos.
- **DataStore Preferences** para configuración del backend.
- **Hilt** para inyección de dependencias.
- **Coroutines** para operaciones asíncronas.
- **WorkManager** solo si se necesita reintento o sincronización diferida.
- **JUnit + MockK** para pruebas unitarias.
- **Compose UI Test** para pruebas de interfaz.

## Backend REST configurable

La app no debe depender de una URL fija. Se recomienda almacenar la URL base mediante `DataStore`.

Ejemplos de URL:

```text
Emulador Android:
http://10.0.2.2:8000/

Dispositivo físico en la misma red:
http://192.168.1.100:8000/

Backend Docker local de referencia:
imágenes: `tpo2_unitreeg1-backend:latest` y `tpo2_unitreeg1-simulator:latest`
puerto de la API publicado: 8000

Desde `TPO2_UnitreeG1`, iniciar con Docker Compose:

```bash
docker compose up --build
```

La configuración detallada y las URL por dispositivo están en [README.md](README.md).

La app debe ofrecer una pantalla **Configuración → Backend** con:

- URL base.
- Timeout.
- Botón "Probar conexión".
- Indicador Conectado / Desconectado.
- Última respuesta.
- Posibilidad de restaurar valores por defecto.

> `10.0.2.2` permite al emulador Android acceder al host. En un teléfono físico conectado a la misma red, usar la IP LAN de la PC que ejecuta Docker y el puerto `8000`.

## Contratos REST comunes

Modelo mínimo sugerido:

```json
{
  "robotId": "g1-01",
  "connection": "CONNECTED",
  "battery": 78,
  "operationalState": "IDLE",
  "missionId": null,
  "missionProgress": 0,
  "position": {
    "x": 1.2,
    "y": 2.5,
    "yaw": 0.4
  },
  "velocity": {
    "vx": 0.0,
    "vy": 0.0,
    "omega": 0.0
  },
  "alerts": [],
  "updatedAt": "2026-11-16T12:00:00Z"
}
```

Respuesta de comando:

```json
{
  "commandId": "cmd-1021",
  "status": "CONFIRMED",
  "message": "Command accepted",
  "timestamp": "2026-11-16T12:00:02Z"
}
```

Estados de comando recomendados:

```text
PENDING
CONFIRMED
REJECTED
NO_RESPONSE
```

## Estructura de proyecto Android

```text
app/
├── core/
│   ├── network/
│   ├── database/
│   ├── config/
│   └── ui/
├── data/
│   ├── remote/
│   ├── local/
│   ├── mapper/
│   └── repository/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── presentation/
│   ├── dashboard/
│   ├── missions/
│   ├── telemetry/
│   ├── alerts/
│   ├── history/
│   └── settings/
└── MainActivity.kt
```


## 6. Pantallas sugeridas

- Destinos accesibles.
- Mapa.
- Guía activa.
- Estado de ruta.
- Alertas.
- Historial.

Además debe existir una pantalla de **Configuración de backend**.

## 7. Endpoints REST sugeridos

| Método | Endpoint | Uso |
|---|---|---|
| `GET` | `/api/v1/accessibility/destinations` | Destinos |
| `GET` | `/api/v1/accessibility/routes` | Rutas |
| `POST` | `/api/v1/missions/guide` | Iniciar guía |
| `GET` | `/api/v1/robots/{id}/telemetry` | Telemetría |
| `POST` | `/api/v1/missions/{id}/pause` | Pausar |
| `POST` | `/api/v1/missions/{id}/resume` | Reanudar |
| `POST` | `/api/v1/missions/{id}/cancel` | Cancelar |
| `POST` | `/api/v1/alerts/{id}/ack` | Reconocer |

Endpoints comunes adicionales:

| Método | Endpoint | Uso |
|---|---|---|
| `GET` | `/health` | Probar conectividad |
| `GET` | `/api/v1/robots` | Robots disponibles |
| `GET` | `/api/v1/robots/{id}/telemetry` | Estado actual |
| `POST` | `/api/v1/robots/{id}/stop` | Detención operativa simulada |
| `POST` | `/api/v1/robots/{id}/return-safe` | Regreso a punto seguro |
| `GET` | `/api/v1/alerts` | Alertas |
| `POST` | `/api/v1/alerts/{id}/ack` | Reconocer alerta |

## 8. Frecuencia de actualización

Para un TPO no es necesario implementar streaming complejo.

Sugerencia:

- telemetría: `GET` cada 1 segundo;
- lista de alertas: cada 2 segundos;
- estado de misión: cada 1 segundo;
- historial: bajo demanda.

Puede implementarse con una `Coroutine` dentro del Repository y exponerse como `Flow`.

## 9. Modelos Android mínimos

```kotlin
data class RobotTelemetry(
    val robotId: String,
    val connection: ConnectionState,
    val battery: Int,
    val operationalState: OperationalState,
    val missionId: String?,
    val missionProgress: Int,
    val position: Position,
    val velocity: Velocity,
    val alerts: List<RobotAlert>,
    val updatedAt: String
)

data class Position(
    val x: Double,
    val y: Double,
    val yaw: Double
)

data class CommandResponse(
    val commandId: String,
    val status: CommandStatus,
    val message: String,
    val timestamp: String
)
```

Agregar modelos específicos del servicio para:

destino accesible, distancia restante, estado de accesibilidad de ruta.

## 10. Repository

Interfaz recomendada:

```kotlin
interface RobotRepository {
    fun observeTelemetry(robotId: String): Flow<RobotTelemetry>
    suspend fun startMission(request: MissionRequest): Result<CommandResponse>
    suspend fun pauseMission(missionId: String): Result<CommandResponse>
    suspend fun resumeMission(missionId: String): Result<CommandResponse>
    suspend fun cancelMission(missionId: String): Result<CommandResponse>
    suspend fun returnToSafePoint(robotId: String): Result<CommandResponse>
    suspend fun operationalStop(robotId: String): Result<CommandResponse>
    suspend fun acknowledgeAlert(alertId: String): Result<CommandResponse>
}
```

Esto permite reemplazar el backend por otro desarrollo sin modificar la UI.

## 11. Configuración Retrofit dinámica

No conviene recrear Retrofit en cada request. Crear un proveedor de cliente que observe la URL guardada en `DataStore` y reconstruya el cliente cuando el usuario confirme el cambio.

Ejemplo conceptual:

```kotlin
interface RobotApi {
    @GET("health")
    suspend fun health(): HealthResponse

    @GET("api/v1/robots/{id}/telemetry")
    suspend fun telemetry(@Path("id") id: String): RobotTelemetryDto
}
```

## 12. Docker de referencia

Usar `../TPO2_UnitreeG1/docker-compose.yml` como configuración ejecutable. El servicio `backend` comparte la red del `simulator`; la API se publica en el puerto `8000` y el visor noVNC en `6080`. La comprobación de salud real es `GET /health`.

Desde Android Emulator, configurar la URL base `http://10.0.2.2:8000/`. En un teléfono físico, usar `http://<IP-LAN-de-la-PC>:8000/`. Consultar [README.md](README.md) para el ejemplo de conexión completo.

## 13. Persistencia local

Guardar con Room:

- misión;
- fecha;
- robot;
- estado final;
- duración;
- alertas;
- comandos enviados;
- respuesta de cada comando.

Tablas sugeridas:

```text
missions
mission_events
alerts
commands
```

## 14. Seguridad y UX

Implementar:

- confirmación antes de cancelar una misión;
- botón de detención operativa destacado;
- no confundir "detención software" con parada de emergencia física;
- deshabilitar botones incompatibles con el estado;
- mostrar telemetría antigua si se pierde conexión;
- marcar claramente `REJECTED` y `NO_RESPONSE`;
- impedir iniciar dos misiones simultáneas;
- mantener visible el estado del robot.

## 15. Plan de implementación

### Fase 1 — Proyecto base

- crear proyecto Android Studio;
- configurar Compose;
- agregar Hilt, Retrofit, Room y DataStore;
- crear navegación;
- crear tema y componentes reutilizables.

### Fase 2 — Backend

- definir contratos JSON;
- iniciar backend y simulador con Docker Compose;
- implementar `/health`;
- implementar telemetría simulada;
- implementar comandos;
- implementar generador de alertas.

### Fase 3 — Capa de datos

- DTOs;
- Retrofit;
- `RemoteDataSource`;
- Room;
- Repository;
- configuración dinámica de URL.

### Fase 4 — Dominio

- modelos;
- validaciones;
- estados de misión;
- reglas de habilitación de comandos;
- casos de uso.

### Fase 5 — UI

Construir las pantallas:

- Destinos accesibles.
- Mapa.
- Guía activa.
- Estado de ruta.
- Alertas.
- Historial.

### Fase 6 — Simulación de escenarios

Implementar botones de desarrollo o endpoints que permitan forzar:

```text
batería baja
desconexión
rechazo de comando
obstáculo/incidente
recuperación
```

### Fase 7 — Persistencia y pruebas

- guardar historial;
- pruebas unitarias del ViewModel;
- pruebas del Repository con MockWebServer;
- pruebas Compose del flujo normal y de error.

### Fase 8 — Presentación

Preparar demostración:

1. conectar al backend;
2. seleccionar robot;
3. iniciar una misión;
4. visualizar telemetría;
5. forzar un incidente;
6. resolverlo;
7. finalizar;
8. mostrar historial.

## 16. Criterios de aceptación

El proyecto se considera funcional cuando:

- APK inicia sin backend;
- permite configurar URL;
- informa estado de conexión;
- puede conectarse al backend en la URL configurada;
- obtiene telemetría REST;
- ejecuta comandos;
- muestra estados `PENDING`, `CONFIRMED`, `REJECTED`, `NO_RESPONSE`;
- persiste historial;
- demuestra misión normal;
- demuestra batería baja;
- demuestra desconexión;
- demuestra incidente;
- demuestra escenario propio.

## 17. Extensiones opcionales

- WebSocket para telemetría en tiempo real.
- Mapa 2D del área.
- Representación de posición `(x,y)`.
- Modo oscuro.
- Exportación de historial.
- Autenticación.
- Soporte de múltiples robots.
- Integración futura con SDK2 real mediante el backend.
