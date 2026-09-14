import { test, expect } from "@playwright/test";
import { e2eEnv, isLive, signInAsAgent } from "./helpers";

test.describe("inbound mail → inbox reply", () => {
  test.skip(!isLive(), "Set E2E_LIVE=1 against a seeded stack");

  test("agent replies to a shared-mailbox ticket", async ({ page }) => {
    await signInAsAgent(page);
    await page.goto(e2eEnv.consoleUrl + "/inbox");

    const ticket = page.getByTestId("inbox-ticket-row").first();
    await expect(ticket).toBeVisible({ timeout: 30_000 });
    await ticket.click();

    await page.getByTestId("mail-reply-body").fill("Thanks — we received your mail.");
    await page.getByTestId("mail-reply-send").click();
    await expect(page.getByText("Thanks — we received your mail.")).toBeVisible();
  });
});
