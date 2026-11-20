"""Funciones puras (numpy) del control de locomoción del G1 y la navegación.

Replica la construcción de observaciones de `deploy/deploy_real/deploy_real.py` de
unitree_rl_gym (BSD-3-Clause) para la política pre-entrenada `policy/motion.pt`.
No depende de DDS ni de torch, así se puede testear sin el simulador.
"""

from __future__ import annotations

import math

import numpy as np

# --- Configuración de deploy_real/configs/g1.yaml (unitree_rl_gym) ---
CONTROL_DT = 0.02  # 50 Hz
NUM_MOTORS = 29
LEG_IDX = list(range(12))
LEG_KP = np.array([100, 100, 100, 150, 40, 40, 100, 100, 100, 150, 40, 40], dtype=np.float32)
LEG_KD = np.array([2, 2, 2, 4, 2, 2, 2, 2, 2, 4, 2, 2], dtype=np.float32)
DEFAULT_ANGLES = np.array([-0.1, 0.0, 0.0, 0.3, -0.2, 0.0,
                           -0.1, 0.0, 0.0, 0.3, -0.2, 0.0], dtype=np.float32)
ARM_WAIST_IDX = list(range(12, 29))
ARM_WAIST_KP = np.array([300, 300, 300, 100, 100, 50, 50, 20, 20, 20,
                         100, 100, 50, 50, 20, 20, 20], dtype=np.float32)
ARM_WAIST_KD = np.array([3, 3, 3, 2, 2, 2, 2, 1, 1, 1,
                         2, 2, 2, 2, 1, 1, 1], dtype=np.float32)
ARM_WAIST_TARGET = np.zeros(17, dtype=np.float32)

ANG_VEL_SCALE = 0.25
DOF_POS_SCALE = 1.0
DOF_VEL_SCALE = 0.05
ACTION_SCALE = 0.25
CMD_SCALE = np.array([2.0, 2.0, 0.25], dtype=np.float32)
MAX_CMD = np.array([0.8, 0.5, 1.57], dtype=np.float32)  # vx, vy [m/s], wz [rad/s]
NUM_ACTIONS = 12
NUM_OBS = 47
GAIT_PERIOD = 0.8


def gravity_orientation(quat_wxyz) -> np.ndarray:
    """Gravedad proyectada en el marco de la pelvis (igual que rotation_helper.py)."""
    qw, qx, qy, qz = quat_wxyz
    return np.array([
        2 * (-qz * qx + qw * qy),
        -2 * (qz * qy + qw * qx),
        1 - 2 * (qw * qw + qz * qz),
    ], dtype=np.float32)


def yaw_from_quat(quat_wxyz) -> float:
    qw, qx, qy, qz = quat_wxyz
    return math.atan2(2 * (qw * qz + qx * qy), 1 - 2 * (qy * qy + qz * qz))


def clamp_command(vx: float, vy: float, wz: float) -> np.ndarray:
    cmd = np.array([vx, vy, wz], dtype=np.float32)
    return np.clip(cmd, -MAX_CMD, MAX_CMD)


def build_observation(quat_wxyz, gyro, q_legs, dq_legs, last_action, cmd, step: int) -> np.ndarray:
    """Observación de 47 valores que espera motion.pt.

    `cmd` es la velocidad deseada en unidades físicas (vx, vy, wz), ya limitada por MAX_CMD.
    """
    phase = (step * CONTROL_DT) % GAIT_PERIOD / GAIT_PERIOD
    obs = np.zeros(NUM_OBS, dtype=np.float32)
    obs[0:3] = np.asarray(gyro, dtype=np.float32) * ANG_VEL_SCALE
    obs[3:6] = gravity_orientation(quat_wxyz)
    obs[6:9] = np.asarray(cmd, dtype=np.float32) * CMD_SCALE
    n = NUM_ACTIONS
    obs[9:9 + n] = (np.asarray(q_legs, dtype=np.float32) - DEFAULT_ANGLES) * DOF_POS_SCALE
    obs[9 + n:9 + 2 * n] = np.asarray(dq_legs, dtype=np.float32) * DOF_VEL_SCALE
    obs[9 + 2 * n:9 + 3 * n] = last_action
    obs[9 + 3 * n] = math.sin(2 * math.pi * phase)
    obs[9 + 3 * n + 1] = math.cos(2 * math.pi * phase)
    return obs


def action_to_targets(action) -> np.ndarray:
    return DEFAULT_ANGLES + np.asarray(action, dtype=np.float32) * ACTION_SCALE


def is_fallen(quat_wxyz, pelvis_z: float) -> bool:
    """Caído si la pelvis está inclinada más de ~55° o muy baja."""
    return gravity_orientation(quat_wxyz)[2] > -0.57 or pelvis_z < 0.45


# --- Navegación "ir al objetivo" para el modo guía ---

def wrap_angle(a: float) -> float:
    return (a + math.pi) % (2 * math.pi) - math.pi


def go_to_goal(x: float, y: float, yaw: float, tx: float, ty: float,
               max_v: float = 0.5, max_w: float = 0.8, tolerance: float = 0.3,
               ) -> tuple[float, float, float, bool]:
    """Devuelve (vx, vy, wz, llegó). Gira en el lugar si el objetivo queda muy de costado."""
    dx, dy = tx - x, ty - y
    dist = math.hypot(dx, dy)
    if dist <= tolerance:
        return 0.0, 0.0, 0.0, True
    err = wrap_angle(math.atan2(dy, dx) - yaw)
    wz = max(-max_w, min(max_w, 1.5 * err))
    if abs(err) > math.pi / 3:
        vx = 0.0
    else:
        vx = min(max_v * math.cos(err), 0.6 * dist + 0.1)
    return vx, 0.0, wz, False


def hold_position(x: float, y: float, yaw: float, hx: float, hy: float, hyaw: float,
                  deadband: float = 0.1, max_v: float = 0.2, max_w: float = 0.4,
                  ) -> tuple[float, float, float]:
    """Corrección suave para quedarse en (hx, hy, hyaw).

    Con comando cero la política marcha en el lugar y deriva; esto la compensa usando
    avance y desplazamiento lateral (marco del robot), sin girar hacia el punto.
    """
    dx, dy = hx - x, hy - y
    yaw_err = wrap_angle(hyaw - yaw)
    if math.hypot(dx, dy) < deadband and abs(yaw_err) < deadband:
        return 0.0, 0.0, 0.0
    c, s = math.cos(yaw), math.sin(yaw)
    ex, ey = c * dx + s * dy, -s * dx + c * dy  # error en el marco del robot
    k = 1.0
    vx = max(-max_v, min(max_v, k * ex))
    vy = max(-max_v, min(max_v, k * ey))
    wz = max(-max_w, min(max_w, 1.0 * yaw_err))
    return vx, vy, wz
