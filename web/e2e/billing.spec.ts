import { test, expect } from "@playwright/test";
import { completeRazorpayTestCard, e2eEnv, isLive } from "./helpers";

test.describe("signup → trial → Razorpay upgrade → webhook → renewal", () => {
  test.skip(!isLive(), "Set E2E_LIVE=1 against a seeded stack");

  test("new workspace reaches billing and completes a test-mode upgrade", async ({ page }) => {
    await page.goto(e2eEnv.identityUrl + "/signup");
    await expect(page.getByTestId("signup-form")).toBeVisible();
    await page.locator("#name").fill(e2eEnv.signupName);
    await page.locator("#organization").fill(e2eEnv.workspaceName);
    await page.locator("#email").fill(e2eEnv.signupEmail);
    await page.locator("#password").fill(e2eEnv.signupPassword);
    await page.getByTestId("signup-submit").click();

    await page.waitForURL(new RegExp("oneops|/billing|/dashboard|localhost:5173"), { timeout: 60_000 });
    await page.goto(e2eEnv.consoleUrl + "/billing");
    await expect(page.getByText(/trial|current plan/i).first()).toBeVisible();

    await page.getByTestId("plan-upgrade").first().click();
    await completeRazorpayTestCard(page);
    await expect(page.getByText(/upgraded|active|payment verified/i).first()).toBeVisible({
      timeout: 60_000,
    });

    await page.reload();
    await expect(page.getByText(/current period ends/i)).toBeVisible();
  });
});
