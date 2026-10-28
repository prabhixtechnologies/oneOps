import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { paymentMatches } from "./RevenuePage";
import RevenuePage from "./RevenuePage";

vi.mock("@/features/ops/control-plane-api", () => ({
  fetchOneOpsRevenue: vi.fn(async () => ({
    capturedTotal: 0,
    pendingTotal: 0,
    capturedCount: 0,
    pendingCount: 0,
    failedCount: 0,
    mrr: 0,
    churnRate: 0,
    activeSubscriptions: 0,
    products: [],
  })),
  fetchMobiPayments: vi.fn(async () => [
    {
      id: "captured",
      shopName: "Ganesh Grahak Seva Kendra",
      priceCode: "WORKSPACE_ACTIVATION",
      amount: 50,
      status: "CAPTURED",
    },
    {
      id: "expired",
      shopName: "Suman Unique Center",
      priceCode: "WORKSPACE_ACTIVATION",
      amount: 50,
      status: "EXPIRED",
    },
  ]),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RevenuePage />
    </QueryClientProvider>,
  );
}

describe("payment status filter", () => {
  it("keeps captured payments on the default filter", () => {
    expect(paymentMatches("CAPTURED", "CAPTURED")).toBe(true);
    expect(paymentMatches("EXPIRED", "CAPTURED")).toBe(false);
    expect(paymentMatches("PENDING", "CAPTURED")).toBe(false);
  });

  it("groups unfinished checkouts as open and refunds together", () => {
    expect(paymentMatches("CREATED", "OPEN")).toBe(true);
    expect(paymentMatches("PENDING", "OPEN")).toBe(true);
    expect(paymentMatches("AUTHORIZED", "OPEN")).toBe(true);
    expect(paymentMatches("CAPTURED", "OPEN")).toBe(false);
    expect(paymentMatches("REFUNDED", "REFUNDED")).toBe(true);
    expect(paymentMatches("PARTIALLY_REFUNDED", "REFUNDED")).toBe(true);
  });
});

describe("Revenue payments list", () => {
  it("shows captured payments by default and switches with the status filter", async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText("Ganesh Grahak Seva Kendra")).toBeInTheDocument();
    expect(screen.queryByText("Suman Unique Center")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Captured (1)" })).toHaveAttribute("aria-pressed", "true");

    await user.click(screen.getByRole("button", { name: "Expired (1)" }));

    expect(screen.getByText("Suman Unique Center")).toBeInTheDocument();
    expect(screen.queryByText("Ganesh Grahak Seva Kendra")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "All (2)" }));

    expect(screen.getByText("Ganesh Grahak Seva Kendra")).toBeInTheDocument();
    expect(screen.getByText("Suman Unique Center")).toBeInTheDocument();
  });
});
