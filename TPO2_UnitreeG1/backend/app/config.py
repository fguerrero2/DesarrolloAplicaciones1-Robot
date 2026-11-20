"""Selección del adaptador del G1 por variables de entorno.

G1_ADAPTER        mock (por defecto) | mujoco
G1_ROBOT_ID       robot del servicio de guía que es el gemelo digital (g1-01)
DDS_DOMAIN_ID     1 en unitree_mujoco (simulate_python/config.py); 0 en el robot real
DDS_INTERFACE     lo en Docker/simulador; la interfaz de red hacia el robot real
G1_POLICY_PATH    política de locomoción TorchScript (policy/motion.pt)
SIM_CONTROL_URL   control del lanzador del simulador (reset/arnés); vacío en el robot real
G1_MAX_SPEED      velocidad máxima de avance en modo guía [m/s]
"""

from __future__ import annotations

import logging
import os
from pathlib import Path

from app.bodies import RobotBody, TwinBody
from app.guide import SAFE_X, SAFE_Y

log = logging.getLogger("g1_config")

DEFAULT_POLICY = Path(__file__).resolve().parents[1] / "policy" / "motion.pt"


def build_bodies_from_env() -> dict[str, RobotBody]:
    adapter = os.environ.get("G1_ADAPTER", "mock").lower()
    if adapter == "mock":
        return {}
    if adapter != "mujoco":
        raise ValueError(f"G1_ADAPTER desconocido: {adapter}")

    from app.g1_dds import G1DdsController

    controller = G1DdsController(
        policy_path=os.environ.get("G1_POLICY_PATH", str(DEFAULT_POLICY)),
        domain_id=int(os.environ.get("DDS_DOMAIN_ID", "1")),
        interface=os.environ.get("DDS_INTERFACE", "lo"),
        sim_control_url=os.environ.get("SIM_CONTROL_URL") or None,
    )
    controller.start()
    robot_id = os.environ.get("G1_ROBOT_ID", "g1-01")
    max_speed = float(os.environ.get("G1_MAX_SPEED", "0.5"))
    log.info("Robot %s conectado al gemelo digital por DDS", robot_id)
    return {robot_id: TwinBody(controller, SAFE_X, SAFE_Y, max_speed=max_speed)}
