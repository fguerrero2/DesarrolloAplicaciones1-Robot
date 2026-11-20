"""Cómo se mueve físicamente cada robot del servicio de guía.

- KinematicBody: mock en memoria, avanza en línea recta a velocidad constante.
- TwinBody: el G1 del gemelo digital (MuJoCo) o el robot real, vía G1DdsController.

Todas las coordenadas expuestas están en el marco del campus (el de la app). El
origen del simulador coincide con el punto seguro del campus.
"""

from __future__ import annotations

import math
from typing import Optional, Protocol

from app import g1_policy as P


class RobotBody(Protocol):
    kind: str

    def pose(self) -> tuple[float, float, float]: ...
    def velocity(self) -> tuple[float, float, float]: ...
    def drive_to(self, tx: float, ty: float, dt: float) -> bool: ...
    def halt(self) -> None: ...
    def connected(self) -> bool: ...
    def drain_events(self) -> list[str]: ...


class KinematicBody:
    kind = "mock"

    def __init__(self, x: float, y: float, speed: float = 0.5, tolerance: float = 0.05) -> None:
        self.x, self.y, self.yaw = x, y, 0.0
        self.vx = self.vy = self.omega = 0.0
        self.speed = speed
        self.tolerance = tolerance

    def pose(self) -> tuple[float, float, float]:
        return self.x, self.y, self.yaw

    def velocity(self) -> tuple[float, float, float]:
        return self.vx, self.vy, self.omega

    def drive_to(self, tx: float, ty: float, dt: float) -> bool:
        dx, dy = tx - self.x, ty - self.y
        dist = math.hypot(dx, dy)
        step = self.speed * dt
        if dist <= max(step, self.tolerance):
            self.x, self.y = tx, ty
            self.halt()
            return True
        yaw = math.atan2(dy, dx)
        self.omega = yaw - self.yaw
        self.yaw = yaw
        self.vx = self.speed * math.cos(yaw)
        self.vy = self.speed * math.sin(yaw)
        self.x += self.vx * dt
        self.y += self.vy * dt
        return False

    def halt(self) -> None:
        self.vx = self.vy = self.omega = 0.0

    def connected(self) -> bool:
        return True

    def drain_events(self) -> list[str]:
        return []


class TwinBody:
    """G1 controlado por DDS. `controller` es un G1DdsController (o un doble de test)."""

    kind = "mujoco"

    def __init__(self, controller, origin_x: float, origin_y: float,
                 max_speed: float = 0.5, tolerance: float = 0.3) -> None:
        self.controller = controller
        self.ox, self.oy = origin_x, origin_y
        self.max_speed = max_speed
        self.tolerance = tolerance
        self._hold: Optional[tuple[float, float, float]] = None

    def pose(self) -> tuple[float, float, float]:
        x, y, yaw = self.controller.pose()
        return x + self.ox, y + self.oy, yaw

    def velocity(self) -> tuple[float, float, float]:
        return self.controller.velocity()

    def drive_to(self, tx: float, ty: float, dt: float) -> bool:
        if not self.controller.ready():
            self._hold = None
            self.controller.set_velocity(0, 0, 0)
            return False
        x, y, yaw = self.pose()
        vx, vy, wz, arrived = P.go_to_goal(x, y, yaw, tx, ty,
                                           max_v=self.max_speed, tolerance=self.tolerance)
        if arrived:
            self.halt()
        else:
            self._hold = None
            self.controller.set_velocity(vx, vy, wz)
        return arrived

    def halt(self) -> None:
        """Quedarse quieto: mantiene la pose donde se detuvo (compensa la deriva)."""
        if not self.controller.ready():
            self._hold = None
            self.controller.set_velocity(0, 0, 0)
            return
        pose = self.pose()
        if self._hold is None:
            self._hold = pose
        self.controller.set_velocity(*P.hold_position(*pose, *self._hold))

    def connected(self) -> bool:
        return self.controller.connected()

    def drain_events(self) -> list[str]:
        return self.controller.drain_events()
