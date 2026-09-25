import { defineConfig, mergeConfig } from "vitest/config";
import viteConfig from "./vite.config.ts";

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.ts"],
      globals: true,
      // Playwright specs live in e2e/ and are a separate job. Vitest's default
      // glob also matches *.spec.ts, and loading them here fails the unit run.
      exclude: ["e2e/**", "**/node_modules/**", "**/dist/**"],
    },
  }),
);
