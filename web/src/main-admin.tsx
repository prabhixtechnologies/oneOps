import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { router } from "./routes-admin";
import "./index.css";

/**
 * Entry point for the private admin console.
 *
 * <p>Selected at build time by `APP=admin`, which swaps this file in for `main.tsx` as the script
 * the page loads. Only one of the two ends up in any given bundle, so the OneOps build does not
 * ship the platform pages.
 */
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App mode="admin" router={router} />
  </StrictMode>,
);
