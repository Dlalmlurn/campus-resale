import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

const devProxyTarget = process.env.VITE_DEV_PROXY_TARGET ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: devProxyTarget,
        changeOrigin: true
      },
      "/ws": {
        target: devProxyTarget.replace(/^http/, "ws"),
        ws: true,
        changeOrigin: true
      }
    }
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/test/setup.ts"
  }
});
