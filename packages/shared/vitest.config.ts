import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    pool: "forks",
    poolOptions: {
      forks: {
        minForks: 1,
        maxForks: 6,
      },
    },
  },
});
