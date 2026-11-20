# Backend Unitree G1

API REST inicial para controlar la posición del gemelo digital. Arranca con un adaptador local (`mock`) para poder desarrollar la app Android sin tener MuJoCo, DDS o el robot físico conectados.

## Ejecutar

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

Documentación interactiva: `http://127.0.0.1:8000/docs`

## Endpoints

- `GET /health`: disponibilidad del servicio.
- `GET /api/v1/robot`: estado actual del G1.
- `PUT /api/v1/area`: configura ancho y largo en metros.
- `POST /api/v1/robot/move`: mueve el mock a `{ "x": 2, "y": 5 }`.
- `POST /api/v1/robot/stop`: detiene el movimiento.

### Contrato de la app "Robot_guia_universitario" (`app/guide.py`)

Servicio de guía con 2 robots (`g1-01`, `g1-02`) que avanzan a 0,5 m/s en tiempo real. JSON en camelCase; los rechazos de negocio vuelven como HTTP 200 con `status: "REJECTED"`.

- `GET /api/v1/robots`
- `GET /api/v1/robots/{id}/telemetry`
- `POST /api/v1/missions/guide` — `{robotId, destinationId, destinationName, x, y, companions}`
- `POST /api/v1/missions/{id}/pause | resume | cancel`
- `POST /api/v1/robots/{id}/stop | return-safe`
- `POST /api/v1/alerts/{id}/ack`
- `POST /api/v1/simulation/{robotId}/{scenario}` — `LOW_BATTERY`, `DISCONNECT` (todo responde 503 hasta `RECOVER`), `REJECT_NEXT_COMMAND`, `VISITOR_LAGGING`, `DESTINATION_UNAVAILABLE`, `ROUTE_BLOCKED`, `RECOVER`

Desde el emulador Android: modo REST con URL `http://10.0.2.2:8000/`.

## Gemelo digital oficial

El modelo y las escenas MJCF se obtienen desde el repositorio oficial de Unitree:

- [unitree_mujoco](https://github.com/unitreerobotics/unitree_mujoco) - soporte para `g1`, simulador Python/C++ y escenas del robot.
- [unitree_sdk2_python](https://github.com/unitreerobotics/unitree_sdk2_python) - interfaz Python para DDS y control de alto nivel.

Ambos repositorios declaran licencia BSD-3-Clause, igual que [unitree_rl_gym](https://github.com/unitreerobotics/unitree_rl_gym), de donde salen la política de locomoción (`policy/motion.pt`) y el modelo `g1_12dof` que usa el gemelo.

## Adaptador DDS (gemelo digital)

Con `G1_ADAPTER=mujoco` (lo setea `docker-compose.yml`), el robot `g1-01` del servicio de guía deja de ser un mock y pasa a ser el G1 de MuJoCo:

```
App Android ──REST──> GuideService ──(vx, vy, wz)──> G1DdsController ──rt/lowcmd──> MuJoCo
                         ▲  navegador ir-al-objetivo       │ política RL 50 Hz          │
                         └──────── pose ◄── rt/lowstate + rt/sportmodestate ◄─────────┘
```

- `app/g1_dds.py`: controlador DDS (`unitree_hg`). Lógica de `deploy_real.py` de unitree_rl_gym: se para (2 s a la pose por defecto), activa la política y la alimenta con IMU y articulaciones. Si detecta una caída, pide un reset al simulador y vuelve a pararse; el servicio de guía genera una alerta y deja la misión en `STOPPED`.
- `app/g1_policy.py`: funciones puras de observación, navegación (`go_to_goal`) y mantener posición (`hold_position`, compensa la deriva de la política cuando el robot está quieto).
- `app/bodies.py`: `KinematicBody` (mock) y `TwinBody` (G1 por DDS). El origen del simulador es el punto seguro del campus (1, 1).
- `app/config.py`: variables de entorno (`G1_ADAPTER`, `G1_ROBOT_ID`, `DDS_DOMAIN_ID`, `DDS_INTERFACE`, `SIM_CONTROL_URL`, `G1_MAX_SPEED`).

`g1-02` sigue siendo un robot simulado en memoria. Los endpoints viejos (`/api/v1/robot`, `/area`, `/robot/move`) siguen con `MockG1Adapter`.

**Por qué `g1_12dof` y no el `g1_29dof` de unitree_mujoco:** la única política pública de marcha del G1 (`motion.pt`) se entrenó con el modelo de 12 GDL. En `g1_29dof`, aun con el torso y los brazos trabados, no sigue los comandos de velocidad (~0,05 m/s con un comando de 0,5 m/s). Con `g1_12dof` camina a la velocidad pedida. El contrato DDS es el mismo (motores 0–11 = piernas), así que cambiar de modelo es solo cambiar `SIM_SCENE`.

**Robot real:** el mismo controlador sirve apuntando `DDS_INTERFACE` a la placa de red del robot, `DDS_DOMAIN_ID=0` y sin `SIM_CONTROL_URL` (sin reset automático: ante una caída queda en modo amortiguado). En el G1 real conviene usar el `LocoClient` de alto nivel de Unitree en lugar de la política; `TwinBody` solo necesita `set_velocity`, `pose` y `ready`.

## Simulador en Docker

El repo `unitree_mujoco` debe estar clonado como carpeta **hermana** de este proyecto (mismo nivel que `TPO2_UnitreeG1`), porque `docker-compose.yml` lo monta como volumen desde `../../unitree_mujoco`:

```
ghrepo/
├── DesarrolloAplicaciones1/TPO2_UnitreeG1/   # este repo
└── unitree_mujoco/                            # https://github.com/unitreerobotics/unitree_mujoco
```

Desde la raíz de `TPO2_UnitreeG1`:

```bash
docker compose up --build
```

Esto levanta dos servicios:
- **simulator**: imagen con `mujoco`, `unitree_sdk2_python` (compilando CycloneDDS) y un Xvfb + x11vnc + noVNC para ver el visor sin instalar nada en el host. Abrí `http://localhost:6080/vnc.html` y conectate (sin password) para ver al G1. Corre `docker/simulator/sim_launcher.py` en lugar de `unitree_mujoco.py`: usa el mismo `config.py` y el mismo bridge, pero recalcula el PD de los motores en cada paso de física, sostiene al robot con un arnés (*weld*) mientras se para y expone `POST /reset`, `POST /support/on|off` y `GET /status` (con el factor de tiempo real) en `127.0.0.1:8765`, solo accesible desde el backend.
- **backend**: la API FastAPI, corriendo con `network_mode: "service:simulator"` para compartir el loopback (`lo`) con el simulador, tal como espera `unitree_sdk2_python` (`INTERFACE = "lo"` en `simulate_python/config.py`). Queda disponible en `http://localhost:8000/docs`.

La config del simulador (`unitree_mujoco/simulate_python/config.py`) ya está seteada en `ROBOT = "g1"` y `USE_JOYSTICK = 0` (no hay gamepad físico dentro del contenedor).

Para parar todo: `docker compose down`.