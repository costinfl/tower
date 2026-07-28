import { useEffect, useState } from "react";
import { getHealth } from "./api/client";
import ApplicationsPage from "./pages/ApplicationsPage";
import EnvironmentsPage from "./pages/EnvironmentsPage";
import PortabilityPage from "./pages/PortabilityPage";
import DemoBanner from "./components/DemoBanner";
import { DEMO_MODE } from "./demo/install";
import PromotionPathsPage from "./pages/PromotionPathsPage";
import ReleasePacksPage from "./pages/ReleasePacksPage";

type ConnectionState =
  | { kind: "loading" }
  | { kind: "connected"; status: string }
  | { kind: "error"; message: string };

// Release Packs leads the nav — it is Tower's central business concept
// (ADR-004) and every other screen exists to support it.
type Tab = "releasePacks" | "applications" | "paths" | "environments" | "portability";

export default function App() {
  const [connection, setConnection] = useState<ConnectionState>({ kind: "loading" });
  const [tab, setTab] = useState<Tab>("releasePacks");

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
            className={tab === "releasePacks" ? "app-nav__tab app-nav__tab--active" : "app-nav__tab"}
            onClick={() => setTab("releasePacks")}
          >
            Release Packs
          </button>
          <button
            type="button"
            className={tab === "applications" ? "app-nav__tab app-nav__tab--active" : "app-nav__tab"}
            onClick={() => setTab("applications")}
          >
            Applications
          </button>
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
          {/*
            Last in the order: sharing is something you do once the release
            information itself exists, not the first thing you reach for.
          */}
          <button
            type="button"
            className={tab === "portability" ? "app-nav__tab app-nav__tab--active" : "app-nav__tab"}
            onClick={() => setTab("portability")}
          >
            Export &amp; Import
          </button>
        </nav>
        <p className="backend-status" data-state={connection.kind}>
          {connection.kind === "loading" && "Checking backend connectivity…"}
          {connection.kind === "connected" && `Backend status: ${connection.status}`}
          {connection.kind === "error" && `Backend unreachable: ${connection.message}`}
        </p>
      </header>
      {DEMO_MODE && <DemoBanner />}
      <main className="app-main">
        {tab === "releasePacks" && <ReleasePacksPage />}
        {tab === "applications" && <ApplicationsPage />}
        {tab === "paths" && <PromotionPathsPage />}
        {tab === "environments" && <EnvironmentsPage />}
        {tab === "portability" && <PortabilityPage />}
      </main>
    </div>
  );
}
