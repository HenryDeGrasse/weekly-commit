/// <reference types="vitest" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import federation from "@originjs/vite-plugin-federation";

export default defineConfig({
  plugins: [
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
  },
});
