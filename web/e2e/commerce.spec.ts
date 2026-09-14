import { test, expect } from "@playwright/test";
import { completeRazorpayTestCard, e2eEnv, isLive, signInAsAgent } from "./helpers";

test.describe("storefront order → fulfil", () => {
  test.skip(!isLive(), "Set E2E_LIVE=1 against a seeded stack");

  test("checkout from /shop then mark fulfilled in the console", async ({ page }) => {
    await page.goto(e2eEnv.marketingUrl + "/shop");
    await page.getByTestId("shop-product").first().click();
    await page.getByTestId("shop-add-to-cart").click();
    await page.goto(e2eEnv.marketingUrl + "/shop/cart");
    await page.getByTestId("shop-checkout").click();

    await page.getByLabel("Email *").fill(e2eEnv.visitorEmail);
    await page.getByLabel("Full name").fill(e2eEnv.visitorName);
    const billing = page.getByRole("heading", { name: "Billing address" }).locator("..");
    await billing.getByLabel("Name *").fill(e2eEnv.visitorName);
    await billing.getByLabel("Address line 1 *").fill("1 Test Street");
    await billing.getByLabel("City *").fill("Bengaluru");
    await billing.getByLabel("PIN code *").fill("560001");
    await page.getByTestId("shop-pay").click();
    await completeRazorpayTestCard(page);

    await signInAsAgent(page);
    await page.goto(e2eEnv.consoleUrl + "/commerce/orders");
    await page.getByTestId("commerce-order-open").first().click();
    await expect(page.getByTestId("order-fulfill")).toBeVisible({ timeout: 60_000 });
    await page.getByTestId("order-fulfill").click();
    await expect(page.getByText(/fulfilled/i).first()).toBeVisible();
  });
});
