import { test, expect } from "@playwright/test";
import { e2eEnv, isLive, signInAsAgent } from "./helpers";

test.describe("visitor chat ↔ agent reply", () => {
  test.skip(!isLive(), "Set E2E_LIVE=1 against a seeded stack");

  test("visitor message is answered from the console", async ({ browser }) => {
    const visitor = await browser.newPage();
    const agent = await browser.newPage();

    await visitor.goto(e2eEnv.marketingUrl + "/");
    await visitor.getByTestId("chat-launcher").click();
    await visitor.getByLabel("Name").fill(e2eEnv.visitorName);
    await visitor.getByLabel("Email").fill(e2eEnv.visitorEmail);
    await visitor.getByTestId("chat-start").click();
    await visitor.getByTestId("chat-visitor-composer").fill("Hello from the storefront");
    await visitor.getByTestId("chat-visitor-send").click();
    await expect(visitor.getByTestId("chat-visitor-messages")).toContainText("Hello from the storefront");

    await signInAsAgent(agent);
    await agent.goto(e2eEnv.consoleUrl + "/chat");
    await agent.getByTestId("chat-conversation-row").first().click();
    await agent.getByTestId("chat-agent-composer").fill("We are here");
    await agent.getByTestId("chat-agent-send").click();

    await expect(visitor.getByTestId("chat-visitor-messages")).toContainText("We are here");

    await visitor.close();
    await agent.close();
  });
});
