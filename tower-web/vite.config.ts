import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Tower binds its backend to the loopback interface only (see
// docs/planning/Implementation-Plan.md, "Deployment Model"), so the dev
// server proxy must target 127.0.0.1 rather than localhost.
export default defineConfig({
  // GitHub Pages serves a project site at /<repo>/, so the default base of "/"
  // would make every asset 404. Left as "/" for local development and for the
  // build packaged into tower-api, where the SPA is served from the root.
  base: process.env.VITE_BASE ?? "/",
  plugins: [react()],
  server: {
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: "dist",
  },
});
