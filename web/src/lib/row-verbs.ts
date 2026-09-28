import { toast } from "sonner";
import type { RowAction } from "@/components/ui/actions";

/*
  The row verbs that are the same on every list, so each surface does not write them again.

  Kept deliberately small. UX-PLAN asks for a context menu on every row, and the easy way to
  fill one is with edit and delete — but most of these lists have no per-row endpoint behind
  them, and a menu item that calls one that does not exist is worse than an absent item. These
  three are real:

    copy     puts a field on the clipboard. The audit counted one copy affordance in the whole
             portfolio, against screens full of ids, slugs and email addresses whose only
             purpose is to be pasted somewhere else.
    filterBy puts the row's value into the list's own search box, which is how you answer
             "what else is there for this customer" without retyping it.
    open     a route the surface already links to, repeated in the menu so a row has one place
             to look rather than one visible button and nothing else.

  Anything specific to a surface belongs on that surface. This is the shared floor.

  MobiStack has the same three in `web/src/ui/rowVerbs.ts`, written against its own action type
  because that app has no Tailwind and cannot use this menu. The two are kept in step by hand;
  they are twenty lines each and a shared package for them would have to be imported by an app
  that cannot render it.
*/

/** Drops the absent entries, so a surface can write a verb list with holes in it. */
export function verbs(...items: (RowAction | null | undefined | false)[]): RowAction[] {
  return items.filter((item): item is RowAction => Boolean(item));
}

/**
 * Copies one field of a row.
 *
 * Returns null for an empty value rather than a disabled item: a menu lists what can be done,
 * and a permanently greyed row is only noise. A customer with no phone has no "Copy phone".
 *
 * The toast is not decoration. A clipboard write is silent and instant, so without it there is
 * nothing to distinguish a success from a click that missed.
 */
export function copyVerb(id: string, label: string, value: string | null | undefined): RowAction | null {
  const text = value?.toString().trim();
  if (!text) return null;
  return {
    id: `copy-${id}`,
    label,
    group: "copy",
    onSelect: async () => {
      try {
        // Present only over HTTPS and on localhost. Everything here is served over HTTPS, but
        // a silent failure is the one outcome worth ruling out.
        await navigator.clipboard.writeText(text);
        toast.success(text.length > 40 ? "Copied to clipboard" : `Copied ${text}`);
      } catch {
        toast.error("Could not reach the clipboard. Copy it by hand.");
      }
    },
  };
}

/**
 * Puts a value into the list's own search box.
 *
 * Takes the setter rather than navigating itself, because each list owns its filter state and
 * some of them keep it in the URL.
 */
export function filterVerb(
  id: string,
  label: string,
  value: string | null | undefined,
  apply: (value: string) => void,
): RowAction | null {
  const text = value?.toString().trim();
  if (!text) return null;
  return { id: `filter-${id}`, label, group: "filter", onSelect: () => apply(text) };
}
