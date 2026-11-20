"""Gemelo digital: política, navegación y TwinBody sin DDS."""

import math

import numpy as np
import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app import g1_policy as P
from app.bodies import TwinBody
from app.guide import GuideService, build_router

UPRIGHT = (1.0, 0.0, 0.0, 0.0)


def quat_yaw(yaw: float):
    return (math.cos(yaw / 2), 0.0, 0.0, math.sin(yaw / 2))


def test_gravity_upright_points_down() -> None:
    assert P.gravity_orientation(UPRIGHT) == pytest.approx([0, 0, -1])


def test_yaw_from_quat() -> None:
    assert P.yaw_from_quat(quat_yaw(1.2)) == pytest.approx(1.2)


def test_observation_layout() -> None:
    action = np.full(12, 0.5, dtype=np.float32)
    obs = P.build_observation(UPRIGHT, [0.4, 0, 0], P.DEFAULT_ANGLES + 0.1, np.ones(12),
                              action, P.clamp_command(0.5, 0, 0.2), step=0)
    assert obs.shape == (47,)
    assert obs[0] == pytest.approx(0.1)                      # gyro * 0.25
    assert obs[5] == pytest.approx(-1)                       # gravedad z
    assert obs[6:9] == pytest.approx([1.0, 0, 0.05])         # cmd * cmd_scale
    assert obs[9:21] == pytest.approx(np.full(12, 0.1))      # q - default
    assert obs[21:33] == pytest.approx(np.full(12, 0.05))    # dq * 0.05
    assert obs[33:45] == pytest.approx(action)
    assert (obs[45], obs[46]) == pytest.approx((0.0, 1.0))   # fase 0


def test_command_is_clamped() -> None:
    assert P.clamp_command(3, -3, 9) == pytest.approx([0.8, -0.5, 1.57])


def test_fall_detection() -> None:
    assert not P.is_fallen(UPRIGHT, 0.75)
    tilted = (math.cos(0.6), math.sin(0.6), 0.0, 0.0)  # ~69° de roll
    assert P.is_fallen(tilted, 0.75)
    assert P.is_fallen(UPRIGHT, 0.3)


def test_go_to_goal_straight_ahead() -> None:
    vx, vy, wz, arrived = P.go_to_goal(0, 0, 0, 5, 0)
    assert not arrived and vx == pytest.approx(0.5) and wz == pytest.approx(0)


def test_go_to_goal_turns_in_place_when_target_behind() -> None:
    vx, _, wz, arrived = P.go_to_goal(0, 0, 0, -5, 0.1)
    assert not arrived and vx == 0 and wz == pytest.approx(0.8)


def test_go_to_goal_arrives_within_tolerance() -> None:
    assert P.go_to_goal(0, 0, 0, 0.2, 0.1)[3]


class FakeController:
    """Doble de G1DdsController: integra el comando de velocidad como un unicycle."""

    def __init__(self) -> None:
        self.x = self.y = self.yaw = 0.0
        self.cmd = (0.0, 0.0, 0.0)
        self.is_ready = True
        self.events: list[str] = []

    def ready(self) -> bool:
        return self.is_ready

    def connected(self) -> bool:
        return True

    def set_velocity(self, vx, vy, wz) -> None:
        self.cmd = (vx, vy, wz)

    def advance(self, dt: float) -> None:
        vx, _, wz = self.cmd
        self.yaw += wz * dt
        self.x += vx * math.cos(self.yaw) * dt
        self.y += vx * math.sin(self.yaw) * dt

    def pose(self):
        return self.x, self.y, self.yaw

    def velocity(self):
        return self.cmd

    def drain_events(self):
        out, self.events = self.events, []
        return out


def test_twin_body_uses_campus_frame_and_waits_until_ready() -> None:
    ctrl = FakeController()
    body = TwinBody(ctrl, origin_x=1.0, origin_y=1.0)
    assert body.pose() == (1.0, 1.0, 0.0)
    ctrl.is_ready = False
    assert body.drive_to(5, 5, 0.1) is False
    assert ctrl.cmd == (0, 0, 0)


def test_twin_guides_robot_to_destination() -> None:
    clock = [0.0]
    ctrl = FakeController()
    service = GuideService(clock=lambda: clock[0], bodies={"g1-01": TwinBody(ctrl, 1.0, 1.0)})
    app = FastAPI()
    app.include_router(build_router(service))
    client = TestClient(app)

    body = {"robotId": "g1-01", "destinationId": "aula-101", "destinationName": "Aula 101",
            "x": 6.0, "y": 4.0, "companions": 1}
    assert client.post("/api/v1/missions/guide", json=body).json()["status"] == "CONFIRMED"
    state = None
    for _ in range(600):  # 60 s a 10 Hz
        clock[0] += 0.1
        state = client.get("/api/v1/robots/g1-01/telemetry").json()
        ctrl.advance(0.1)
        if state["operationalState"] == "ARRIVED":
            break
    assert state["operationalState"] == "ARRIVED"
    assert math.hypot(state["position"]["x"] - 6, state["position"]["y"] - 4) <= 0.35


def test_twin_fall_event_stops_mission_and_alerts() -> None:
    ctrl = FakeController()
    service = GuideService(clock=lambda: 0.0, bodies={"g1-01": TwinBody(ctrl, 1.0, 1.0)})
    app = FastAPI()
    app.include_router(build_router(service))
    client = TestClient(app)
    body = {"robotId": "g1-01", "destinationId": "d", "destinationName": "D", "x": 6, "y": 4, "companions": 0}
    client.post("/api/v1/missions/guide", json=body)
    ctrl.events.append("FALLEN")
    t = client.get("/api/v1/robots/g1-01/telemetry").json()
    assert t["operationalState"] == "STOPPED"
    assert t["alerts"][0]["severity"] == "CRITICAL"


def test_hold_position_deadband_and_body_frame() -> None:
    assert P.hold_position(0, 0, 0, 0.05, 0, 0) == (0, 0, 0)
    # mira hacia +y y derivó 0.5 m hacia -x: el punto queda a su derecha -> paso lateral (vy < 0)
    vx, vy, wz = P.hold_position(-0.5, 0, math.pi / 2, 0, 0, math.pi / 2)
    assert vx == pytest.approx(0, abs=1e-9) and vy == pytest.approx(-0.2) and wz == pytest.approx(0)


def test_twin_body_holds_where_it_stopped() -> None:
    ctrl = FakeController()
    body = TwinBody(ctrl, 1.0, 1.0)
    body.halt()                       # fija el punto de espera en (1, 1)
    ctrl.x = 0.5                      # deriva 0.5 m hacia adelante
    body.halt()
    assert ctrl.cmd[0] == pytest.approx(-0.2)  # retrocede
    body.drive_to(10, 1, 0.1)         # moverse anula el punto de espera
    assert body._hold is None
