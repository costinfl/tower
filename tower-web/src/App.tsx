import { useEffect, useState } from "react";
import { getHealth } from "./api/client";

type ConnectionState =
  | { kind: "loading" }
  | { kind: "connected"; status: string }
  | { kind: "error"; message: string };

export default function App() {
  const [connection, setConnection] = useState<ConnectionState>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;

    getHealth()
      .then((health) => {
        if (!cancelled) {
          setConnection({ kind: "connected", status: health.status });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          const message = error instanceof Error ? error.message : String(error);
          setConnection({ kind: "error", message });
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="app">
      <header className="app-header">
        <h1>Tower</h1>
      </header>
      <main className="app-main">
        <p className="backend-status" data-state={connection.kind}>
          {connection.kind === "loading" && "Checking backend connectivity…"}
          {connection.kind === "connected" && `Backend status: ${connection.status}`}
          {connection.kind === "error" && `Backend unreachable: ${connection.message}`}
        </p>
      </main>
    </div>
  );
}
