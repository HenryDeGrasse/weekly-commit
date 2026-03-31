/// <reference types="vitest" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import federation from "@originjs/vite-plugin-federation";

export default defineConfig({
  define: {
    // Inline API base URL at build time — avoids Module Federation chunk issues
    __WC_API_BASE_URL__: JSON.stringify(process.env.VITE_API_BASE_URL || "/api"),
  },
  server: {
    proxy: {
      // Proxy /api calls to the Spring Boot backend during `vite dev`
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  plugins: [
    tailwindcss(),
    react(),
    federation({
      name: "weeklyCommit",
      filename: "remoteEntry.js",
      exposes: {
        // Route-level entry for the host to mount the remote
        "./Routes": "./src/Routes.tsx",
      },
      // Shared deps as string array — avoids SharedConfig property mismatches;
      // the plugin resolves versions from package.json automatically.
      shared: ["react", "react-dom", "react-router-dom"],
    }),
  ],
  build: {
    target: "esnext",
    minify: false,
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test-setup.ts"],
    pool: "forks",
    poolOptions: {
      forks: {
        minForks: 1,
        maxForks: 6,
      },
    },
  },
});
