import { useBlocker } from "react-router";
import { useBeforeUnload } from "@prabhixtechnologies/ui";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

/*
  The react-router half of @prabhixtechnologies/ui's unsaved-changes guard.

  The package hook is router-agnostic and asks the caller to wrap each navigation, because a
  shared package cannot depend on a router. That covers explicit "Back" buttons and misses the
  case that actually happens: someone fills in half a settings form and clicks a sidebar link.

  This app renders a data router, so `useBlocker` is available and intercepts every in-app
  navigation - links, redirects, programmatic pushes - without the form needing to know where
  any of them are. The package hook still supplies the browser half, since `useBlocker` knows
  nothing about a reload or a closed tab.

  Contract: Infra/docs/UX-STANDARD.md
*/

export interface UnsavedChangesProps {
  /** Whether there is work that would be lost. */
  when: boolean;
  /**
   * What is at stake, in the page's own words: "The seller and tax details you changed have
   * not been saved." Never just "You have unsaved changes" when the page can say more.
   */
  description?: string;
}

export function UnsavedChanges({ when, description }: UnsavedChangesProps) {
  // Reload and tab-close. The browser owns the wording and refuses to show anything at all
  // unless the person has interacted with the page, so this cannot be used to trap anyone.
  useBeforeUnload(when);

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      when && currentLocation.pathname !== nextLocation.pathname,
  );

  const blocked = blocker.state === "blocked";

  return (
    <Dialog
      open={blocked}
      // Escape and the overlay both mean "I did not mean to leave", so they reset the
      // blocker rather than letting the navigation through.
      onOpenChange={(open) => {
        if (!open && blocked) blocker.reset();
      }}
    >
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Leave without saving?</DialogTitle>
          <DialogDescription>
            {description ?? "Your changes on this page have not been saved yet."}
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          {/* Staying is the safe outcome, so it leads and takes the initial focus. */}
          <Button variant="outline" onClick={() => blocker.reset?.()}>
            Keep editing
          </Button>
          <Button variant="destructive" onClick={() => blocker.proceed?.()}>
            Discard changes
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
