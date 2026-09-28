import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { createMemoryRouter, Link, RouterProvider } from "react-router";
import { afterEach, describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { UnsavedChanges } from "@/components/shared/UnsavedChanges";

/*
  The guard exists for one moment: half a form filled in, and a click on the sidebar.

  Every case here navigates the real router rather than calling the blocker, because the
  whole point of using `useBlocker` over the package's `guard()` wrapper is that the form
  does not know where the links are. A test that drove the hook directly would pass while
  the thing it protects had stopped working.
*/

afterEach(() => {
  // A Radix dialog parks `pointer-events: none` on the body and restores it on close;
  // unmounting while open leaves it behind and the next test cannot click anything.
  document.body.style.pointerEvents = "";
});

function renderAt(when: boolean) {
  const router = createMemoryRouter(
    [
      {
        path: "/",
        element: (
          <>
            <UnsavedChanges when={when} description="The details you changed have not been saved." />
            <Link to="/elsewhere">Go to orders</Link>
          </>
        ),
      },
      { path: "/elsewhere", element: <h1>Orders</h1> },
    ],
    { initialEntries: ["/"] },
  );
  render(<RouterProvider router={router} />);
  return router;
}

describe("UnsavedChanges", () => {
  it("lets a link through when there is nothing to lose", async () => {
    const user = userEvent.setup();
    renderAt(false);

    await user.click(screen.getByRole("link", { name: "Go to orders" }));

    expect(await screen.findByRole("heading", { name: "Orders" })).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("stops a link and says what is at stake", async () => {
    const user = userEvent.setup();
    renderAt(true);

    await user.click(screen.getByRole("link", { name: "Go to orders" }));

    const dialog = await screen.findByRole("dialog");
    expect(dialog).toHaveTextContent("Leave without saving?");
    expect(dialog).toHaveTextContent("The details you changed have not been saved.");
    expect(screen.queryByRole("heading", { name: "Orders" })).not.toBeInTheDocument();
  });

  it("keeps you on the page, and keeps guarding, when you choose to stay", async () => {
    const user = userEvent.setup();
    const router = renderAt(true);

    await user.click(screen.getByRole("link", { name: "Go to orders" }));
    await user.click(await screen.findByRole("button", { name: "Keep editing" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe("/");

    // The blocker has to be reset, not merely hidden: a second attempt must stop too.
    await user.click(screen.getByRole("link", { name: "Go to orders" }));
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
  });

  it("navigates when you choose to discard", async () => {
    const user = userEvent.setup();
    const router = renderAt(true);

    await user.click(screen.getByRole("link", { name: "Go to orders" }));
    await user.click(await screen.findByRole("button", { name: "Discard changes" }));

    expect(await screen.findByRole("heading", { name: "Orders" })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/elsewhere");
  });

  it("treats Escape as staying, not as leaving", async () => {
    const user = userEvent.setup();
    const router = renderAt(true);

    await user.click(screen.getByRole("link", { name: "Go to orders" }));
    await screen.findByRole("dialog");
    await user.keyboard("{Escape}");

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe("/");
  });
});
