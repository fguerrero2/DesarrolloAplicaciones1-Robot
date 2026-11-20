# Heurísticas de Nielsen aplicadas

| # | Heurística | Cómo se aplica | Dónde (código) |
|---|---|---|---|
| 1 | Visibilidad del estado del sistema | Barra de estado persistente en todas las pantallas (robot, estado, batería, conexión, "Actualizado hace N s"). "Última orden vs. estado real" separa lo pedido de lo ejecutado, con `PENDING / CONFIRMED / REJECTED / NO_RESPONSE`. Aviso de datos desactualizados. Badge de alertas pendientes y punto en "Misión" si hay una activa. | `ui/common/Components.kt` (`RobotStatusBar`, `StaleDataBanner`, `CommandStatusPill`), `ui/mission/MissionScreen.kt`, `ui/AppNavigation.kt` |
| 2 | Relación con el mundo real | Vocabulario del operador: aula, laboratorio, piso, acompañados, punto seguro. Mapa con edificios reales del campus y coordenadas en metros. | `data/CampusCatalog.kt`, `ui/map/CampusMap.kt`, `domain/UseCases.kt` (`label()`) |
| 3 | Control y libertad del usuario | Pausar ↔ Reanudar, Cancelar, Regresar a punto seguro, Detención operativa. Botón "Volver" en pantallas secundarias. "Restaurar valores por defecto". | `MissionScreen.kt`, `SettingsScreen.kt` |
| 4 | Consistencia y estándares | Material 3 en toda la app. Detención operativa con el mismo color, texto y posición. Estado siempre con ícono + texto + color (no sólo color). | `ui/theme/`, `OperationalStopButton`, `StatusPill` |
| 5 | Prevención de errores | Los botones incompatibles con el estado se deshabilitan (`CommandRules`). Confirmación antes de cancelar o abortar una misión. Acompañados acotados a 1–8 con stepper. No se pueden iniciar dos misiones. Validación de URL antes de guardar. | `domain/CommandRules.kt`, `ConfirmDialog`, `DestinationScreen.kt`, `SettingsViewModel.kt` |
| 6 | Reconocer antes que recordar | Lista de destinos con tipo, edificio, piso y distancia. Resumen fijo del destino y acompañados antes de "Iniciar guía". Presets de URL (Emulador, Red local). | `DestinationScreen.kt`, `SettingsScreen.kt` |
| 7 | Flexibilidad y eficiencia | Iniciar misión desde Inicio o desde el Mapa ("Guiar hasta aquí" preselecciona). Filtros por tipo y búsqueda que ignora tildes. | `MapScreen.kt`, `DestinationViewModel.kt` |
| 8 | Estética y diseño minimalista | Una acción principal por pantalla. Detalle del historial plegado. Las alertas informativas llegan ya reconocidas, así el contador sólo muestra lo accionable. Los controles de misión se ocultan cuando no hay misión. | `HistoryScreen.kt`, `OfflineFirstRobotRepository.kt`, `MissionScreen.kt` |
| 9 | Ayudar a reconocer, diagnosticar y recuperarse de errores | Cada incidente muestra qué hacer (rezagado, ruta bloqueada, destino inaccesible, batería, desconexión, rechazo). El motivo de bloqueo de "Nueva misión" se muestra escrito. REJECTED y NO_RESPONSE llevan el mensaje del backend o del timeout. | `recoveryHint()` en `MissionScreen.kt`, `CommandRules.startBlockedReason` |
| 10 | Ayuda y documentación | Textos de ayuda contextual (qué IP usar, qué hace cada modo). Aclaración de seguridad: la detención es por software y no reemplaza la parada física. | `SettingsScreen.kt`, `OperationalStopButton` |

## Ajustes surgidos al probar la app en el emulador

Al recorrer el flujo real aparecieron problemas que no se veían en el prototipo, y se corrigieron:

1. **Nombre del robot cortado en la barra de estado** (H1): las píldoras ocupaban todo el ancho. Se pasó a dos líneas: nombre + conexión arriba, antigüedad + estado + batería abajo.
2. **Robot por defecto inesperado** (H2): al ordenar por nombre quedaba elegido "Edificio B" en lugar de "Recepción". Ahora se ordena por id.
3. **Botones deshabilitados sin misión** (H8): con la misión completada seguían visibles Pausar/Cancelar grises. Ahora sólo aparecen con una misión activa.
4. **Alertas informativas que pedían "Reconocer"** (H8): inflaban el contador de pendientes. Ahora llegan reconocidas.
5. **Etiquetas del mapa fuera del borde** (H4): las del lado derecho se dibujan a la izquierda del pin.
6. **Misión demasiado rápida para demostrar incidentes**: la velocidad simulada bajó de 0,8 a 0,5 m/s (paso de visitante) y la ETA usa la misma constante.
