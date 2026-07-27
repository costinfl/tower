import { useEffect, useState } from "react";
import { getHealth } from "./api/client";
import EnvironmentsPage from "./pages/EnvironmentsPage";
import PromotionPathsPage from "./pages/PromotionPathsPage";

type ConnectionState =
  | { kind: "loading" }
  | { kind: "connected"; status: string }
  | { kind: "error"; message: string };

type Tab = "paths" | "environments";

export default function App() {
  const [connection, setConnection] = useState<ConnectionState>({ kind: "loading" });
  const [tab, setTab] = useState<Tab>("paths");

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
        <nav className="app-nav">
          <button
            type="button"
            className={tab === "paths" ? "app-nav__tab app-nav__tab--active" : "app-nav__tab"}
            onClick={() => setTab("paths")}
          >
            Promotion Paths
          </button>
          <button
            type="button"
            className={tab === "environments" ? "app-nav__tab app-nav__tab--active" : "app-nav__tab"}
            onClick={() => setTab("environments")}
          >
            Environments
          </button>
        </nav>
        <p className="backend-status" data-state={connection.kind}>
          {connection.kind === "loading" && "Checking backend connectivity…"}
          {connection.kind === "connected" && `Backend status: ${connection.status}`}
          {connection.kind === "error" && `Backend unreachable: ${connection.message}`}
        </p>
      </header>
      <main className="app-main">
        {tab === "paths" ? <PromotionPathsPage /> : <EnvironmentsPage />}
      </main>
    </div>
  );
}
