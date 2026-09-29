import { useState } from "react";
import { AlertTriangle, Plus, Tag as TagIcon, Trash2 } from "lucide-react";
import { TAG_SWATCHES, TAG_TONES } from "@prabhixtechnologies/brand";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { EmptyState } from "@/components/shared/states";
import { Skeleton } from "@/components/ui/skeleton";
import { getApiErrorMessage } from "@/lib/api-client";
import {
  useCreateTag,
  useDeleteTag,
  useTags,
  useUpdateTag,
  type Tag,
} from "@/features/helpdesk/api";
import { adminErrorHint } from "./MailSettingsLayout";
import { ConfirmDialog } from "./ConfirmDialog";

/**
 * The presets offered in the colour picker.
 *
 * These were six hand-picked hexes that existed nowhere else in the portfolio and had never
 * been through the contrast gate. They are now the shared tag swatches, so a tag the user
 * creates here lands on the same palette as every seeded swatch elsewhere.
 *
 * Inks rather than backgrounds, because a tag renders as a small solid dot: the pale `bg` half
 * of a swatch is nearly invisible against a card. The value is stored in the database and fed
 * to `<input type="color">`, which is why this is the one place that needs literal hex instead
 * of `var(--px-tag-*)`. The neutral swatch is skipped — it is the "no category" colour.
 *
 * Named, not just coloured: the old `aria-label` read "Colour #0e7490", which tells a screen
 * reader nothing. It now says "Colour teal".
 */
const PRESET_COLOURS = TAG_TONES.filter((tone) => tone !== "neutral")
  .slice(0, 8)
  .map((tone) => ({ name: tone, hex: TAG_SWATCHES.light[tone].ink }));

export default function TagsPage() {
  const tags = useTags();
  const create = useCreateTag();
  const update = useUpdateTag();
  const remove = useDeleteTag();

  const [showCreate, setShowCreate] = useState(false);
  const [name, setName] = useState("");
  const [colour, setColour] = useState(PRESET_COLOURS[0].hex);
  const [editing, setEditing] = useState<Tag | null>(null);
  const [editName, setEditName] = useState("");
  const [editColour, setEditColour] = useState("");
  const [error, setError] = useState<string>();
  const [confirmDelete, setConfirmDelete] = useState<Tag>();

  const submitCreate = async () => {
    setError(undefined);
    try {
      await create.mutateAsync({ name: name.trim(), colour });
      setShowCreate(false);
      setName("");
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const submitEdit = async () => {
    if (!editing) return;
    setError(undefined);
    try {
      await update.mutateAsync({
        tagId: editing.id,
        name: editName.trim(),
        colour: editColour,
      });
      setEditing(null);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const doDelete = async () => {
    if (!confirmDelete) return;
    setError(undefined);
    try {
      await remove.mutateAsync(confirmDelete.id);
      setConfirmDelete(undefined);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="mx-auto max-w-3xl p-6">
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-xl font-semibold tracking-tight">Tags</h1>
          <p className="mt-1 text-sm text-text-muted">
            Organization-wide labels applied to tickets in the inbox.
          </p>
        </div>
        <Button size="sm" onClick={() => setShowCreate((v) => !v)}>
          <Plus className="size-4" />
          Add tag
        </Button>
      </div>

      {error ? (
        <p className="mb-4 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      {showCreate ? (
        <div className="mb-6 space-y-3 rounded-lg border border-border p-4">
          <Input
            placeholder="Tag name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            aria-label="Tag name"
          />
          <ColourPicker value={colour} onChange={setColour} />
          <div className="flex gap-2">
            <Button size="sm" disabled={create.isPending || !name.trim()} onClick={() => void submitCreate()}>
              {create.isPending ? "Creating…" : "Create"}
            </Button>
            <Button size="sm" variant="outline" onClick={() => setShowCreate(false)}>
              Cancel
            </Button>
          </div>
        </div>
      ) : null}

      {tags.isPending ? (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      ) : tags.isError ? (
        <EmptyState
          icon={<AlertTriangle className="size-8" />}
          title="Tags could not be loaded"
          description={adminErrorHint(tags.error) ?? getApiErrorMessage(tags.error)}
          action={{ label: "Try again", onClick: () => void tags.refetch() }}
        />
      ) : (tags.data ?? []).length === 0 ? (
        <EmptyState
          icon={<TagIcon className="size-8" />}
          title="No tags yet"
          description="Create tags to classify tickets in the inbox."
          action={{ label: "Add the first tag", onClick: () => setShowCreate(true) }}
        />
      ) : (
        <ul className="divide-y divide-border rounded-lg border border-border">
          {(tags.data ?? []).map((tag) =>
            editing?.id === tag.id ? (
              <li key={tag.id} className="space-y-2 px-4 py-3">
                <Input
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  aria-label="Edit tag name"
                />
                <ColourPicker value={editColour} onChange={setEditColour} />
                <div className="flex gap-2">
                  <Button size="sm" disabled={update.isPending} onClick={() => void submitEdit()}>
                    Save
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => setEditing(null)}>
                    Cancel
                  </Button>
                </div>
              </li>
            ) : (
              <li key={tag.id} className="flex items-center gap-3 px-4 py-3">
                <span
                  className="size-3 shrink-0 rounded-full"
                  style={{ backgroundColor: tag.colour }}
                />
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-medium">{tag.name}</div>
                  <div className="text-xs text-text-muted">{tag.usageCount} uses</div>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setEditing(tag);
                    setEditName(tag.name);
                    setEditColour(tag.colour);
                  }}
                >
                  Rename
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`Delete ${tag.name}`}
                  onClick={() => setConfirmDelete(tag)}
                >
                  <Trash2 className="size-4 text-destructive" />
                </Button>
              </li>
            ),
          )}
        </ul>
      )}

      {confirmDelete ? (
        <ConfirmDialog
          title={`Delete ${confirmDelete.name}?`}
          hint="The tag will be removed from tickets that use it."
          busy={remove.isPending}
          onCancel={() => setConfirmDelete(undefined)}
          onConfirm={() => void doDelete()}
        />
      ) : null}
    </div>
  );
}

function ColourPicker({ value, onChange }: { value: string; onChange: (c: string) => void }) {
  return (
    <div className="flex flex-wrap gap-2">
      {PRESET_COLOURS.map(({ name, hex }) => (
        <button
          key={name}
          type="button"
          aria-label={`Colour ${name}`}
          aria-pressed={value === hex}
          onClick={() => onChange(hex)}
          className={`size-7 rounded-full border-2 ${value === hex ? "border-text" : "border-transparent"}`}
          style={{ backgroundColor: hex }}
        />
      ))}
      <Input
        type="color"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="h-7 w-12 cursor-pointer p-0"
        aria-label="Custom colour"
      />
    </div>
  );
}
