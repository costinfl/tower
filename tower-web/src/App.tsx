import { useEffect, useState } from "react";
import { getHealth } from "./api/client";
import ApplicationsPage from "./pages/ApplicationsPage";
import ConnectorsPage from "./pages/ConnectorsPage";
import DashboardPage from "./pages/DashboardPage";
import DocumentTemplatesPage from "./pages/DocumentTemplatesPage";
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

export type Tab =
  | "dashboard"
  | "releasePacks"
  | "applications"
  | "paths"
  | "environments"
  | "connectors"
  | "templates"
  | "portability";

// The nav has to serve two people and used to serve only one.
//
// Ordered by frequency of use, the Dashboard leads: it is the question a
// developer arrives with, and every other screen answers a narrower one.
// Ordered by what a newcomer must do, it runs the other way — Environments
// first, because a Promotion Path is a sequence of them and everything else
// refers to them in turn.
//
// Grouping is how both are true at once. The daily pair stays first, so nobody
// who already configured Tower pays for the newcomer's benefit, and the middle
// group says outright that its order is an order. The Dashboard's setup path
// teaches the same sequence from the other end, driven by what actually exists.
const NAV: { label: string; tabs: { tab: Tab; label: string }[] }[] = [
  {
    label: "Watch",
    tabs: [
      { tab: "dashboard", label: "Dashboard" },
      // Second because it remains Tower's central business concept (ADR-004).
      { tab: "releasePacks", label: "Release Packs" },
    ],
  },
  {
    // The order inside this group is the one the model forces rather than one
    // somebody preferred, which is why it is safe to label as an order.
    label: "Set up, in order",
    tabs: [
      { tab: "environments", label: "Environments" },
      { tab: "paths", label: "Promotion Paths" },
      { tab: "applications", label: "Applications" },
      { tab: "connectors", label: "Connectors" },
      // Configuration for the documents Release Packs produce, so it sits after
      // the screens that produce them rather than beside them.
      { tab: "templates", label: "Doc templates" },
    ],
  },
  {
    // Last: sharing is something you do once the release information itself
    // exists, not the first thing you reach for.
    label: "Share",
    tabs: [{ tab: "portability", label: "Export & Import" }],
  },
];

export default function App() {
  const [connection, setConnection] = useState<ConnectionState>({ kind: "loading" });
  const [tab, setTab] = useState<Tab>("dashboard");

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
          {NAV.map((group) => (
            <div className="app-nav__group" key={group.label}>
              <span className="app-nav__group-label">{group.label}</span>
              <div className="app-nav__tabs">
                {group.tabs.map((entry) => (
                  <button
                    key={entry.tab}
                    type="button"
                    className={
                      tab === entry.tab ? "app-nav__tab app-nav__tab--active" : "app-nav__tab"
                    }
                    onClick={() => setTab(entry.tab)}
                  >
                    {entry.label}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </nav>
        <p className="backend-status" data-state={connection.kind}>
          {connection.kind === "loading" && "Checking backend connectivity…"}
          {connection.kind === "connected" && `Backend status: ${connection.status}`}
          {connection.kind === "error" && `Backend unreachable: ${connection.message}`}
        </p>
      </header>
      {DEMO_MODE && <DemoBanner />}
      <main className="app-main">
        {/*
          The Dashboard is the only screen that navigates: its setup path names
          the steps in order and each one has to lead somewhere. Passing the
          setter rather than routing keeps the rest of the app as it was.
        */}
        {tab === "dashboard" && <DashboardPage onNavigate={setTab} />}
        {tab === "releasePacks" && <ReleasePacksPage />}
        {tab === "applications" && <ApplicationsPage />}
        {tab === "paths" && <PromotionPathsPage />}
        {tab === "environments" && <EnvironmentsPage />}
        {tab === "connectors" && <ConnectorsPage />}
        {tab === "templates" && <DocumentTemplatesPage />}
        {tab === "portability" && <PortabilityPage />}
      </main>
    </div>
  );
}
