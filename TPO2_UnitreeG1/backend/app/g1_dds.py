"""Controlador DDS del G1 para el gemelo digital (unitree_mujoco) o el robot real.

Publica `rt/lowcmd` (unitree_hg) a 50 Hz corriendo la política de locomoción
pre-entrenada de unitree_rl_gym, y lee `rt/lowstate` + `rt/sportmodestate` para
conocer articulaciones, IMU y posición. Recibe comandos de velocidad (vx, vy, wz).

Máquina de estados:
  WAITING_STATE -> STANDING_UP -> WALKING
        ^                            |
        +------ FALLEN (reset) <-----+

En el simulador, el reset y el "arnés" que sostiene la pelvis mientras se para los
provee el lanzador de docker/simulator (HTTP en SIM_CONTROL_URL). Sin esa URL
(robot real) una caída deja el robot en modo amortiguado hasta reiniciar el backend.

Las dependencias pesadas (unitree_sdk2py, torch) se importan recién en start(), así
el backend sigue funcionando en modo mock sin ellas.
"""

from __future__ import annotations

import logging
import threading
import time
import urllib.request
from collections import deque
from typing import Optional

import numpy as np

from app import g1_policy as P

log = logging.getLogger("g1_dds")

STATE_TIMEOUT_S = 0.5
STAND_UP_S = 2.0
HOLD_DEFAULT_S = 1.5


class G1DdsController:
    def __init__(self, policy_path: str, domain_id: int = 1, interface: str = "lo",
                 sim_control_url: Optional[str] = None) -> None:
        self.policy_path = policy_path
        self.domain_id = domain_id
        self.interface = interface
        self.sim_control_url = sim_control_url.rstrip("/") if sim_control_url else None

        self.state = "WAITING_STATE"
        self.events: deque[str] = deque(maxlen=20)
        self._lock = threading.Lock()
        self._cmd = np.zeros(3, dtype=np.float32)
        self._low_state = None
        self._low_state_at = 0.0
        self._position = np.zeros(3, dtype=np.float32)
        self._velocity = np.zeros(3, dtype=np.float32)
        self._thread: Optional[threading.Thread] = None
        self._running = False

    # ------------------------------------------------------------ API para el backend

    def start(self) -> None:
        from unitree_sdk2py.core.channel import (ChannelFactoryInitialize, ChannelPublisher,
                                                 ChannelSubscriber)
        from unitree_sdk2py.idl.default import unitree_hg_msg_dds__LowCmd_
        from unitree_sdk2py.idl.unitree_go.msg.dds_ import SportModeState_
        from unitree_sdk2py.idl.unitree_hg.msg.dds_ import LowCmd_, LowState_
        from unitree_sdk2py.utils.crc import CRC
        import torch

        torch.set_num_threads(1)
        self._torch = torch
        self._policy = torch.jit.load(self.policy_path)
        self._policy.eval()

        ChannelFactoryInitialize(self.domain_id, self.interface)
        self._crc = CRC()
        self._low_cmd = unitree_hg_msg_dds__LowCmd_()
        self._low_cmd.mode_pr = 0
        for m in self._low_cmd.motor_cmd:
            m.mode = 1
        self._pub = ChannelPublisher("rt/lowcmd", LowCmd_)
        self._pub.Init()
        self._sub_low = ChannelSubscriber("rt/lowstate", LowState_)
        self._sub_low.Init(self._on_low_state, 10)
        self._sub_high = ChannelSubscriber("rt/sportmodestate", SportModeState_)
        self._sub_high.Init(self._on_high_state, 10)

        self._running = True
        self._thread = threading.Thread(target=self._loop, name="g1-control", daemon=True)
        self._thread.start()
        log.info("Controlador G1 DDS iniciado (domain=%s, iface=%s)", self.domain_id, self.interface)

    def stop(self) -> None:
        self._running = False

    def set_velocity(self, vx: float, vy: float, wz: float) -> None:
        with self._lock:
            self._cmd = P.clamp_command(vx, vy, wz)

    def connected(self) -> bool:
        return self._low_state is not None and time.monotonic() - self._low_state_at < STATE_TIMEOUT_S

    def ready(self) -> bool:
        return self.state == "WALKING" and self.connected()

    def pose(self) -> tuple[float, float, float]:
        """(x, y, yaw) en el marco del mundo del simulador."""
        with self._lock:
            ls = self._low_state
            x, y = float(self._position[0]), float(self._position[1])
        yaw = P.yaw_from_quat(ls.imu_state.quaternion) if ls is not None else 0.0
        return x, y, yaw

    def velocity(self) -> tuple[float, float, float]:
        with self._lock:
            ls = self._low_state
            vx, vy = float(self._velocity[0]), float(self._velocity[1])
        wz = float(ls.imu_state.gyroscope[2]) if ls is not None else 0.0
        return vx, vy, wz

    def drain_events(self) -> list[str]:
        out = list(self.events)
        self.events.clear()
        return out

    # ------------------------------------------------------------ DDS callbacks

    def _on_low_state(self, msg) -> None:
        with self._lock:
            self._low_state = msg
            self._low_state_at = time.monotonic()

    def _on_high_state(self, msg) -> None:
        with self._lock:
            self._position[:] = msg.position[:3]
            self._velocity[:] = msg.velocity[:3]

    # ------------------------------------------------------------ helpers

    def _sim(self, path: str) -> bool:
        if not self.sim_control_url:
            return False
        try:
            req = urllib.request.Request(self.sim_control_url + path, method="POST", data=b"")
            urllib.request.urlopen(req, timeout=2).read()
            return True
        except OSError as e:
            log.warning("No se pudo llamar al simulador %s: %s", path, e)
            return False

    def _send(self, q, kp, kd, idx) -> None:
        for j, motor in enumerate(idx):
            mc = self._low_cmd.motor_cmd[motor]
            mc.q = float(q[j])
            mc.dq = 0.0
            mc.kp = float(kp[j])
            mc.kd = float(kd[j])
            mc.tau = 0.0

    def _publish(self) -> None:
        self._low_cmd.crc = self._crc.Crc(self._low_cmd)
        self._pub.Write(self._low_cmd)

    def _hold_upper_body(self) -> None:
        self._send(P.ARM_WAIST_TARGET, P.ARM_WAIST_KP, P.ARM_WAIST_KD, P.ARM_WAIST_IDX)

    def _damping(self) -> None:
        for mc in self._low_cmd.motor_cmd:
            mc.q = mc.dq = mc.kp = mc.tau = 0.0
            mc.kd = 8.0
        self._publish()

    def _reset_policy(self) -> None:
        self._action = np.zeros(P.NUM_ACTIONS, dtype=np.float32)
        self._step = 0
        if hasattr(self._policy, "reset_memory"):
            self._policy.reset_memory()

    # ------------------------------------------------------------ bucle de control

    def _loop(self) -> None:
        next_tick = time.monotonic()
        stand_t0 = 0.0
        stand_from = None
        self._reset_policy()
        while self._running:
            next_tick += P.CONTROL_DT
            try:
                if not self.connected():
                    if self.state != "WAITING_STATE":
                        log.warning("Se perdió rt/lowstate")
                        self.events.append("DISCONNECTED")
                    self.state = "WAITING_STATE"
                else:
                    with self._lock:
                        ls = self._low_state
                        pelvis_z = float(self._position[2])

                    if self.state == "WAITING_STATE":
                        # Siempre arrancamos desde una pose conocida (en el sim, robot de pie y sostenido).
                        self._sim("/reset")
                        time.sleep(0.2)
                        with self._lock:
                            ls = self._low_state
                        stand_from = np.array([ls.motor_state[i].q for i in range(P.NUM_MOTORS)], dtype=np.float32)
                        stand_t0 = time.monotonic()
                        self.state = "STANDING_UP"
                        log.info("G1: parándose")

                    elif self.state == "STANDING_UP":
                        t = time.monotonic() - stand_t0
                        alpha = min(1.0, t / STAND_UP_S)
                        legs = stand_from[:12] * (1 - alpha) + P.DEFAULT_ANGLES * alpha
                        upper = stand_from[12:29] * (1 - alpha) + P.ARM_WAIST_TARGET * alpha
                        self._send(legs, P.LEG_KP, P.LEG_KD, P.LEG_IDX)
                        self._send(upper, P.ARM_WAIST_KP, P.ARM_WAIST_KD, P.ARM_WAIST_IDX)
                        self._publish()
                        if t >= STAND_UP_S + HOLD_DEFAULT_S:
                            self._sim("/support/off")
                            self._reset_policy()
                            self.state = "WALKING"
                            self.events.append("READY")
                            log.info("G1: caminando (política RL activa)")

                    elif self.state == "WALKING":
                        if P.is_fallen(ls.imu_state.quaternion, pelvis_z):
                            log.warning("G1: caída detectada (z=%.2f)", pelvis_z)
                            self._damping()
                            self.events.append("FALLEN")
                            if self.sim_control_url:
                                self.state = "WAITING_STATE"  # reset en el próximo ciclo
                                self.events.append("RESET")
                            else:
                                self.state = "FALLEN"
                        else:
                            self._policy_step(ls)

                    elif self.state == "FALLEN":
                        self._damping()
            except Exception:  # el hilo de control no debe morir
                log.exception("Error en el bucle de control del G1")

            sleep = next_tick - time.monotonic()
            if sleep > 0:
                time.sleep(sleep)
            else:
                next_tick = time.monotonic()

    def _policy_step(self, ls) -> None:
        q = np.array([ls.motor_state[i].q for i in P.LEG_IDX], dtype=np.float32)
        dq = np.array([ls.motor_state[i].dq for i in P.LEG_IDX], dtype=np.float32)
        with self._lock:
            cmd = self._cmd.copy()
        self._step += 1
        obs = P.build_observation(ls.imu_state.quaternion, ls.imu_state.gyroscope, q, dq,
                                  self._action, cmd, self._step)
        with self._torch.no_grad():
            self._action = self._policy(self._torch.from_numpy(obs).unsqueeze(0)).numpy().squeeze()
        self._send(P.action_to_targets(self._action), P.LEG_KP, P.LEG_KD, P.LEG_IDX)
        self._hold_upper_body()
        self._publish()
