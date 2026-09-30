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
  oneops: {
    script: "/src/main.tsx",
    title: "Prabhix — Team Inbox &amp; Operations",
    // Must match a theme in web-kit/packages/brand/tokens.json.
    brand: "oneops",
    density: "comfortable",
    themeColor: "#4338ca",
  },
  admin: {
    script: "/src/main-admin.tsx",
    title: "Prabhix Admin",
    brand: "admin",
    density: "compact",
    themeColor: "#6d28d9",
  },
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
        const e = ENTRY[APP];
        return html
          .replace(ENTRY.oneops.script, e.script)
          // Matched by element rather than by its text, which contains an em dash and so would
          // depend on this file and index.html agreeing about encoding.
          .replace(/<title>[^<]*<\/title>/, `<title>${e.title}</title>`)
          // Swaps the generated token theme: admin is violet + cyan and compact.
          .replace(/data-brand="[^"]*"/, `data-brand="${e.brand}"`)
          .replace(/data-density="[^"]*"/, `data-density="${e.density}"`)
          .replace(/(<meta name="theme-color" content=")[^"]*/, `$1${e.themeColor}`);
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
      "@prabhixtechnologies/oneops-api": path.resolve(import.meta.dirname, "../packages/oneops-api/src/index.ts"),
    },
    // react and react-dom for the usual reason: hooks read a dispatcher off a module-level
    // singleton, so a second copy fails as "Cannot read properties of null". zod because two
    // copies make `instanceof ZodError` false across the boundary.
    //
    // `preserveSymlinks: true` used to sit here, and a `server.fs.allow` entry pointing at the
    // real path of a sibling web-kit checkout. Both existed because brand and ui were `file:`
    // links leaving this repository: Vite resolves a link to its real path before checking
    // fs.allow, and brand's entry builds mark URLs with `new URL("../marks/…", import.meta.url)`,
    // which then resolved outside the allowed roots and failed as "Denied ID". They are ordinary
    // packages under this app's node_modules now, so there is no link to preserve and nothing
    // outside the root to allow. `optimizeDeps.include` went with them — it was there because
    // Vite excludes linked packages from pre-bundling, which does not apply to a registry install.
    dedupe: ["react", "react-dom", "zod"],
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
    // The repository root, because @prabhixtechnologies/oneops-api is aliased to its source in
    // ../packages. Nothing outside this repository is read any more; see resolve.dedupe above.
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
