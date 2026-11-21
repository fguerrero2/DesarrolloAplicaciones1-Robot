# appdemo1 - G1 Inspecciones

App Android nativa (Kotlin + Jetpack Compose) para ejecutar una ronda de inspeccion, visualizar los puntos sobre un plano local y consultar el estado del robot desde la API REST.

## Requisitos

- Android Studio con Android SDK 35.
- Docker Desktop.
- El repositorio `unitree_mujoco` clonado como carpeta hermana de este repositorio.

## Ejecutar

1. Desde `TPO2_UnitreeG1`, iniciar los contenedores:

   ```bash
   docker compose up --build
   ```

2. Confirmar la API en `http://localhost:8000/health` y el simulador en `http://localhost:6080/vnc.html`.
3. Abrir `TPO2_UnitreeG1/appdemo1` en Android Studio y ejecutar `app` en un emulador.
4. La URL predeterminada para el emulador es `http://10.0.2.2:8000`, que apunta al puerto 8000 publicado por Docker en el host.

Para probar en un dispositivo Android físico conectado a la misma red, agregar `backendUrl` a `gradle.properties` con la IP LAN de la computadora, por ejemplo `backendUrl=http://192.168.1.20:8000`. Asegurarse de que el firewall permita el puerto 8000.

## Funciones de la demo

- Consulta periódica de `GET /api/v1/robot` y estado de conexión visible.
- Ronda predefinida de cinco puntos dentro de un área de 6 × 8 m; utiliza `PUT /api/v1/area`, `POST /api/v1/robot/move` y `POST /api/v1/robot/stop`.
- Plano local con posición del G1, destino seleccionado y puntos completados.
- Registro manual del estado y captura de una foto desde el dispositivo.
- La temperatura mostrada es un dato de muestra, no una lectura del robot.

## Alcance actual

La API de este proyecto todavía usa `MockG1Adapter`: los movimientos actualizan coordenadas simuladas en el backend. Docker inicia MuJoCo por separado, pero el backend aún no publica destinos al simulador por DDS. Por eso esta demo valida el flujo Android-REST y no debe considerarse control efectivo del G1 en MuJoCo o del robot físico. Las fotos se mantienen en memoria durante la sesión de la app; todavía no se envían ni persisten en el backend.