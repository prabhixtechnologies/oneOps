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
      "@": path.resolve(import.meta.dirname, "./src"),
      "@prabhix/oneops-api": path.resolve(import.meta.dirname, "../packages/oneops-api/src/index.ts"),
    },
    dedupe: ["react", "react-dom", "zod"],
  },
  optimizeDeps: {
    include: ["@prabhix/ui", "@prabhix/oidc-client"],
  },
  build: {
    rolldownOptions: {
      output: {
        // Keep UI runtime libraries in one graph. Splitting Radix / Floating UI away from React
        // (especially with includeDependenciesRecursively: false) created circular chunks that
        // crashed /login with "X is not a function" before paint.
        codeSplitting: {
          groups: [
            {
              name: "vendor-react",
              test: /node_modules[\\/](react-dom|react-router|scheduler|@radix-ui|@floating-ui|cmdk)[\\/]|node_modules[\\/]react[\\/]/,
              priority: 30,
            },
            {
              name: "vendor-query",
              test: /node_modules[\\/]@tanstack[\\/](react-query|query-core)/,
              priority: 25,
            },
            {
              name: "vendor-virtual",
              test: /node_modules[\\/]@tanstack[\\/]react-virtual/,
              priority: 23,
            },
            {
              name: "vendor-date",
              test: /node_modules[\\/]date-fns/,
              priority: 22,
            },
            {
              name: "vendor-dompurify",
              test: /node_modules[\\/]dompurify/,
              priority: 21,
            },
            {
              name: "vendor-icons",
              test: /node_modules[\\/]lucide-react/,
              priority: 20,
            },
            {
              name: "vendor-forms",
              test: /node_modules[\\/](react-hook-form|@hookform)/,
              priority: 19,
            },
            {
              name: "vendor-zod",
              test: /node_modules[\\/]zod/,
              priority: 18,
            },
            {
              name: "vendor-sonner",
              test: /node_modules[\\/]sonner/,
              priority: 17,
            },
          ],
        },
      },
    },
  },
  server: {
    // Distinct ports so both consoles can run at once, which is the only way to check locally that
    // one sign-in covers both. Both are in the backend's CORS allowlist.
    port: APP === "admin" ? 5174 : 5173,
    fs: {
      allow: [path.resolve(import.meta.dirname, "..")],
    },
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
