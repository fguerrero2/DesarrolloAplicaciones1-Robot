"""Lanzador del gemelo digital G1 (reemplaza a unitree_mujoco.py, sin modificar el repo upstream).

Reutiliza `config.py` y `unitree_sdk2py_bridge.py` de unitree_mujoco/simulate_python y agrega:

1. PD por paso de física: el bridge upstream calcula el torque sólo cuando llega un
   LowCmd; con un controlador a 50 Hz el torque queda congelado 4 pasos y el G1 se
   vuelve inestable. Acá se guarda el último LowCmd y el PD se recalcula en cada paso,
   como hace el driver de los motores reales.
2. Arnés de soporte (restricción weld de la pelvis) para que el G1 no se caiga al arrancar
   ni al reiniciarse mientras el controlador lo lleva a la pose por defecto.
3. Endpoint HTTP de control en 127.0.0.1:SIM_CONTROL_PORT (sólo loopback, que el
   backend comparte vía network_mode):
     POST /reset         robot de pie en el origen y arnés activo
     POST /support/off   suelta el arnés
     POST /support/on
     GET  /status        {"support":..., "sim_time":..., "realtime_factor":..., "pelvis":[x,y,z]}
"""

import json
import os
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SIM_DIR = os.environ.get("SIM_DIR", "/workspace/unitree_mujoco/simulate_python")
sys.path.insert(0, SIM_DIR)
os.chdir(SIM_DIR)  # config.ROBOT_SCENE es relativo a esta carpeta

import mujoco  # noqa: E402
import mujoco.viewer  # noqa: E402
import numpy as np  # noqa: E402
from unitree_sdk2py.core.channel import ChannelFactoryInitialize  # noqa: E402

import config  # noqa: E402
from unitree_sdk2py_bridge import UnitreeSdk2Bridge  # noqa: E402

CONTROL_PORT = int(os.environ.get("SIM_CONTROL_PORT", "8765"))

locker = threading.Lock()
# El arnés es una restricción "weld" pelvis-mundo agregada al modelo (implícita y
# estable, a diferencia de aplicar fuerzas a mano). Se activa/desactiva con eq_active.
# SIM_SCENE permite usar otra escena (por defecto la de config.py de unitree_mujoco).
SCENE = os.environ.get("SIM_SCENE") or config.ROBOT_SCENE


def add_bridge_sensors(spec):
    """Agrega los sensores que espera unitree_sdk2py_bridge si la escena no los trae.

    Orden: pos/vel/torque de cada motor, imu_quat, imu_gyro, imu_acc, frame_pos, frame_vel.
    (Los modelos de unitree_rl_gym, p. ej. g1_12dof, no definen sensores.)
    """
    if any(sensor.name == "imu_quat" for sensor in spec.sensors):
        return
    joints = [act.target for act in spec.actuators]
    for kind, suffix in ((mujoco.mjtSensor.mjSENS_JOINTPOS, "pos"),
                         (mujoco.mjtSensor.mjSENS_JOINTVEL, "vel"),
                         (mujoco.mjtSensor.mjSENS_JOINTACTFRC, "torque")):
        for joint in joints:
            spec.add_sensor(name=f"{joint}_{suffix}", type=kind,
                            objtype=mujoco.mjtObj.mjOBJ_JOINT, objname=joint)
    if not any(site.name == "imu" for site in spec.sites):
        spec.body("pelvis").add_site(name="imu", pos=[0, 0, 0])
    site = dict(objtype=mujoco.mjtObj.mjOBJ_SITE, objname="imu")
    spec.add_sensor(name="imu_quat", type=mujoco.mjtSensor.mjSENS_FRAMEQUAT, **site)
    spec.add_sensor(name="imu_gyro", type=mujoco.mjtSensor.mjSENS_GYRO, **site)
    spec.add_sensor(name="imu_acc", type=mujoco.mjtSensor.mjSENS_ACCELEROMETER, **site)
    spec.add_sensor(name="frame_pos", type=mujoco.mjtSensor.mjSENS_FRAMEPOS, **site)
    spec.add_sensor(name="frame_vel", type=mujoco.mjtSensor.mjSENS_FRAMELINVEL, **site)


_spec = mujoco.MjSpec.from_file(SCENE)
add_bridge_sensors(_spec)
_spec.add_equality(name="support", type=mujoco.mjtEq.mjEQ_WELD, name1="pelvis",
                   objtype=mujoco.mjtObj.mjOBJ_BODY)
mj_model = _spec.compile()
mj_data = mujoco.MjData(mj_model)
SUPPORT_EQ = mj_model.equality("support").id
mj_model.opt.timestep = config.SIMULATE_DT
PELVIS = mj_model.body("pelvis").id


class PdBridge(UnitreeSdk2Bridge):
    def __init__(self, model, data):
        self._cmd = np.zeros((5, model.nu))  # q, dq, kp, kd, tau
        self._cmd_lock = threading.Lock()
        super().__init__(model, data)

    def LowCmdHandler(self, msg):
        n = self.num_motor
        cmd = np.array([[m.q, m.dq, m.kp, m.kd, m.tau] for m in msg.motor_cmd[:n]]).T
        with self._cmd_lock:
            self._cmd = cmd

    def clear_command(self):
        with self._cmd_lock:
            self._cmd = np.zeros_like(self._cmd)

    def apply_pd(self):
        with self._cmd_lock:
            q, dq, kp, kd, tau = self._cmd
        n = self.num_motor
        s = self.mj_data.sensordata
        self.mj_data.ctrl[:] = tau + kp * (q - s[:n]) + kd * (dq - s[n:2 * n])


class Support:
    """Arnés: weld de la pelvis a su pose inicial (de pie en el origen)."""

    def set(self, enabled: bool) -> None:
        with locker:
            mj_data.eq_active[SUPPORT_EQ] = 1 if enabled else 0

    @property
    def enabled(self) -> bool:
        return bool(mj_data.eq_active[SUPPORT_EQ])


support = Support()
stats = {"sim_time": 0.0, "realtime_factor": 0.0}
bridge = None


def reset_robot():
    with locker:
        mujoco.mj_resetData(mj_model, mj_data)  # también reactiva el weld (eq_active0)
        mujoco.mj_forward(mj_model, mj_data)
        if bridge is not None:
            bridge.clear_command()


class ControlHandler(BaseHTTPRequestHandler):
    def _reply(self, body, code=200):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/status":
            with locker:
                pelvis = mj_data.xpos[PELVIS].tolist()
            self._reply({"support": support.enabled, **stats, "pelvis": pelvis})
        else:
            self._reply({"error": "not found"}, 404)

    def do_POST(self):
        if self.path == "/reset":
            reset_robot()
        elif self.path == "/support/off":
            support.set(False)
        elif self.path == "/support/on":
            support.set(True)  # vuelve a la pose del weld (de pie en el origen)
        else:
            return self._reply({"error": "not found"}, 404)
        self._reply({"ok": True, "support": support.enabled})

    def log_message(self, *args):
        pass


def control_server():
    ThreadingHTTPServer(("127.0.0.1", CONTROL_PORT), ControlHandler).serve_forever()


viewer = mujoco.viewer.launch_passive(mj_model, mj_data)
time.sleep(0.2)


def simulation_thread():
    global bridge
    ChannelFactoryInitialize(config.DOMAIN_ID, config.INTERFACE)
    bridge = PdBridge(mj_model, mj_data)
    if config.PRINT_SCENE_INFORMATION:
        bridge.PrintSceneInformation()

    dt = mj_model.opt.timestep
    wall0, sim0 = time.perf_counter(), mj_data.time
    deadline = time.perf_counter()
    while viewer.is_running():
        with locker:
            bridge.apply_pd()
            mujoco.mj_step(mj_model, mj_data)
            sim_t = mj_data.time
        now = time.perf_counter()
        if now - wall0 >= 2.0:
            stats["realtime_factor"] = round((sim_t - sim0) / (now - wall0), 3)
            wall0, sim0 = now, sim_t
        stats["sim_time"] = round(sim_t, 2)
        # Deadline acumulado: compensa el exceso de sleep para mantener tiempo real
        # (la política de locomoción asume 20 ms de física por paso de control).
        deadline += dt
        remaining = deadline - time.perf_counter()
        if remaining > 0:
            time.sleep(remaining)
        elif remaining < -0.1:
            deadline = time.perf_counter()  # muy atrasados: no intentar recuperar


def viewer_thread():
    while viewer.is_running():
        t0 = time.perf_counter()
        with locker:
            t1 = time.perf_counter()
            viewer.sync()
            t2 = time.perf_counter()
        stats["viewer_wait_ms"] = round((t1 - t0) * 1000, 1)
        stats["viewer_sync_ms"] = round((t2 - t1) * 1000, 1)
        time.sleep(config.VIEWER_DT)


if __name__ == "__main__":
    threading.Thread(target=control_server, daemon=True).start()
    threading.Thread(target=viewer_thread).start()
    threading.Thread(target=simulation_thread).start()
