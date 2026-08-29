import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { router } from "./routes";
import "./index.css";

/** Entry point for the OneOps product. See {@link ./main-admin.tsx} for the admin console. */
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App router={router} />
  </StrictMode>,
);
