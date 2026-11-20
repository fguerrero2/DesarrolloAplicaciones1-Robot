"""Servicio de guía universitaria sobre el G1.

Implementa el contrato REST que consume la app Android "Robot_guia_universitario"
(ver RobotApi en RestRobotDataSource.kt). El estado del robot se simula en memoria
(mismo comportamiento que SimulatedRobotDataSource de la app), pero avanzando según
el tiempo real transcurrido. Cuando exista el adaptador DDS hacia MuJoCo, `_step`
es el único punto que debe delegar el movimiento al gemelo digital.
"""

from __future__ import annotations

import math
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import Enum
from threading import Lock, Thread
from typing import Callable, Optional

from fastapi import APIRouter, HTTPException, Response
from pydantic import BaseModel

from app.bodies import KinematicBody, RobotBody

# Punto seguro del campus (recepción), en metros. Igual que SafePoint en la app.
SAFE_X = 1.0
SAFE_Y = 1.0
SAFE_POINT_NAME = "Recepción (punto seguro)"

BATTERY_DRAIN_MOVING_PER_S = 0.25
BATTERY_DRAIN_IDLE_PER_S = 0.02
LOW_BATTERY_THRESHOLD = 20
CHARGE_PER_S = 0.2           # cargando en la base (recepción)
DOCK_RADIUS_M = 0.5


# ---------------------------------------------------------------- DTOs (JSON camelCase)

class HealthDto(BaseModel):
    status: str
    service: str


class RobotDto(BaseModel):
    robotId: str
    name: str
    location: str = ""


class PositionDto(BaseModel):
    x: float = 0.0
    y: float = 0.0
    yaw: float = 0.0


class VelocityDto(BaseModel):
    vx: float = 0.0
    vy: float = 0.0
    omega: float = 0.0


class AlertDto(BaseModel):
    id: str
    type: str
    severity: str
    message: str
    timestamp: str
    acknowledged: bool = False


class RobotTelemetryDto(BaseModel):
    robotId: str
    connection: str = "CONNECTED"
    battery: int
    operationalState: str
    missionId: Optional[str] = None
    missionProgress: int = 0
    position: PositionDto
    velocity: VelocityDto
    alerts: list[AlertDto]
    updatedAt: str
    destinationId: Optional[str] = None
    destinationName: Optional[str] = None
    remainingDistance: float = 0.0
    companions: int = 0


class GuideMissionRequestDto(BaseModel):
    robotId: str
    destinationId: str
    destinationName: str
    x: float
    y: float
    companions: int = 0


class CommandResponseDto(BaseModel):
    commandId: str
    status: str
    message: str
    timestamp: str
    missionId: Optional[str] = None


class Scenario(str, Enum):
    LOW_BATTERY = "LOW_BATTERY"
    DISCONNECT = "DISCONNECT"
    REJECT_NEXT_COMMAND = "REJECT_NEXT_COMMAND"
    VISITOR_LAGGING = "VISITOR_LAGGING"
    DESTINATION_UNAVAILABLE = "DESTINATION_UNAVAILABLE"
    ROUTE_BLOCKED = "ROUTE_BLOCKED"
    RECOVER = "RECOVER"


# ---------------------------------------------------------------- Estado interno

@dataclass
class _Robot:
    id: str
    name: str
    location: str
    body: RobotBody
    battery: float = 86.0
    state: str = "IDLE"
    mission_id: Optional[str] = None
    dest_id: Optional[str] = None
    dest_name: Optional[str] = None
    dest_x: float = 0.0
    dest_y: float = 0.0
    total_distance: float = 0.0
    companions: int = 0
    visitor_lagging: bool = False
    route_blocked: bool = False
    destination_unavailable: bool = False
    low_battery_alerted: bool = False
    alerts: list[AlertDto] = field(default_factory=list)

    @property
    def x(self) -> float:
        return self.body.pose()[0]

    @property
    def y(self) -> float:
        return self.body.pose()[1]

    def clear_mission(self) -> None:
        self.mission_id = None
        self.dest_id = None
        self.dest_name = None
        self.companions = 0

    @property
    def blocked(self) -> bool:
        return self.visitor_lagging or self.route_blocked or self.destination_unavailable


class RobotUnavailable(Exception):
    """El robot no responde (desconexión forzada)."""


class GuideService:
    def __init__(self, clock: Callable[[], float] = time.monotonic,
                 bodies: Optional[dict[str, RobotBody]] = None) -> None:
        self._clock = clock
        self._lock = Lock()
        self._connected = True
        self._reject_next = False
        self._last_tick = clock()
        bodies = bodies or {}

        def body(robot_id: str) -> RobotBody:
            return bodies.get(robot_id) or KinematicBody(SAFE_X, SAFE_Y)

        self._robots: dict[str, _Robot] = {
            "g1-01": _Robot("g1-01", "Unitree G1 · Recepción", "Hall principal", body("g1-01")),
            "g1-02": _Robot("g1-02", "Unitree G1 · Edificio B", "Planta baja B", body("g1-02")),
        }
        self._ticker: Optional[Thread] = None

    def bodies(self) -> list[RobotBody]:
        return [r.body for r in self._robots.values()]

    def start_background_ticks(self, interval_s: float = 0.1) -> None:
        """Avanza la simulación/navegación periódicamente aunque la app no consulte.

        Necesario con el gemelo digital: el navegador corrige el rumbo a 10 Hz.
        """
        def run() -> None:
            while True:
                time.sleep(interval_s)
                with self._lock:
                    self._advance()

        self._ticker = Thread(target=run, name="guide-ticker", daemon=True)
        self._ticker.start()

    # ------------------------------------------------------------ helpers

    @staticmethod
    def _now() -> str:
        return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

    @staticmethod
    def _new_id(prefix: str) -> str:
        # Únicos entre reinicios del backend: la app guarda misiones/alertas por id.
        return f"{prefix}-{uuid.uuid4().hex[:10]}"

    def _ok(self, message: str, mission_id: Optional[str] = None) -> CommandResponseDto:
        return CommandResponseDto(commandId=self._new_id("cmd"), status="CONFIRMED",
                                  message=message, timestamp=self._now(), missionId=mission_id)

    def _rejected(self, message: str) -> CommandResponseDto:
        return CommandResponseDto(commandId=self._new_id("cmd"), status="REJECTED",
                                  message=message, timestamp=self._now())

    def _alert(self, r: _Robot, type_: str, severity: str, message: str) -> None:
        r.alerts.insert(0, AlertDto(id=self._new_id("al"), type=type_, severity=severity,
                                    message=message, timestamp=self._now()))

    def _link(self) -> None:
        if not self._connected:
            raise RobotUnavailable("El robot no responde (desconexión simulada)")

    def _robot(self, robot_id: str) -> _Robot:
        r = self._robots.get(robot_id)
        if r is None:
            raise KeyError(f"Robot {robot_id} inexistente")
        return r

    def _robot_by_mission(self, mission_id: str) -> Optional[_Robot]:
        return next((r for r in self._robots.values() if r.mission_id == mission_id), None)

    def _consume_rejection(self) -> Optional[CommandResponseDto]:
        if self._reject_next:
            self._reject_next = False
            return self._rejected("Comando rechazado por el robot (escenario forzado)")
        return None

    # ------------------------------------------------------------ simulación

    def _advance(self) -> None:
        """Avanza todos los robots según el tiempo real transcurrido."""
        now = self._clock()
        dt = max(0.0, now - self._last_tick)
        self._last_tick = now
        for r in self._robots.values():
            self._step(r, dt)

    def _step(self, r: _Robot, dt: float) -> None:
        for event in r.body.drain_events():
            self._on_body_event(r, event)

        if r.state == "GUIDING":
            if r.blocked:
                r.state = "WAITING"
                r.body.halt()
            elif r.body.drive_to(r.dest_x, r.dest_y, dt):
                r.state = "ARRIVED"
                self._alert(r, "INFO", "INFO", f"Llegada a {r.dest_name}. Misión completada.")
        elif r.state == "WAITING":
            r.body.halt()
            if not r.blocked:
                r.state = "GUIDING"
        elif r.state == "RETURNING":
            if r.body.drive_to(SAFE_X, SAFE_Y, dt):
                r.state = "IDLE"
                r.clear_mission()
        else:
            r.body.halt()

        vx, vy, _ = r.body.velocity()
        moving = math.hypot(vx, vy) > 0.05
        x, y, _ = r.body.pose()
        docked = r.state == "IDLE" and math.hypot(x - SAFE_X, y - SAFE_Y) <= DOCK_RADIUS_M
        if docked:
            # Sin esto, con el gemelo (que avanza en segundo plano) la batería llegaba a 0 %
            # en ~1 h de backend levantado y la app rechazaba toda misión.
            r.battery = min(100.0, r.battery + CHARGE_PER_S * dt)
            if r.battery >= LOW_BATTERY_THRESHOLD + 10:
                r.low_battery_alerted = False
        else:
            drain = BATTERY_DRAIN_MOVING_PER_S if moving else BATTERY_DRAIN_IDLE_PER_S
            r.battery = max(0.0, r.battery - drain * dt)
        if r.battery < LOW_BATTERY_THRESHOLD and not r.low_battery_alerted:
            r.low_battery_alerted = True
            self._alert(r, "LOW_BATTERY", "CRITICAL",
                        f"Batería baja ({int(r.battery)}%). Se recomienda regresar al punto seguro.")

    def _on_body_event(self, r: _Robot, event: str) -> None:
        if event == "FALLEN":
            self._alert(r, "INFO", "CRITICAL",
                        "Caída detectada en el gemelo digital. Se reinicia el robot en el punto seguro.")
            if r.mission_id is not None:
                r.state = "STOPPED"
            elif r.state == "RETURNING":
                r.state = "IDLE"
        elif event == "DISCONNECTED":
            self._alert(r, "DISCONNECTED", "CRITICAL", "Se perdió la comunicación DDS con el G1.")
        elif event == "READY":
            self._alert(r, "INFO", "INFO", "G1 de pie y listo (política de locomoción activa).")

    # ------------------------------------------------------------ API pública

    def health(self) -> HealthDto:
        with self._lock:
            self._link()
            return HealthDto(status="ok", service="unitree-g1-backend")

    def robots(self) -> list[RobotDto]:
        with self._lock:
            self._link()
            return [RobotDto(robotId=r.id, name=r.name, location=r.location) for r in self._robots.values()]

    def telemetry(self, robot_id: str) -> RobotTelemetryDto:
        with self._lock:
            self._link()
            r = self._robot(robot_id)
            self._advance()
            remaining = (math.hypot(r.dest_x - r.x, r.dest_y - r.y)
                         if r.dest_id is not None and r.state != "RETURNING" else 0.0)
            if r.mission_id is None:
                progress = 0
            elif r.state == "ARRIVED":
                progress = 100
            elif r.total_distance <= 0:
                progress = 0
            else:
                progress = min(99, max(0, int((r.total_distance - remaining) / r.total_distance * 100)))
            x, y, yaw = r.body.pose()
            vx, vy, omega = r.body.velocity()
            return RobotTelemetryDto(
                robotId=r.id, connection="CONNECTED" if r.body.connected() else "DISCONNECTED",
                battery=int(r.battery),
                operationalState=r.state, missionId=r.mission_id, missionProgress=progress,
                position=PositionDto(x=x, y=y, yaw=yaw),
                velocity=VelocityDto(vx=vx, vy=vy, omega=omega),
                alerts=list(r.alerts), updatedAt=self._now(),
                destinationId=r.dest_id, destinationName=r.dest_name,
                remainingDistance=remaining, companions=r.companions,
            )

    def start_guide(self, req: GuideMissionRequestDto) -> CommandResponseDto:
        with self._lock:
            self._link()
            self._advance()
            if (rej := self._consume_rejection()) is not None:
                return rej
            r = self._robot(req.robotId)
            if r.state not in ("IDLE", "ARRIVED"):
                return self._rejected("El robot ya tiene una misión en curso")
            if r.battery < LOW_BATTERY_THRESHOLD:
                return self._rejected("Batería insuficiente para iniciar la guía")
            r.mission_id = self._new_id("mis")
            r.dest_id, r.dest_name = req.destinationId, req.destinationName
            r.dest_x, r.dest_y = req.x, req.y
            r.total_distance = math.hypot(req.x - r.x, req.y - r.y)
            r.companions = req.companions
            r.state = "GUIDING"
            return self._ok(f"Misión de guía hacia {req.destinationName} aceptada", r.mission_id)

    def _mission_command(self, mission_id: str, allowed: set[str], to: str, message: str) -> CommandResponseDto:
        with self._lock:
            self._link()
            self._advance()
            if (rej := self._consume_rejection()) is not None:
                return rej
            r = self._robot_by_mission(mission_id)
            if r is None:
                return self._rejected(f"Misión {mission_id} inexistente")
            if r.state not in allowed:
                return self._rejected(f"No se puede ejecutar en estado {r.state}")
            r.state = to
            return self._ok(message, mission_id)

    def pause(self, mission_id: str) -> CommandResponseDto:
        return self._mission_command(mission_id, {"GUIDING", "WAITING"}, "PAUSED", "Misión pausada")

    def resume(self, mission_id: str) -> CommandResponseDto:
        return self._mission_command(mission_id, {"PAUSED", "STOPPED"}, "GUIDING", "Misión reanudada")

    def cancel(self, mission_id: str) -> CommandResponseDto:
        with self._lock:
            self._link()
            self._advance()
            if (rej := self._consume_rejection()) is not None:
                return rej
            r = self._robot_by_mission(mission_id)
            if r is None:
                return self._rejected(f"Misión {mission_id} inexistente")
            r.state = "IDLE"
            r.clear_mission()
            return self._ok("Misión cancelada. El robot queda disponible en su posición actual.")

    def return_safe(self, robot_id: str) -> CommandResponseDto:
        with self._lock:
            self._link()
            self._advance()
            if (rej := self._consume_rejection()) is not None:
                return rej
            r = self._robot(robot_id)
            r.clear_mission()
            r.state = "RETURNING"
            return self._ok(f"Regresando a {SAFE_POINT_NAME}")

    def stop(self, robot_id: str) -> CommandResponseDto:
        with self._lock:
            self._link()
            self._advance()
            if (rej := self._consume_rejection()) is not None:
                return rej
            r = self._robot(robot_id)
            r.state = "STOPPED"
            return self._ok("Detención operativa por software ejecutada", r.mission_id)

    def ack_alert(self, alert_id: str) -> CommandResponseDto:
        with self._lock:
            self._link()
            if (rej := self._consume_rejection()) is not None:
                return rej
            for r in self._robots.values():
                for i, a in enumerate(r.alerts):
                    if a.id == alert_id:
                        r.alerts[i] = a.model_copy(update={"acknowledged": True})
                        return self._ok("Alerta reconocida")
            return self._rejected(f"Alerta {alert_id} inexistente")

    def scenario(self, robot_id: str, scenario: Scenario) -> None:
        """No pasa por _link(): los escenarios deben poder forzarse aun "desconectado"."""
        with self._lock:
            self._advance()
            r = self._robot(robot_id)
            if scenario is Scenario.LOW_BATTERY:
                r.battery = 12.0
                r.low_battery_alerted = False
            elif scenario is Scenario.DISCONNECT:
                self._connected = False
            elif scenario is Scenario.REJECT_NEXT_COMMAND:
                self._reject_next = True
            elif scenario is Scenario.VISITOR_LAGGING:
                r.visitor_lagging = True
                self._alert(r, "VISITOR_LAGGING", "WARNING", "Visitante rezagado: el robot espera al grupo.")
            elif scenario is Scenario.DESTINATION_UNAVAILABLE:
                r.destination_unavailable = True
                self._alert(r, "DESTINATION_UNAVAILABLE", "WARNING", "Destino temporalmente inaccesible (aula cerrada).")
            elif scenario is Scenario.ROUTE_BLOCKED:
                r.route_blocked = True
                self._alert(r, "ROUTE_BLOCKED", "CRITICAL", "Ruta bloqueada por un obstáculo en el pasillo.")
            elif scenario is Scenario.RECOVER:
                self._connected = True
                self._reject_next = False
                r.visitor_lagging = r.route_blocked = r.destination_unavailable = False
                if r.battery < 60:
                    r.battery = 85.0
                    r.low_battery_alerted = False
                self._alert(r, "INFO", "INFO", "Situación normalizada.")


# ---------------------------------------------------------------- Router

def build_router(service: GuideService) -> APIRouter:
    router = APIRouter(prefix="/api/v1", tags=["guía universitaria"])

    def call(fn, *args):
        try:
            return fn(*args)
        except RobotUnavailable as e:
            raise HTTPException(status_code=503, detail=str(e)) from e
        except KeyError as e:
            raise HTTPException(status_code=404, detail=e.args[0]) from e

    @router.get("/robots", response_model=list[RobotDto])
    def robots():
        return call(service.robots)

    @router.get("/robots/{robot_id}/telemetry", response_model=RobotTelemetryDto)
    def telemetry(robot_id: str):
        return call(service.telemetry, robot_id)

    @router.post("/missions/guide", response_model=CommandResponseDto)
    def start_guide(body: GuideMissionRequestDto):
        return call(service.start_guide, body)

    @router.post("/missions/{mission_id}/pause", response_model=CommandResponseDto)
    def pause(mission_id: str):
        return call(service.pause, mission_id)

    @router.post("/missions/{mission_id}/resume", response_model=CommandResponseDto)
    def resume(mission_id: str):
        return call(service.resume, mission_id)

    @router.post("/missions/{mission_id}/cancel", response_model=CommandResponseDto)
    def cancel(mission_id: str):
        return call(service.cancel, mission_id)

    @router.post("/robots/{robot_id}/return-safe", response_model=CommandResponseDto)
    def return_safe(robot_id: str):
        return call(service.return_safe, robot_id)

    @router.post("/robots/{robot_id}/stop", response_model=CommandResponseDto)
    def stop(robot_id: str):
        return call(service.stop, robot_id)

    @router.post("/alerts/{alert_id}/ack", response_model=CommandResponseDto)
    def ack_alert(alert_id: str):
        return call(service.ack_alert, alert_id)

    @router.post("/simulation/{robot_id}/{scenario}", status_code=204)
    def scenario(robot_id: str, scenario: Scenario):
        call(service.scenario, robot_id, scenario)
        return Response(status_code=204)

    return router
