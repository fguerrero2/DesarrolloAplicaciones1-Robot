# Planes TPO Unitree — Android + REST

Este paquete contiene 20 planes de desarrollo e implementación para aplicaciones Android Studio orientadas a Unitree G1/Go2.

Todos los planes usan una base tecnológica común:

- Kotlin
- Jetpack Compose
- MVVM
- StateFlow / Flow
- Retrofit + OkHttp
- Room
- DataStore
- Hilt
- API REST configurable
- backend local o remoto
- Docker de referencia: `TPO2_UnitreeG1` (servicios `backend` y `simulator`)

## Archivos

01. [Robot guía universitario](01_robot_guia_universitario.md)
02. [Patrullaje de seguridad](02_patullaje_seguridad.md)
03. [Inspección de aulas y laboratorios](03_inspeccion_aulas_laboratorios.md)
04. [Entrega de documentación interna](04_entrega_documentacion.md)
05. [Asistente de biblioteca](05_asistente_biblioteca.md)
06. [Robot de recorrida en depósitos](06_recorrida_depositos.md)
07. [Supervisión de estacionamientos](07_supervision_estacionamientos.md)
08. [Asistente para eventos universitarios](08_asistente_eventos.md)
09. [Inspección de planta industrial](09_inspeccion_industrial.md)
10. [Control de acceso a zonas restringidas](10_control_acceso.md)
11. [Acompañante de visitas guiadas](11_visitas_guiadas.md)
12. [Inspección de pasillos hospitalarios](12_pasillos_hospitalarios.md)
13. [Entrega interna de insumos médicos simulada](13_entrega_insumos_medicos.md)
14. [Supervisor de evacuación simulada](14_evacuacion_simulada.md)
15. [Control de inventario visual](15_inventario_visual.md)
16. [Asistente para personas con movilidad reducida](16_asistente_accesibilidad.md)
17. [Control de circuito turístico](17_circuito_turistico.md)
18. [Robot inspector de obra](18_inspector_obra.md)
19. [Vigilancia de museo](19_vigilancia_museo.md)
20. [Gestor y editor de misiones robóticas](20_editor_misiones.md)

## Conexión con el Docker de referencia

La configuración ejecutable está en [`TPO2_UnitreeG1/docker-compose.yml`](../TPO2_UnitreeG1/docker-compose.yml). Desde la carpeta `TPO2_UnitreeG1` se construyen y levantan el backend FastAPI y el simulador:

```bash
docker compose up --build
```

La configuración local de Docker usa las imágenes `tpo2_unitreeg1-backend:latest` y `tpo2_unitreeg1-simulator:latest`. El backend comparte la red del simulador (`network_mode: service:simulator`), por eso el puerto de API `8000` se publica en el servicio `simulator`. El visor noVNC usa el puerto `6080`.

| Servicio | URL desde la PC | Uso |
|---|---|---|
| API REST | `http://localhost:8000/` | Conexión desde la app |
| Health check | `http://localhost:8000/health` | Verificar disponibilidad |
| Swagger | `http://localhost:8000/docs` | Explorar la API |
| Visor MuJoCo | `http://localhost:6080/vnc.html` | Ver el gemelo digital |

El backend actual ofrece `GET /health`, `GET /api/v1/robot`, `PUT /api/v1/area`, `POST /api/v1/robot/move` y `POST /api/v1/robot/stop`. Los otros endpoints descritos en cada idea son propuestas para ampliar esa API.

### URL base para Android

Para un emulador Android:

```text
http://10.0.2.2:8000/
```

`10.0.2.2` permite al emulador Android acceder al host. En un teléfono físico conectado a la misma red Wi-Fi, usar la IP LAN de la PC donde se ejecuta Docker:

```text
http://192.168.1.100:8000/
```

La IP es solo un ejemplo; obtener la dirección real de la PC y permitir el puerto `8000` en el firewall. Guardar la URL base configurable en la app (por ejemplo, con DataStore). Para verificar la conexión desde la PC, ejecutar `curl http://localhost:8000/health`; debe responder `{"status":"ok","service":"unitree-g1-backend"}`. Consultar el estado y los logs con `docker compose ps` y `docker compose logs backend simulator`.
