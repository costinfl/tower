import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { DEMO_MODE, installDemoBackend } from "./demo/install";
import "./index.css";

// Installed before React mounts, or the first request would escape the shim
// and hit a network that has no Tower behind it.
if (DEMO_MODE) {
  installDemoBackend();
}

const container = document.getElementById("root");
if (!container) {
  throw new Error("Root element not found");
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
