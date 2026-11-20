"""Contrato REST consumido por la app Android Robot_guia_universitario."""

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.guide import GuideService, build_router


class FakeClock:
    def __init__(self) -> None:
        self.t = 0.0

    def __call__(self) -> float:
        return self.t


@pytest.fixture
def clock() -> FakeClock:
    return FakeClock()


@pytest.fixture
def client(clock: FakeClock) -> TestClient:
    app = FastAPI()
    app.include_router(build_router(GuideService(clock=clock)))
    return TestClient(app)


def start(client: TestClient, x: float = 6.0, y: float = 1.0) -> str:
    body = {"robotId": "g1-01", "destinationId": "aula-101", "destinationName": "Aula 101",
            "x": x, "y": y, "companions": 2}
    res = client.post("/api/v1/missions/guide", json=body)
    assert res.status_code == 200
    assert res.json()["status"] == "CONFIRMED"
    return res.json()["missionId"]


def telemetry(client: TestClient) -> dict:
    res = client.get("/api/v1/robots/g1-01/telemetry")
    assert res.status_code == 200
    return res.json()


def test_robots_list(client: TestClient) -> None:
    res = client.get("/api/v1/robots")
    assert res.status_code == 200
    assert [r["robotId"] for r in res.json()] == ["g1-01", "g1-02"]


def test_telemetry_starts_idle_at_safe_point(client: TestClient) -> None:
    t = telemetry(client)
    assert t["operationalState"] == "IDLE"
    assert t["position"]["x"] == 1.0 and t["position"]["y"] == 1.0
    assert t["updatedAt"].endswith("Z")


def test_unknown_robot_is_404(client: TestClient) -> None:
    assert client.get("/api/v1/robots/nope/telemetry").status_code == 404


def test_guide_mission_moves_over_time_and_arrives(client: TestClient, clock: FakeClock) -> None:
    mission = start(client)  # 5 m hacia +x
    clock.t = 4.0  # 0.5 m/s -> 2 m
    t = telemetry(client)
    assert t["operationalState"] == "GUIDING"
    assert t["missionId"] == mission
    assert t["position"]["x"] == pytest.approx(3.0)
    assert t["missionProgress"] == 40
    clock.t = 20.0
    t = telemetry(client)
    assert t["operationalState"] == "ARRIVED"
    assert t["missionProgress"] == 100
    assert t["alerts"][0]["message"].startswith("Llegada a Aula 101")


def test_second_mission_is_rejected_while_guiding(client: TestClient) -> None:
    start(client)
    body = {"robotId": "g1-01", "destinationId": "x", "destinationName": "X", "x": 2, "y": 2, "companions": 0}
    assert client.post("/api/v1/missions/guide", json=body).json()["status"] == "REJECTED"


def test_pause_resume_cancel(client: TestClient) -> None:
    mission = start(client)
    assert client.post(f"/api/v1/missions/{mission}/pause").json()["status"] == "CONFIRMED"
    assert telemetry(client)["operationalState"] == "PAUSED"
    assert client.post(f"/api/v1/missions/{mission}/pause").json()["status"] == "REJECTED"
    assert client.post(f"/api/v1/missions/{mission}/resume").json()["status"] == "CONFIRMED"
    assert client.post(f"/api/v1/missions/{mission}/cancel").json()["status"] == "CONFIRMED"
    t = telemetry(client)
    assert t["operationalState"] == "IDLE" and t["missionId"] is None


def test_return_safe_goes_back_to_reception(client: TestClient, clock: FakeClock) -> None:
    start(client)
    clock.t = 20.0
    telemetry(client)
    assert client.post("/api/v1/robots/g1-01/return-safe").json()["status"] == "CONFIRMED"
    assert telemetry(client)["operationalState"] == "RETURNING"
    clock.t = 40.0
    t = telemetry(client)
    assert t["operationalState"] == "IDLE"
    assert (t["position"]["x"], t["position"]["y"]) == (1.0, 1.0)


def test_stop_then_resume(client: TestClient) -> None:
    mission = start(client)
    res = client.post("/api/v1/robots/g1-01/stop").json()
    assert res["status"] == "CONFIRMED" and res["missionId"] == mission
    assert telemetry(client)["operationalState"] == "STOPPED"
    assert client.post(f"/api/v1/missions/{mission}/resume").json()["status"] == "CONFIRMED"


def test_ack_alert(client: TestClient) -> None:
    assert client.post("/api/v1/simulation/g1-01/ROUTE_BLOCKED").status_code == 204
    alert = telemetry(client)["alerts"][0]
    assert alert["type"] == "ROUTE_BLOCKED" and not alert["acknowledged"]
    assert client.post(f"/api/v1/alerts/{alert['id']}/ack").json()["status"] == "CONFIRMED"
    assert telemetry(client)["alerts"][0]["acknowledged"]


def test_route_blocked_puts_robot_waiting_until_recover(client: TestClient, clock: FakeClock) -> None:
    start(client)
    client.post("/api/v1/simulation/g1-01/ROUTE_BLOCKED")
    clock.t = 2.0
    assert telemetry(client)["operationalState"] == "WAITING"
    client.post("/api/v1/simulation/g1-01/RECOVER")
    clock.t = 3.0
    assert telemetry(client)["operationalState"] == "GUIDING"


def test_disconnect_returns_503_until_recover(client: TestClient) -> None:
    client.post("/api/v1/simulation/g1-01/DISCONNECT")
    assert client.get("/api/v1/robots/g1-01/telemetry").status_code == 503
    assert client.post("/api/v1/simulation/g1-01/RECOVER").status_code == 204
    assert client.get("/api/v1/robots/g1-01/telemetry").status_code == 200


def test_reject_next_command(client: TestClient) -> None:
    client.post("/api/v1/simulation/g1-01/REJECT_NEXT_COMMAND")
    body = {"robotId": "g1-01", "destinationId": "x", "destinationName": "X", "x": 2, "y": 2, "companions": 0}
    assert client.post("/api/v1/missions/guide", json=body).json()["status"] == "REJECTED"
    assert client.post("/api/v1/missions/guide", json=body).json()["status"] == "CONFIRMED"


def test_low_battery_blocks_new_missions(client: TestClient) -> None:
    client.post("/api/v1/simulation/g1-01/LOW_BATTERY")
    t = telemetry(client)
    assert t["battery"] == 12
    assert any(a["type"] == "LOW_BATTERY" for a in t["alerts"])
    body = {"robotId": "g1-01", "destinationId": "x", "destinationName": "X", "x": 2, "y": 2, "companions": 0}
    assert client.post("/api/v1/missions/guide", json=body).json()["status"] == "REJECTED"


def test_unknown_scenario_is_422(client: TestClient) -> None:
    assert client.post("/api/v1/simulation/g1-01/FOO").status_code == 422


def test_battery_charges_while_idle_at_reception(client: TestClient, clock: FakeClock) -> None:
    client.post("/api/v1/simulation/g1-01/LOW_BATTERY")
    assert telemetry(client)["battery"] == 12
    clock.t = 100.0  # 100 s en la base a 0,2 %/s
    assert telemetry(client)["battery"] == 32


def test_battery_drains_when_idle_away_from_reception(client: TestClient, clock: FakeClock) -> None:
    start(client)
    clock.t = 20.0
    telemetry(client)  # llegó al aula
    mission = client.post("/api/v1/robots/g1-01/stop").json()["missionId"]
    client.post(f"/api/v1/missions/{mission}/cancel")
    before = telemetry(client)["battery"]
    clock.t = 120.0
    assert telemetry(client)["battery"] < before
