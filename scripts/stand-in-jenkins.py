#!/usr/bin/env python3
"""A stand-in Jenkins for driving the pipeline screens (ADR-020).

Answers the one API path the Connector reads - /job/.../api/json with a tree expression - for a
single job with three builds: one that succeeded and carries a VERSION parameter, one that failed,
and one still running. Those three are exactly the cases the report has to tell apart.

Records every request method so a drive can assert, from the far side of the socket, that Tower
issued nothing but GET. That assertion matters more here than anywhere else in Tower: a Jenkins
token that can read a job can very often start it.

It is not evidence about Jenkins. The bodies are hand-written from knowledge of the API rather
than recorded off a real instance, and CHECKLIST.md keeps the live verification open for exactly
that reason.
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

METHODS = []

BUILDS = [
    {
        "number": 43, "result": "SUCCESS", "timestamp": 1717236000000,
        "url": "http://127.0.0.1:9098/job/deploy-uat/43/", "displayName": "#43",
        "actions": [
            {"environment": {"BUILD_ID": "43"}},
            {"parameters": [{"name": "VERSION", "value": "2.5.0"}]},
        ],
    },
    {
        "number": 42, "result": "FAILURE", "timestamp": 1717149600000,
        "url": "http://127.0.0.1:9098/job/deploy-uat/42/", "displayName": "#42",
        "actions": [{"parameters": [{"name": "VERSION", "value": "2.4.9"}]}],
    },
    {
        # Still going: Jenkins reports a null result, which the Connector turns
        # into the outcome "RUNNING" rather than an empty string.
        "number": 44, "result": None, "timestamp": 1717322400000,
        "url": "http://127.0.0.1:9098/job/deploy-uat/44/", "displayName": "#44",
        "actions": [{"parameters": [{"name": "VERSION", "value": "2.5.1"}]}],
    },
]


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _send(self, status, body):
        payload = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        METHODS.append("GET " + self.path)
        if self.path == "/methods":
            self._send(200, METHODS)
        elif "/api/json" in self.path:
            # tree=name is the connection test; anything else is a run read.
            if "tree=name" in self.path:
                self._send(200, {"name": "deploy-uat"})
            else:
                self._send(200, {"builds": BUILDS})
        else:
            self._send(404, {"message": "no such job"})

    def do_POST(self):
        METHODS.append(self.command + " " + self.path)
        self._send(405, {"message": "read-only stand-in"})

    do_PUT = do_POST
    do_DELETE = do_POST


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 9098
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
