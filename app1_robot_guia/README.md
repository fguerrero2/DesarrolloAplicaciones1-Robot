# Robot guía universitario — App de operador (Unitree G1)

App Android (Kotlin + Jetpack Compose + Material 3) que funciona como HMI/HRI para que un operador de recepción supervise y controle al robot **Unitree G1** mientras guía visitantes, alumnos nuevos e invitados hasta un aula, laboratorio, auditorio u oficina del campus.

Implementa la idea de [`01_robot_guia_universitario.md`](01_robot_guia_universitario.md).

| Entregable | Dónde |
|---|---|
| Wireframes de baja fidelidad + flujo de pantallas y de datos | [`docs/wireframes.html`](docs/wireframes.html) |
| Prototipo de alta fidelidad navegable | [`docs/prototipo_alta_fidelidad.html`](docs/prototipo_alta_fidelidad.html) |
| Capturas de la app real | [`docs/screenshots/`](docs/screenshots/) |
| Plan de pruebas (casos de verificación por cambio) | [`docs/PLAN_DE_PRUEBAS.md`](docs/PLAN_DE_PRUEBAS.md) |
| Heurísticas de Nielsen aplicadas | [`docs/HEURISTICAS_NIELSEN.md`](docs/HEURISTICAS_NIELSEN.md) |

## Flujo del MVP

```text
Seleccionar robot → configurar misión → iniciar → ver progreso → recibir telemetría
→ manejar alerta o contingencia → finalizar o regresar a punto seguro → persistir resultado
```

## Arquitectura

**Clean Architecture + MVVM + Repository + offline-first**, con sólo tres carpetas de primer nivel para que la separación de responsabilidades se lea a simple vista:

```text
app/src/main/java/ar/edu/fguerrero/da1/robot_guia_universitario/
├── AppContainer.kt           Inyección de dependencias manual (+ Application)
├── MainActivity.kt
├── domain/                   Kotlin puro: no conoce Android, Room ni Retrofit
│   ├── Models.kt             RobotTelemetry, MissionRecord, CommandStatus, …
│   ├── Repositories.kt       Interfaces RobotRepository y SettingsRepository
│   ├── CommandRules.kt       Qué comandos se habilitan en cada estado
│   └── UseCases.kt           StartGuideMission, ExecuteCommand, ObserveRobotStatus…
├── data/                     Implementaciones
│   ├── RemoteDataSource.kt   Contrato remoto + DTOs (JSON)
│   ├── RestRobotDataSource.kt  Retrofit/OkHttp + proveedor con URL dinámica
│   ├── SimulatedRobotDataSource.kt  Simulador embebido del G1 y escenarios de falla
│   ├── LocalDatabase.kt      Room: entidades, DAO, base
│   ├── Mappers.kt            DTO → Entity → Dominio
│   ├── DataStoreSettingsRepository.kt
│   ├── OfflineFirstRobotRepository.kt
│   └── CampusCatalog.kt      Destinos del campus (disponibles sin red)
└── ui/                       Compose + ViewModels, una carpeta por pantalla
    ├── AppNavigation.kt, AppViewModel.kt
    ├── common/  theme/
    └── home/ map/ destination/ mission/ alerts/ history/ settings/
```

Dependencias permitidas: `ui → domain ← data`. La UI sólo conoce interfaces del dominio; `AppContainer` decide qué implementación se usa. Por eso los tests reemplazan todo el backend por `MockRobotRepository` sin tocar la UI.

### Offline-first

- La UI **siempre lee Room**. Nunca espera a la red para mostrar algo.
- Mientras alguna pantalla observa la telemetría, el repositorio sondea al robot cada 1 s (un único sondeo compartido) y escribe el resultado en Room.
- Si se corta la conexión se conserva el último dato, se marca `DISCONNECTED`, se genera una alerta local y la UI muestra "Datos desactualizados · hace N s".
- Cada comando se guarda como `PENDING` **antes** de enviarse y luego pasa a `CONFIRMED`, `REJECTED` o `NO_RESPONSE`. El historial queda completo aunque la app se cierre.
- Configuración (modo, URL, timeout, robot elegido) en DataStore.

### Origen de datos

Configuración → Backend permite elegir:

- **Simulador embebido** (por defecto): la app funciona sin red ni backend. Simula guía, batería, desconexión, rechazo de comandos, visitante rezagado, destino inaccesible y ruta bloqueada.
- **Backend REST**: Retrofit contra los endpoints de la sección 7 del plan (`/health`, `/api/v1/robots/{id}/telemetry`, `/api/v1/missions/guide`, …). Emulador: `http://10.0.2.2:8000/`; teléfono: `http://<IP-LAN-de-la-PC>:8000/`.

> El backend actual de `../TPO2_UnitreeG1/backend` sólo implementa `/health`, `/api/v1/robot`, `/move` y `/stop`. "Probar conexión" funciona contra él, pero los endpoints de guía todavía hay que agregarlos al backend; hasta entonces usar el simulador embebido.

### Decisiones

- **Sin Hilt**: DI manual en `AppContainer` (un archivo) para que el cableado sea explícito y el proyecto compile rápido. Migrar a Hilt sólo cambia ese archivo y las factories.
- **Detención operativa** siempre visible, y aclarado en la UI que es por software y no reemplaza la parada de emergencia física.
- Textos en español en el código (TPO); para internacionalizar, moverlos a `strings.xml`.

## Ejecutar

```bash
./gradlew :app:installDebug          # instala en emulador/dispositivo
./gradlew :app:testDebugUnitTest     # 61 tests JVM (dominio, datos, ViewModels)
./gradlew :app:connectedDebugAndroidTest   # 17 tests en dispositivo (Room + UI Compose)
```

Stack: AGP 9.3 (Kotlin integrado) · Kotlin 2.2.10 · Compose BOM 2026.02 · Material 3 · Navigation Compose 2.10 (rutas type-safe) · Room 2.8 + KSP · DataStore · Retrofit 3 + kotlinx.serialization · OkHttp · JUnit4 + coroutines-test + MockWebServer · Compose UI Test.
