import { expect, type Page } from "@playwright/test";

export function isLive(): boolean {
  return process.env.E2E_LIVE === "1";
}

export const e2eEnv = {
  consoleUrl: (process.env.E2E_BASE_URL ?? "http://127.0.0.1:5173").replace(/\/$/, ""),
  marketingUrl: (process.env.E2E_MARKETING_URL ?? "http://127.0.0.1:3000").replace(/\/$/, ""),
  identityUrl: (process.env.E2E_IDENTITY_URL ?? "http://127.0.0.1:8081").replace(/\/$/, ""),
  agentEmail: process.env.E2E_AGENT_EMAIL ?? "ci@prabhixtechnologies.com",
  agentPassword: process.env.E2E_AGENT_PASSWORD ?? "ci-seed-password",
  visitorName: process.env.E2E_VISITOR_NAME ?? "E2E Visitor",
  visitorEmail: process.env.E2E_VISITOR_EMAIL ?? "visitor@example.com",
  signupEmail: process.env.E2E_SIGNUP_EMAIL ?? `e2e-${Date.now()}@example.com`,
  signupPassword: process.env.E2E_SIGNUP_PASSWORD ?? "e2e-pass-word-10",
  signupName: process.env.E2E_SIGNUP_NAME ?? "E2E Signup",
  workspaceName: process.env.E2E_WORKSPACE_NAME ?? "E2E Workspace",
};

/** Identity hosted login (address, then password). Lands back on the console. */
export async function signInAsAgent(page: Page): Promise<void> {
  await page.goto(e2eEnv.consoleUrl + "/");
  await page.getByLabel("Email address").fill(e2eEnv.agentEmail);
  await page.getByRole("button", { name: "Continue" }).click();
  await page.locator("#password").fill(e2eEnv.agentPassword);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(new RegExp(escapeRegExp(e2eEnv.consoleUrl)));
}

export function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Razorpay test-mode iframe: card 4111…, any future expiry, any CVV. */
export async function completeRazorpayTestCard(page: Page): Promise<void> {
  const frame = page.frameLocator("iframe.razorpay-checkout-frame, iframe[src*='razorpay']").first();
  await frame.getByText(/card/i).first().click({ timeout: 15_000 }).catch(() => undefined);
  const card = frame.locator("input[name='card[number]'], input[name='card_number'], #card_number");
  if (await card.count()) {
    await card.first().fill("4111111111111111");
    await frame.locator("input[name='card[expiry]'], #card_expiry").first().fill("12/29");
    await frame.locator("input[name='card[cvv]'], #card_cvv").first().fill("123");
    await frame.getByRole("button", { name: /pay|submit/i }).first().click();
  }
}
