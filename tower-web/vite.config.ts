import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Tower binds its backend to the loopback interface only (see
// docs/planning/Implementation-Plan.md, "Deployment Model"), so the dev
// server proxy must target 127.0.0.1 rather than localhost.
export default defineConfig({
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
