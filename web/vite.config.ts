import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "./src"),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes("node_modules")) return;

          if (
            id.includes("react-dom") ||
            id.includes("/react/") ||
            id.includes("react-router") ||
            id.includes("scheduler")
          ) {
            return "vendor-react";
          }
          if (id.includes("@tanstack/react-query") || id.includes("@tanstack/query-core")) {
            return "vendor-query";
          }
          if (id.includes("@radix-ui") || id.includes("cmdk")) {
            return "vendor-radix";
          }
          if (id.includes("@tanstack/react-virtual")) {
            return "vendor-virtual";
          }
          if (id.includes("date-fns")) {
            return "vendor-date";
          }
          if (id.includes("dompurify")) {
            return "vendor-dompurify";
          }
          if (id.includes("lucide-react")) {
            return "vendor-icons";
          }
          if (id.includes("react-hook-form") || id.includes("@hookform")) {
            return "vendor-forms";
          }
          if (id.includes("/zod/") || id.endsWith("/zod")) {
            return "vendor-zod";
          }
          if (id.includes("sonner")) {
            return "vendor-sonner";
          }
        },
      },
    },
  },
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
