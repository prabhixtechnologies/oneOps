import { render } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import axe from "axe-core";
import { writeHeapSnapshot } from "node:v8";
import { join } from "node:path";
import { tmpdir } from "node:os";
import { beforeAll, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { AuthShell } from "@/components/layout/AuthShell";
import { LoginPage } from "@/features/auth/LoginPage";
import { SignupPage } from "@/features/auth/SignupPage";
import { LogoMark } from "@/components/brand/LogoMark";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState } from "@/components/shared/states";
import { ThemeProvider } from "@/lib/theme";

beforeAll(() => {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      addEventListener() {},
      removeEventListener() {},
      addListener() {},
      removeListener() {},
      dispatchEvent() {
        return false;
      },
    }),
  });
});

const RULES = [
  "button-name",
  "link-name",
  "image-alt",
  "label-content-name-mismatch",
];

async function violations(container: HTMLElement) {
  const results = await axe.run(container, {
    runOnly: { type: "rule", values: RULES },
  });
  return results.violations.map((v) => ({ rule: v.id, html: v.nodes.map((n) => n.html) }));
}

describe("console accessibility", () => {
  it("names the Prabhix mark", async () => {
    const { container, getByRole } = render(<LogoMark className="h-8 w-8" />);
    expect(getByRole("img", { name: "Prabhix" })).toBeInTheDocument();
    expect(await violations(container)).toEqual([]);
  });

  it("exposes a skip link on the auth shell", async () => {
    const { container, getByRole } = render(
      <ThemeProvider>
        <MemoryRouter>
          <AuthShell />
        </MemoryRouter>
      </ThemeProvider>,
    );
    expect(getByRole("link", { name: "Skip to sign in" })).toHaveAttribute("href", "#main-content");
    expect(await violations(container)).toEqual([]);
  });

  it("names login actions", async () => {
    const { container } = render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );
    expect(await violations(container)).toEqual([]);
  });

  it("reaches the skip link from the keyboard", async () => {
    const user = userEvent.setup();
    render(
      <ThemeProvider>
        <MemoryRouter>
          <AuthShell />
        </MemoryRouter>
      </ThemeProvider>,
    );
    await user.tab();
    expect(document.activeElement).toHaveTextContent("Skip to sign in");
  });

  it("names signup waiting copy, page headers, and empty states", async () => {
    const { container, getByRole } = render(
      <MemoryRouter>
        <SignupPage />
        <PageHeader title="Files" description="Upload by id until a file list exists." />
        <EmptyState title="No file list yet" description="Upload a file or enter a file ID." />
      </MemoryRouter>,
    );
    expect(getByRole("heading", { name: /Taking you to sign up|Identity is required/ })).toBeInTheDocument();
    expect(getByRole("heading", { name: "Files" })).toBeInTheDocument();
    expect(await violations(container)).toEqual([]);
  });

  it("writes a heap snapshot of the auth shell", () => {
    render(
      <ThemeProvider>
        <MemoryRouter>
          <AuthShell />
        </MemoryRouter>
      </ThemeProvider>,
    );
    const dest = join(tmpdir(), "oneops-auth-shell.heapsnapshot");
    writeHeapSnapshot(dest);
    expect(dest.endsWith(".heapsnapshot")).toBe(true);
  }, 30_000);
});
