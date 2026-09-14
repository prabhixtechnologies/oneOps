import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { useAuth } from "@/lib/auth";
import { priorities, statusLabel, threadStatuses, useAssignableMembers, useTags } from "@/features/helpdesk/api";
import {
  actionLabel,
  buildAction,
  buildCondition,
  fieldLabel,
  matchModes,
  operatorsForField,
  parseAction,
  parseCondition,
  routingActionTypes,
  routingConditionFields,
  useAdminMailboxes,
  useTeams,
  type MatchMode,
  type RoutingActionType,
  type RoutingConditionField,
  type RoutingOperator,
  type SaveRoutingRuleInput,
  type RoutingRule,
} from "@/features/helpdesk/mailbox-admin";

interface ConditionRow {
  id: string;
  field: RoutingConditionField;
  headerName: string;
  op: RoutingOperator;
  rawValue: string;
  listValues: string[];
}

interface ActionRow {
  id: string;
  type: RoutingActionType;
  rawValue: string;
}

export function RoutingRuleEditor({
  initial,
  busy,
  error,
  onCancel,
  onSave,
}: {
  initial?: RoutingRule;
  busy?: boolean;
  error?: string;
  onCancel: () => void;
  onSave: (input: SaveRoutingRuleInput) => void;
}) {
  const { me } = useAuth();
  const members = useAssignableMembers(me?.organizationId ?? undefined);
  const teams = useTeams();
  const mailboxes = useAdminMailboxes();
  const tags = useTags();

  const [name, setName] = useState(initial?.name ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [enabled, setEnabled] = useState(initial?.enabled ?? true);
  const [match, setMatch] = useState<MatchMode>(initial?.match ?? "ALL");
  const [continueAfter, setContinueAfter] = useState(initial?.continueAfterMatch ?? false);
  const [conditions, setConditions] = useState<ConditionRow[]>(() =>
    initial?.conditions.length
      ? initial.conditions.map((c, i) => ({ id: String(i), ...parseCondition(c) }))
      : [emptyCondition("0")],
  );
  const [actions, setActions] = useState<ActionRow[]>(() =>
    initial?.actions.length
      ? initial.actions.map((a, i) => ({ id: String(i), ...parseAction(a) }))
      : [emptyAction("0")],
  );
  const [localError, setLocalError] = useState<string>();

  const addCondition = () =>
    setConditions((prev) => [...prev, emptyCondition(String(Date.now()))]);
  const removeCondition = (id: string) =>
    setConditions((prev) => (prev.length <= 1 ? prev : prev.filter((c) => c.id !== id)));

  const addAction = () => setActions((prev) => [...prev, emptyAction(String(Date.now()))]);
  const removeAction = (id: string) =>
    setActions((prev) => (prev.length <= 1 ? prev : prev.filter((a) => a.id !== id)));

  const updateCondition = (id: string, patch: Partial<ConditionRow>) => {
    setConditions((prev) =>
      prev.map((c) => {
        if (c.id !== id) return c;
        const next = { ...c, ...patch };
        if (patch.field && patch.field !== c.field) {
          const ops = operatorsForField(patch.field);
          next.op = ops[0] ?? "EQUALS";
          next.rawValue = patch.field === "HAS_ATTACHMENT" ? "true" : "";
          next.listValues = [];
        }
        return next;
      }),
    );
  };

  const updateAction = (id: string, patch: Partial<ActionRow>) => {
    setActions((prev) =>
      prev.map((a) => (a.id === id ? { ...a, ...patch, rawValue: patch.type ? "" : (patch.rawValue ?? a.rawValue) } : a)),
    );
  };

  const submit = () => {
    setLocalError(undefined);
    if (!name.trim()) {
      setLocalError("Name is required.");
      return;
    }
    if (conditions.some((c) => c.field === "HEADER" && !c.headerName.trim())) {
      setLocalError("Custom header conditions need a header name.");
      return;
    }
    const builtConditions = conditions.map((c) =>
      buildCondition(c.field, c.headerName, c.op, c.rawValue, c.listValues),
    );
    const builtActions = actions.map((a) => buildAction(a.type, a.rawValue));
    onSave({
      name: name.trim(),
      description: description.trim() || undefined,
      enabled,
      match,
      continueAfterMatch: continueAfter,
      conditions: builtConditions,
      actions: builtActions,
    });
  };

  const displayError = localError ?? error;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-sm font-medium">{initial ? "Edit rule" : "New rule"}</h2>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={onCancel} disabled={busy}>
            Cancel
          </Button>
          <Button size="sm" onClick={submit} disabled={busy}>
            {busy ? "Saving…" : "Save rule"}
          </Button>
        </div>
      </div>

      {displayError ? (
        <p className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {displayError}
        </p>
      ) : null}

      <div className="space-y-3">
        <div>
          <label className="mb-1 block text-xs text-text-muted" htmlFor="rule-name">
            Name
          </label>
          <Input id="rule-name" value={name} onChange={(e) => setName(e.target.value)} maxLength={160} />
        </div>
        <div>
          <label className="mb-1 block text-xs text-text-muted" htmlFor="rule-description">
            Description <span className="text-text-muted/70">(optional)</span>
          </label>
          <Input
            id="rule-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={500}
            placeholder="Why this rule exists"
          />
        </div>
        <div className="flex flex-wrap gap-4">
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
            Enabled
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={continueAfter}
              onChange={(e) => setContinueAfter(e.target.checked)}
            />
            Continue after match
          </label>
        </div>
        <div>
          <label className="mb-1 block text-xs text-text-muted" htmlFor="rule-match">
            Match mode
          </label>
          <select
            id="rule-match"
            value={match}
            onChange={(e) => setMatch(e.target.value as MatchMode)}
            className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm"
          >
            {matchModes.map((m) => (
              <option key={m} value={m}>
                {m === "ALL" ? "All conditions must match" : "Any condition may match"}
              </option>
            ))}
          </select>
        </div>
      </div>

      <section>
        <div className="mb-2 flex items-center justify-between">
          <h3 className="text-sm font-medium">Conditions</h3>
          <Button variant="outline" size="sm" onClick={addCondition} disabled={conditions.length >= 20}>
            Add condition
          </Button>
        </div>
        <div className="space-y-3">
          {conditions.map((c) => (
            <div key={c.id} className="rounded-lg border border-border p-3">
              <div className="grid gap-2 sm:grid-cols-3">
                <select
                  aria-label="Condition field"
                  value={c.field}
                  onChange={(e) =>
                    updateCondition(c.id, { field: e.target.value as RoutingConditionField })
                  }
                  className="rounded-md border border-border bg-surface px-2 py-1.5 text-sm"
                >
                  {routingConditionFields.map((f) => (
                    <option key={f} value={f}>
                      {fieldLabel(f)}
                    </option>
                  ))}
                </select>
                <select
                  aria-label="Operator"
                  value={c.op}
                  onChange={(e) => updateCondition(c.id, { op: e.target.value as RoutingOperator })}
                  className="rounded-md border border-border bg-surface px-2 py-1.5 text-sm"
                >
                  {operatorsForField(c.field).map((op) => (
                    <option key={op} value={op}>
                      {op.toLowerCase().replace(/_/g, " ")}
                    </option>
                  ))}
                </select>
                <Button
                  variant="ghost"
                  size="sm"
                  className="justify-self-end"
                  onClick={() => removeCondition(c.id)}
                >
                  Remove
                </Button>
              </div>
              {c.field === "HEADER" ? (
                <Input
                  className="mt-2"
                  placeholder="Header name (e.g. X-Priority)"
                  aria-label="Header name"
                  value={c.headerName}
                  onChange={(e) => updateCondition(c.id, { headerName: e.target.value })}
                />
              ) : null}
              <div className="mt-2">
                <ConditionValueInput row={c} onChange={(patch) => updateCondition(c.id, patch)} />
              </div>
            </div>
          ))}
        </div>
      </section>

      <section>
        <div className="mb-2 flex items-center justify-between">
          <h3 className="text-sm font-medium">Actions</h3>
          <Button variant="outline" size="sm" onClick={addAction} disabled={actions.length >= 10}>
            Add action
          </Button>
        </div>
        <div className="space-y-3">
          {actions.map((a) => (
            <div key={a.id} className="rounded-lg border border-border p-3">
              <div className="grid gap-2 sm:grid-cols-2">
                <select
                  aria-label="Action type"
                  value={a.type}
                  onChange={(e) => updateAction(a.id, { type: e.target.value as RoutingActionType })}
                  className="rounded-md border border-border bg-surface px-2 py-1.5 text-sm"
                >
                  {routingActionTypes.map((t) => (
                    <option key={t} value={t}>
                      {actionLabel(t)}
                    </option>
                  ))}
                </select>
                <Button
                  variant="ghost"
                  size="sm"
                  className="justify-self-end"
                  onClick={() => removeAction(a.id)}
                >
                  Remove
                </Button>
              </div>
              <div className="mt-2">
                <ActionValueInput
                  row={a}
                  members={members.data?.items ?? []}
                  teams={teams.data?.items ?? []}
                  mailboxes={mailboxes.data ?? []}
                  tags={tags.data ?? []}
                  onChange={(rawValue) => updateAction(a.id, { rawValue })}
                />
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function ConditionValueInput({
  row,
  onChange,
}: {
  row: ConditionRow;
  onChange: (patch: Partial<ConditionRow>) => void;
}) {
  if (row.field === "HAS_ATTACHMENT") {
    return (
      <select
        aria-label="Has attachment"
        value={row.rawValue}
        onChange={(e) => onChange({ rawValue: e.target.value })}
        className="w-full rounded-md border border-border bg-surface px-2 py-1.5 text-sm"
      >
        <option value="true">Yes</option>
        <option value="false">No</option>
      </select>
    );
  }
  if (row.op === "IN") {
    return (
      <Textarea
        placeholder="One value per line"
        aria-label="Match any of these values, one per line"
        value={row.listValues.join("\n")}
        onChange={(e) =>
          onChange({ listValues: e.target.value.split("\n"), rawValue: "" })
        }
        rows={3}
      />
    );
  }
  return (
    <Input
      placeholder={row.field === "SPAM_SCORE" ? "Numeric value" : "Value"}
      aria-label={row.field === "SPAM_SCORE" ? "Spam score" : "Condition value"}
      value={row.rawValue}
      onChange={(e) => onChange({ rawValue: e.target.value })}
    />
  );
}

function ActionValueInput({
  row,
  members,
  teams,
  mailboxes,
  tags,
  onChange,
}: {
  row: ActionRow;
  members: { userId: string; displayName?: string | null; email?: string | null }[];
  teams: { id: string; name: string }[];
  mailboxes: { id: string; name: string }[];
  tags: { id: string; name: string }[];
  onChange: (rawValue: string) => void;
}) {
  if (row.type === "MARK_SPAM") {
    return <p className="text-xs text-text-muted">No value needed.</p>;
  }
  if (row.type === "ASSIGN_USER") {
    return (
      <select
        aria-label="Assign to user"
        value={row.rawValue}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-md border border-border bg-surface px-2 py-1.5 text-sm"
      >
        <option value="">Select user…</option>
        {members.map((m) => (
          <option key={m.userId} value={m.userId}>
            {m.displayName ?? m.email ?? m.userId}
          </option>
        ))}
      </select>
    );
  }
  if (row.type === "ASSIGN_TEAM") {
    return (
      <select
        aria-label="Assign to team"
        value={row.rawValue}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-md border border-border bg-surface px-2 py-1.5 text-sm"
      >
        <option value="">Select team…</option>
        {teams.map((t) => (
          <option key={t.id} value={t.id}>
            {t.name}
          </option>
        ))}
      </select>
    );
  }
  if (row.type === "MOVE_MAILBOX") {
    return (
      <select
        aria-label="Move to mailbox"
        value={row.rawValue}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-md border border-border bg-surface px-2 py-1.5 text-sm"
      >
        <option value="">Select mailbox…</option>
        {mailboxes.map((m) => (
          <option key={m.id} value={m.id}>
            {m.name}
          </option>
        ))}
      </select>
    );
  }
  if (row.type === "SET_PRIORITY") {
    return (
      <select
        aria-label="Priority"
        value={row.rawValue}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-md border border-border bg-surface px-2 py-1.5 text-sm"
      >
        <option value="">Select priority…</option>
        {priorities.map((p) => (
          <option key={p} value={p}>
            {statusLabel(p)}
          </option>
        ))}
      </select>
    );
  }
  if (row.type === "SET_STATUS") {
    return (
      <select
        aria-label="Status"
        value={row.rawValue}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-md border border-border bg-surface px-2 py-1.5 text-sm"
      >
        <option value="">Select status…</option>
        {threadStatuses.map((s) => (
          <option key={s} value={s}>
            {statusLabel(s)}
          </option>
        ))}
      </select>
    );
  }
  if (row.type === "ADD_TAG") {
    return (
      <select
        aria-label="Tag"
        value={row.rawValue}
        onChange={(e) => onChange(e.target.value)}
        className="w-full rounded-md border border-border bg-surface px-2 py-1.5 text-sm"
      >
        <option value="">Select tag…</option>
        {tags.map((t) => (
          <option key={t.id} value={t.name}>
            {t.name}
          </option>
        ))}
      </select>
    );
  }
  if (row.type === "APPLY_SLA") {
    return (
      <Input
        type="number"
        min={1}
        placeholder="Minutes"
        aria-label="SLA minutes"
        value={row.rawValue}
        onChange={(e) => onChange(e.target.value)}
      />
    );
  }
  return (
    <Textarea
      placeholder="Auto-reply body"
      aria-label="Auto-reply body"
      value={row.rawValue}
      onChange={(e) => onChange(e.target.value)}
      rows={3}
    />
  );
}

function emptyCondition(id: string): ConditionRow {
  return { id, field: "SUBJECT", headerName: "", op: "CONTAINS", rawValue: "", listValues: [] };
}

function emptyAction(id: string): ActionRow {
  return { id, type: "ADD_TAG", rawValue: "" };
}
