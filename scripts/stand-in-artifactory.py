#!/usr/bin/env python3
"""A stand-in Artifactory for scripts/acceptance-artifacts.sh (ADR-021).

Answers /api/storage for a chart and a container image, 404 for everything else, and refuses
every method but GET. It exists so the artifact pass can be run end to end - binding, service,
Collector, Connector, HTTP - without a JFrog host, which no build environment here can reach.

It is not evidence about Artifactory. The bodies are hand-written from knowledge of the API
rather than recorded off a real instance, which is the weakest of the three kinds of check
ADR-019 describes, and CHECKLIST.md keeps the live verification open for exactly that reason.

/methods reports every request it saw, so the run can assert from the far side of the socket
that Tower issued nothing but GETs. That assertion is the one ADR-001 cannot make any other
way: the architecture rule proves no write path is named, and this proves none was taken.
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

CHART = "/api/storage/helm-local/api-2.5.0-abc1234.tgz"
IMAGE = "/api/storage/docker-local/acme/api/2.5.0-abc1234/manifest.json"

HELD = {
    CHART: {
        "repo": "helm-local", "path": "/api-2.5.0-abc1234.tgz",
        "created": "2024-05-01T10:00:00.000+02:00",
        "downloadUri": "http://127.0.0.1:9099/artifactory/helm-local/api-2.5.0-abc1234.tgz",
        "size": "18342",
        "checksums": {"sha1": "aaa", "md5": "bbb", "sha256": "ccc"},
    },
    IMAGE: {
        "repo": "docker-local", "path": "/acme/api/2.5.0-abc1234/manifest.json",
        "created": "2024-05-01T10:00:00.000Z",
        "downloadUri": "http://127.0.0.1:9099/artifactory/docker-local/acme/api/2.5.0-abc1234/manifest.json",
        "size": "1442",
        "checksums": {"sha1": "ddd", "sha256": "eee"},
    },
}

METHODS = []


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _record(self):
        METHODS.append(self.command + " " + self.path)

    def _send(self, status, body):
        payload = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        self._record()
        if self.path == "/methods":
            self._send(200, METHODS)
        elif self.path == "/api/repositories":
            self._send(200, [{"key": "helm-local"}, {"key": "docker-local"}])
        elif self.path in HELD:
            self._send(200, HELD[self.path])
        else:
            self._send(404, {"errors": [{"status": 404, "message": "Not Found"}]})

    def do_POST(self):
        self._record()
        self._send(405, {"errors": [{"status": 405, "message": "read-only stand-in"}]})

    do_PUT = do_POST
    do_DELETE = do_POST


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9099
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
