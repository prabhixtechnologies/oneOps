import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "node:path";

/**
 * Which of the two consoles to build: the OneOps product or the private admin app.
 *
 * <p>Both are built from this one source tree. `APP=admin` swaps the entry script in `index.html`,
 * so each build pulls in only the routes its entry imports and the OneOps bundle does not contain
 * the platform admin pages at all. The output filename stays `index.html` either way, which keeps
 * the nginx config and the CSP hash generator identical for both images.
 */
const APP = process.env.APP === "admin" ? "admin" : "oneops";

const ENTRY = {
  oneops: { script: "/src/main.tsx", title: "Prabhix — Team Inbox &amp; Operations" },
  admin: { script: "/src/main-admin.tsx", title: "Prabhix Admin" },
} as const;

function appEntryPlugin(): Plugin {
  return {
    name: "prabhix-app-entry",
    transformIndexHtml: {
      // The order belongs on the hook, not on the plugin: Vite sorts index-HTML hooks by this
      // property alone. Without it the swap lands after Vite has already collected the entry
      // script, which silently produces an admin-titled page running the OneOps bundle.
      order: "pre",
      handler(html) {
        if (APP === "oneops") return html;
        return html
          .replace(ENTRY.oneops.script, ENTRY[APP].script)
          // Matched by element rather than by its text, which contains an em dash and so would
          // depend on this file and index.html agreeing about encoding.
          .replace(/<title>[^<]*<\/title>/, `<title>${ENTRY[APP].title}</title>`);
      },
    },
  };
}

export default defineConfig({
  plugins: [appEntryPlugin(), react(), tailwindcss()],
  define: {
    // Read by lib/app-mode.ts. A constant rather than a runtime value so the comparisons against it
    // fold, and each bundle keeps only its own app's branches.
    __APP_MODE__: JSON.stringify(APP),
  },
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
    // Distinct ports so both consoles can run at once, which is the only way to check locally that
    // one sign-in covers both. Both are in the backend's CORS allowlist.
    port: APP === "admin" ? 5174 : 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
