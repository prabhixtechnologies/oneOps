import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { PERMISSIONS } from "@/lib/permissions";

vi.mock("@/lib/auth", () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from "@/lib/auth";

const mockedUseAuth = vi.mocked(useAuth);

describe("PermissionGate", () => {
  it("renders children when the user has the required permission", () => {
    mockedUseAuth.mockReturnValue({
      permissions: [PERMISSIONS.CHAT_REPLY],
    } as ReturnType<typeof useAuth>);

    render(
      <PermissionGate permission={PERMISSIONS.CHAT_REPLY}>
        <button type="button">Send reply</button>
      </PermissionGate>,
    );

    expect(screen.getByRole("button", { name: "Send reply" })).toBeInTheDocument();
  });

  it("hides children when the user lacks the required permission", () => {
    mockedUseAuth.mockReturnValue({
      permissions: [PERMISSIONS.CHAT_READ],
    } as ReturnType<typeof useAuth>);

    render(
      <PermissionGate permission={PERMISSIONS.CHAT_REPLY}>
        <button type="button">Send reply</button>
      </PermissionGate>,
    );

    expect(screen.queryByRole("button", { name: "Send reply" })).not.toBeInTheDocument();
  });

  it("renders fallback when provided and permission is missing", () => {
    mockedUseAuth.mockReturnValue({
      permissions: [],
    } as ReturnType<typeof useAuth>);

    render(
      <PermissionGate permission={PERMISSIONS.CHAT_ASSIGN} fallback={<p>Read-only</p>}>
        <button type="button">Assign</button>
      </PermissionGate>,
    );

    expect(screen.getByText("Read-only")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Assign" })).not.toBeInTheDocument();
  });
});
