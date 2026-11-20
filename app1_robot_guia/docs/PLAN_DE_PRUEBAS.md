# Plan de pruebas — Robot guía universitario

## Dobles de prueba

`app/src/sharedTest/java/.../MockRepositories.kt` se comparte entre tests JVM (`src/test`) e instrumentados (`src/androidTest`) mediante `sourceSets` en `app/build.gradle.kts`.

- **`MockRobotRepository`**: implementa `RobotRepository` en memoria. Se le fija el estado (`telemetry`, `activeMission`, `alerts`, …), se elige con qué responde el próximo comando (`nextStatus = REJECTED / NO_RESPONSE / CONFIRMED`) y registra lo recibido (`calls`, `scenarios`, `healthChecks`).
- **`MockSettingsRepository`**: configuración y robot seleccionado en memoria.
- **`sampleTelemetry()` / `sampleMission()`**: fábricas de datos de ejemplo.
- `SimulatedRobotDataSource(latencyMs = 0)` + Room en memoria para probar el repositorio real.
- `MockWebServer` para probar el cliente Retrofit sin backend.

## Qué correr según qué se cambió

Regla general: **todo cambio debe dejar verde `./gradlew :app:testDebugUnitTest`** (≈15 s). Además:

| Si cambiaste… | Corré además | Casos que lo cubren |
|---|---|---|
| `domain/CommandRules.kt` | `--tests "*CommandRulesTest"` | CP-DOM-01..12 |
| `domain/UseCases.kt` | `--tests "*UseCasesTest" --tests "*ViewModelsTest"` | CP-DOM-13..21, CP-VM |
| `domain/Models.kt` | todos los JVM + `connectedDebugAndroidTest` | todos |
| `data/SimulatedRobotDataSource.kt` | `--tests "*SimulatedRobotDataSourceTest"` + `OfflineFirstRobotRepositoryTest` | CP-SIM, CP-REPO |
| `data/RestRobotDataSource.kt`, DTOs | `--tests "*RestRobotDataSourceTest" --tests "*MappersTest"` | CP-REST, CP-MAP |
| `data/Mappers.kt` | `--tests "*MappersTest"` | CP-MAP |
| `data/LocalDatabase.kt`, `OfflineFirstRobotRepository.kt` | `connectedDebugAndroidTest` (Room real) | CP-REPO |
| Un `*ViewModel.kt` | `--tests "*ViewModelsTest"` | CP-VM |
| Una pantalla Compose (`*Screen.kt`, `Components.kt`) | `connectedDebugAndroidTest` + verificación manual (abajo) | CP-UI, CP-MAN |

Comandos:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests "*CommandRulesTest"
./gradlew :app:connectedDebugAndroidTest          # requiere emulador o dispositivo
```

## Casos de prueba automatizados

### Dominio — `CommandRulesTest`, `UseCasesTest` (JVM)

| ID | Caso | Resultado esperado |
|---|---|---|
| CP-DOM-01 | Sin telemetría | No hay comandos habilitados |
| CP-DOM-02 | Robot IDLE | Se habilita Iniciar y Detener; no Pausar |
| CP-DOM-03 | Robot en punto seguro | No se ofrece "Regresar a punto seguro" |
| CP-DOM-04 | GUIDING con misión | Pausar, Cancelar, Regresar, Detener |
| CP-DOM-05 | PAUSED | Reanudar en lugar de Pausar |
| CP-DOM-06 | Misión activa | No se puede iniciar otra (no hay misiones simultáneas) |
| CP-DOM-07 | Batería < 20 % | Iniciar bloqueado con motivo |
| CP-DOM-08 | Desconectado | Sólo Detención operativa |
| CP-DOM-09 | Comando pendiente | Sólo Detención operativa |
| CP-DOM-10 | STOPPED | No repite Detener; permite Reanudar |
| CP-DOM-11 | RETURNING | Sólo Detener |
| CP-DOM-12 | Sin bloqueos | Motivo de bloqueo nulo |
| CP-DOM-13 | Iniciar misión válida | Delega en el repositorio, CONFIRMED |
| CP-DOM-14 | Iniciar con misión activa | ValidationException, no llama al repo |
| CP-DOM-15 | Acompañados 0 o > 8 | Inválido |
| CP-DOM-16 | Iniciar desconectado | Inválido |
| CP-DOM-17 | Comando no disponible | No se envía |
| CP-DOM-18 | Comando de misión sin misión | Falla |
| CP-DOM-19 | Comando disponible | Se enruta al método correcto |
| CP-DOM-20 | ObserveRobotStatus | Combina telemetría, misión y reglas |
| CP-DOM-21 | PENDING reciente vs. viejo (> 30 s) | El viejo no bloquea los controles |

### Datos (JVM)

| ID | Caso | Resultado esperado |
|---|---|---|
| CP-SIM-01 | Estado inicial | IDLE, CONNECTED, `/health` ok, 2 robots |
| CP-SIM-02 | Misión de guía | Avanza hasta ARRIVED, progreso 100, distancia 0 |
| CP-SIM-03 | Dos misiones | La segunda es REJECTED |
| CP-SIM-04 | Pausar/Reanudar | Cambia el estado; pausar dos veces → REJECTED |
| CP-SIM-05 | Rechazo forzado | Sólo afecta al próximo comando |
| CP-SIM-06 | Desconexión / recuperar | IOException; luego CONNECTED |
| CP-SIM-07 | Batería baja | Alerta CRITICAL y no se puede iniciar |
| CP-SIM-08 | Visitante rezagado | WAITING hasta recuperar, luego GUIDING |
| CP-SIM-09 | Regresar a punto seguro | Termina IDLE en recepción, sin misión |
| CP-SIM-10 | Reconocer alerta | Queda reconocida |
| CP-MAP-01..04 | Mappers | Enum desconocido → default; fecha inválida → fallback; batería acotada 0–100; status desconocido → NO_RESPONSE |
| CP-REST-01 | GET /health | Ruta correcta, parsea |
| CP-REST-02 | Telemetría del contrato del plan | Parsea e ignora campos nuevos |
| CP-REST-03 | POST pausa | Método y ruta correctos |
| CP-REST-04 | HTTP 409 | `RemoteRejectedException` (→ REJECTED) |
| CP-REST-05 | Proveedor dinámico | Reutiliza Retrofit si no cambia la URL; lo recrea si cambia |

### ViewModels — `ViewModelsTest` (JVM, con MockRobotRepository)

| ID | Caso |
|---|---|
| CP-VM-01..04 | Inicio: lista y robot elegido; cambio de robot persistido; STOP informa resultado; error al refrescar |
| CP-VM-05..10 | Destino: búsqueda sin tildes; acompañados 1–8; sin selección no inicia; misión confirmada navega; rechazada muestra motivo; sin conexión bloquea |
| CP-VM-11..16 | Misión: pausar→reanudar; NO_RESPONSE no cambia el estado real; cancelar va al historial; comando no habilitado no se envía; forzar escenario; ETA |
| CP-VM-17 | Alertas: reconocer y filtrar pendientes |
| CP-VM-18 | Historial: expandir/colapsar |
| CP-VM-19..21 | Configuración: URL inválida no se guarda; probar conexión guarda y llama a /health; restaurar valores por defecto |

### Repositorio offline-first — `OfflineFirstRobotRepositoryTest` (dispositivo, Room en memoria + simulador)

| ID | Caso |
|---|---|
| CP-REPO-01 | Refrescar robots los guarda en Room |
| CP-REPO-02 | Iniciar misión persiste comando CONFIRMED, misión y eventos |
| CP-REPO-03 | No se permiten dos misiones simultáneas |
| CP-REPO-04 | Llegar a destino completa la misión en el historial |
| CP-REPO-05 | Desconexión conserva el último dato y genera alerta |
| CP-REPO-06 | Comando sin conexión queda NO_RESPONSE |
| CP-REPO-07 | Comando rechazado queda REJECTED con alerta y sin misión |
| CP-REPO-08 | Cancelar → CANCELLED; regresar → RETURNED_SAFE |
| CP-REPO-09 | Alerta local se reconoce sin red |

### UI Compose — `ScreensUiTest` (dispositivo, pantallas + ViewModels reales + MockRobotRepository)

| ID | Caso |
|---|---|
| CP-UI-01 | Inicio muestra el robot; Nueva misión y Detención habilitadas |
| CP-UI-02 | Sin conexión: aviso de datos desactualizados, inicio bloqueado, Detención disponible |
| CP-UI-03 | Cancelar pide confirmación y recién después envía el comando |
| CP-UI-04 | Pausar cambia el botón a Reanudar y muestra el estado del comando |
| CP-UI-05 | Incidente con guía de recuperación |
| CP-UI-06 | Stepper de acompañados + iniciar navega |
| CP-UI-07 | Búsqueda sin resultados ofrece limpiar |
| CP-UI-08 | Configuración valida la URL y prueba la conexión |

## Verificación manual (demo, criterios de aceptación de la sección 16)

Con el simulador embebido, en emulador:

| ID | Pasos | Esperado |
|---|---|---|
| CP-MAN-01 | Abrir la app sin backend | Inicia, muestra robots y telemetría |
| CP-MAN-02 | Nueva misión → Aula 204 → 2 acompañados → Iniciar | Navega a Misión, progreso avanza, mapa con ruta |
| CP-MAN-03 | Simular incidente → Visitante rezagado | Estado "Esperando (incidente)", tarjeta con qué hacer, badge en Alertas |
| CP-MAN-04 | Alertas → Reconocer; Configuración → Recuperar | Misión continúa |
| CP-MAN-05 | Configuración → Rechazar próximo comando; Pausar | "Rechazado" en Última orden; estado real sigue Guiando |
| CP-MAN-06 | Configuración → Desconexión; volver a Inicio | "Desconectado", "Datos desactualizados · hace N s", inicio bloqueado |
| CP-MAN-07 | Con desconexión, Detención operativa | Comando NO_RESPONSE registrado |
| CP-MAN-08 | Batería baja | Alerta crítica; Nueva misión bloqueada con motivo |
| CP-MAN-09 | Cancelar misión (confirmar) / Regresar a punto seguro | Historial con estado final, duración, comandos y eventos |
| CP-MAN-10 | Cerrar y reabrir la app sin red | Historial y último estado siguen visibles |
| CP-MAN-11 | Configuración → Backend REST, URL inválida | Error en línea; Guardar deshabilitado |
| CP-MAN-12 | Backend Docker levantado, URL `http://10.0.2.2:8000/`, Probar conexión | "Conectado", latencia y respuesta de `/health` |

Capturas de esta verificación en [`screenshots/`](screenshots/).
