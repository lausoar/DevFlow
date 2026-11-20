#!/usr/bin/env python3
"""Build and exercise the real production Compose stack.

Requires Docker Compose v2 and ``websocket-client`` (``pip install websocket-client``).
All accounts and secrets are disposable; public demo-account seeding stays disabled.
Logs are retained under SMOKE_LOG_DIR and the Compose project is always removed with -v.
"""

from __future__ import annotations

import base64
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import websocket


ROOT = Path(__file__).resolve().parent.parent
COMPOSE_FILE = ROOT / "docker-compose.prod.yml"


def free_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def stomp_frame(command: str, headers: dict[str, str], body: str = "") -> str:
    lines = [command, *(f"{key}:{value}" for key, value in headers.items())]
    return "\n".join(lines) + "\n\n" + body + "\x00"


class ProductionSmoke:
    def __init__(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory(prefix="devflow-prod-smoke-")
        self.work_dir = Path(self.temp_dir.name)
        self.project = f"devflow-smoke-{os.getpid()}-{secrets.token_hex(3)}"
        self.port = free_port()
        self.origin = f"http://127.0.0.1:{self.port}"
        log_dir = Path(os.environ.get("SMOKE_LOG_DIR", ROOT / "artifacts" / "production-smoke"))
        self.log_dir = log_dir if log_dir.is_absolute() else ROOT / log_dir
        self.env_file = self.work_dir / "smoke.env"
        self.compose = [
            "docker", "compose", "--env-file", str(self.env_file),
            "-f", str(COMPOSE_FILE), "-p", self.project,
        ]
        values = {
            "HTTP_PORT": str(self.port),
            "HTTPS_PORT": str(free_port()),
            "TAG": f"smoke-{os.getpid()}",
            "DB_PASSWORD": secrets.token_urlsafe(24),
            "REDIS_PASSWORD": "",
            "MINIO_ACCESS_KEY": f"smoke{secrets.token_hex(8)}",
            "MINIO_SECRET_KEY": secrets.token_urlsafe(32),
            "MINIO_BUCKET": "devflow-smoke",
            "JWT_SECRET": base64.b64encode(secrets.token_bytes(48)).decode("ascii"),
            "AI_SERVICE_TOKEN": secrets.token_urlsafe(32),
            "DEVFLOW_SEED_DEMO_ACCOUNTS": "false",
            "CORS_ALLOWED_ORIGINS": self.origin,
            "WS_ALLOWED_ORIGINS": self.origin,
            "PUBLIC_API_BASE_URL": "/api/v1",
            "PUBLIC_AI_BASE_URL": "/ai-api/api/v1",
            "AI_PROVIDER": "openai",
            "OPENAI_API_KEY": "",
            "EMBEDDING_PROVIDER": "api",
            "EMBEDDING_API_KEY": "",
            "LOG_LEVEL": "warn",
            "SWAGGER_ENABLED": "false",
        }
        self.env_file.write_text(
            "".join(f"{key}={value}\n" for key, value in values.items()), encoding="utf-8"
        )

    def run(self) -> None:
        if not shutil.which("docker"):
            raise RuntimeError("Docker is required for the production smoke test")
        subprocess.run(["docker", "compose", "version"], check=True, capture_output=True)
        print(f"Building and starting disposable Compose project {self.project}", flush=True)
        subprocess.run(
            self.compose + ["up", "-d", "--build"], check=True, cwd=ROOT, timeout=1800
        )
        self.wait_for_healthy_backend()
        self.wait_for_http("/", "frontend")
        health = self.wait_for_http("/ai-api/health", "AI health", json_response=True)
        assert health.get("status") == "ok", health

        first = self.register_login_and_fixtures("Alpha")
        second = self.register_login_and_fixtures("Beta")
        self.check_offline_ai(first["token"])
        self.check_stomp(first, second)
        print("Production smoke passed", flush=True)

    def wait_for_healthy_backend(self, timeout: int = 600) -> None:
        deadline = time.monotonic() + timeout
        last = "missing"
        while time.monotonic() < deadline:
            container_id = subprocess.run(
                self.compose + ["ps", "-q", "backend"], check=True, text=True,
                capture_output=True, cwd=ROOT,
            ).stdout.strip()
            if container_id:
                inspected = json.loads(subprocess.run(
                    ["docker", "inspect", container_id], check=True, text=True,
                    capture_output=True,
                ).stdout)[0]
                state = inspected["State"]
                last = state.get("Health", {}).get("Status", state.get("Status", "unknown"))
                if last == "healthy":
                    print("Backend container is healthy on the management-port probe", flush=True)
                    return
                if last in {"unhealthy", "dead", "exited"}:
                    raise AssertionError(f"Backend container state: {last}")
            time.sleep(3)
        raise TimeoutError(f"Backend did not become healthy (last state: {last})")

    def wait_for_http(self, path: str, name: str, json_response: bool = False):
        deadline = time.monotonic() + 180
        last_error: Exception | None = None
        while time.monotonic() < deadline:
            try:
                result = self.request("GET", path, json_response=json_response, timeout=10)
                print(f"{name} is reachable through Nginx", flush=True)
                return result
            except Exception as exc:  # retry only until the readiness deadline
                last_error = exc
                time.sleep(2)
        raise TimeoutError(f"{name} was not ready: {last_error}")

    def request(self, method: str, path: str, token: str | None = None,
                payload: dict | None = None, json_response: bool = True, timeout: int = 30):
        data = None if payload is None else json.dumps(payload).encode("utf-8")
        headers = {"Origin": self.origin}
        if data is not None:
            headers["Content-Type"] = "application/json"
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = Request(self.origin + path, data=data, headers=headers, method=method)
        try:
            with urlopen(request, timeout=timeout) as response:  # noqa: S310 - fixed local URL
                raw = response.read().decode("utf-8")
                assert response.status == 200, (path, response.status, raw)
        except HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            raise AssertionError(f"{method} {path} returned {exc.code}: {body}") from exc
        if json_response:
            result = json.loads(raw)
            if isinstance(result, dict) and "code" in result:
                assert result["code"] == 200, (method, path, result)
            return result
        assert "<html" in raw.lower(), f"Frontend did not return HTML: {raw[:200]}"
        return raw

    def register_login_and_fixtures(self, label: str) -> dict[str, str]:
        suffix = secrets.token_hex(4)
        username = f"smoke{suffix}"
        password = f"Sm0ke!{secrets.token_urlsafe(12)}aA"
        registered = self.request("POST", "/api/v1/auth/register", payload={
            "username": username,
            "realName": f"Smoke {label}",
            "email": f"{username}@example.invalid",
            "password": password,
        })
        logged_in = self.request("POST", "/api/v1/auth/login", payload={
            "username": username, "password": password,
        })
        token = logged_in["data"]["accessToken"]
        me = self.request("GET", "/api/v1/auth/me", token=token)
        assert me["data"]["id"] == registered["data"]["user"]["id"]
        enterprise = self.request("POST", "/api/v1/enterprises", token=token, payload={
            "name": f"Smoke {label} {suffix}",
        })["data"]
        project = self.request("POST", "/api/v1/projects", token=token, payload={
            "name": f"Smoke Project {label}", "projectKey": f"SM{suffix.upper()}",
        })["data"]
        channels = self.request("GET", "/api/v1/channels", token=token)["data"]
        channel = next(item for item in channels if item["projectId"] == project["id"])
        print(f"Registered/login/API fixtures passed for isolated tenant {label}", flush=True)
        return {
            "token": token, "enterpriseId": enterprise["id"],
            "projectId": project["id"], "channelId": channel["id"],
        }

    def check_offline_ai(self, token: str) -> None:
        result = self.request("POST", "/ai-api/api/v1/chat/completions", token=token, payload={
            "messages": [{"role": "user", "content": "hello"}],
            "stream": False, "max_tokens": 16,
        })
        assert result["model"] == "offline-fallback", result
        assert result["content"].strip(), result
        print("Authenticated offline AI chat passed through /ai-api/api/v1", flush=True)

    def open_stomp(self, token: str) -> websocket.WebSocket:
        url = f"ws://127.0.0.1:{self.port}/api/v1/ws/000/{secrets.token_hex(4)}/websocket"
        ws = websocket.create_connection(url, origin=self.origin, timeout=15)
        assert ws.recv() == "o", "SockJS did not send its open frame"
        ws.send(stomp_frame("CONNECT", {
            "accept-version": "1.2", "heart-beat": "0,0",
            "Authorization": f"Bearer {token}",
        }))
        self.wait_for_frame(ws, "CONNECTED")
        return ws

    @staticmethod
    def receive_frames(ws: websocket.WebSocket) -> list[str]:
        while True:
            raw = ws.recv()
            if raw == "h":
                continue
            if not raw:
                raise websocket.WebSocketConnectionClosedException("SockJS connection closed")
            if raw.startswith("a"):
                payload = "".join(json.loads(raw[1:]))
                return [frame.strip("\r\n") for frame in payload.split("\x00") if frame.strip()]

    def wait_for_frame(self, ws: websocket.WebSocket, command: str) -> str:
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            for frame in self.receive_frames(ws):
                if frame.startswith(command + "\n") or frame == command:
                    return frame
        raise TimeoutError(f"Did not receive STOMP {command}")

    def subscribe(self, ws: websocket.WebSocket, subscription_id: str, destination: str) -> None:
        ws.send(stomp_frame("SUBSCRIBE", {
            "id": subscription_id, "destination": destination, "receipt": subscription_id,
        }))

    def assert_subscription_rejected(self, token: str, destination: str) -> None:
        ws = self.open_stomp(token)
        try:
            self.subscribe(ws, "denied", destination)
            while True:
                for frame in self.receive_frames(ws):
                    if frame.startswith("ERROR\n"):
                        return
                    if frame.startswith("RECEIPT\n"):
                        raise AssertionError(f"Foreign subscription was accepted: {destination}")
        except websocket.WebSocketConnectionClosedException:
            return
        finally:
            try:
                ws.close()
            except Exception:
                pass

    def check_stomp(self, first: dict[str, str], second: dict[str, str]) -> None:
        ws = self.open_stomp(first["token"])
        try:
            self.subscribe(ws, "presence", f"/topic/presence.{first['enterpriseId']}")
            self.wait_for_frame(ws, "RECEIPT")
            destination = f"/topic/chat.events.{first['channelId']}"
            self.subscribe(ws, "events", destination)
            self.wait_for_frame(ws, "RECEIPT")
            self.request("POST", f"/api/v1/channels/{first['channelId']}/messages",
                         token=first["token"], payload={"content": "production smoke"})
            event = self.wait_for_frame(ws, "MESSAGE")
            headers, body = event.split("\n\n", 1)
            assert f"destination:{destination}" in headers, headers
            assert json.loads(body)["channelId"] == first["channelId"]
        finally:
            ws.close()
        print("SockJS WebSocket upgrade, STOMP CONNECT, and channel event delivery passed", flush=True)

        self.assert_subscription_rejected(
            first["token"], f"/topic/presence.{second['enterpriseId']}"
        )
        self.assert_subscription_rejected(
            first["token"], f"/topic/chat.events.{second['channelId']}"
        )
        print("Foreign presence and channel subscriptions were rejected", flush=True)

    def cleanup(self) -> None:
        try:
            self.log_dir.mkdir(parents=True, exist_ok=True)
            with (self.log_dir / "compose.log").open("w", encoding="utf-8") as output:
                subprocess.run(self.compose + ["logs", "--no-color"], cwd=ROOT,
                               stdout=output, stderr=subprocess.STDOUT, timeout=120)
            with (self.log_dir / "compose-ps.log").open("w", encoding="utf-8") as output:
                subprocess.run(self.compose + ["ps", "-a"], cwd=ROOT,
                               stdout=output, stderr=subprocess.STDOUT, timeout=30)
        except Exception as exc:
            print(f"Could not collect all Compose logs: {exc}", flush=True)
        finally:
            subprocess.run(self.compose + ["down", "-v", "--remove-orphans"], cwd=ROOT,
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=180)
            self.temp_dir.cleanup()


def main() -> None:
    smoke = ProductionSmoke()
    try:
        smoke.run()
    finally:
        smoke.cleanup()


if __name__ == "__main__":
    main()
