import { useState } from "react";
import { useOutletContext } from "react-router";
import { ArrowDown, ArrowUp, GitBranch, Pencil, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/shared/states";
import { getApiErrorMessage } from "@/lib/api-client";
import {
  useCreateRoutingRule,
  useDeleteRoutingRule,
  useUpdateRoutingRule,
  type MailboxDetail,
  type RoutingRule,
  type SaveRoutingRuleInput,
} from "@/features/helpdesk/mailbox-admin";
import { ConfirmDialog } from "./ConfirmDialog";
import { RoutingRuleEditor } from "./RoutingRuleEditor";

export function MailboxRoutingSection() {
  const { mailbox } = useOutletContext<{ mailbox: MailboxDetail }>();
  const create = useCreateRoutingRule();
  const updateRule = useUpdateRoutingRule();
  const remove = useDeleteRoutingRule();

  const [editing, setEditing] = useState<RoutingRule | "new" | null>(null);
  const [error, setError] = useState<string>();
  const [confirmDelete, setConfirmDelete] = useState<RoutingRule>();

  const rules = [...mailbox.routingRules].sort((a, b) => a.priority - b.priority);

  const saveRule = async (input: SaveRoutingRuleInput, ruleId?: string) => {
    setError(undefined);
    try {
      if (ruleId) {
        await updateRule.mutateAsync({ mailboxId: mailbox.id, ruleId, rule: input });
      } else {
        const nextPriority =
          rules.length === 0 ? 0 : Math.max(...rules.map((r) => r.priority)) + 1;
        await create.mutateAsync({
          mailboxId: mailbox.id,
          rule: { ...input, priority: input.priority ?? nextPriority },
        });
      }
      setEditing(null);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const moveRule = async (rule: RoutingRule, direction: "up" | "down") => {
    const idx = rules.findIndex((r) => r.id === rule.id);
    const swapWith = direction === "up" ? rules[idx - 1] : rules[idx + 1];
    if (!swapWith) return;
    setError(undefined);
    try {
      await Promise.all([
        updateRule.mutateAsync({
          mailboxId: mailbox.id,
          ruleId: rule.id,
          rule: ruleToInput(rule, swapWith.priority),
        }),
        updateRule.mutateAsync({
          mailboxId: mailbox.id,
          ruleId: swapWith.id,
          rule: ruleToInput(swapWith, rule.priority),
        }),
      ]);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const doDelete = async () => {
    if (!confirmDelete) return;
    setError(undefined);
    try {
      await remove.mutateAsync({ mailboxId: mailbox.id, ruleId: confirmDelete.id });
      setConfirmDelete(undefined);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  if (editing) {
    return (
      <RoutingRuleEditor
        initial={editing === "new" ? undefined : editing}
        busy={create.isPending || updateRule.isPending}
        error={error}
        onCancel={() => {
          setEditing(null);
          setError(undefined);
        }}
        onSave={(input) => void saveRule(input, editing === "new" ? undefined : editing.id)}
      />
    );
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-text-muted">
          Rules run in priority order when mail arrives. Lower numbers run first.
        </p>
        <Button size="sm" onClick={() => setEditing("new")}>
          <Plus className="size-4" />
          Add rule
        </Button>
      </div>

      {error ? (
        <p className="mb-4 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      {rules.length === 0 ? (
        <EmptyState
          icon={<GitBranch className="size-8" />}
          title="No routing rules"
          description="Add a rule to assign, tag, or auto-reply to incoming mail."
        />
      ) : (
        <ul className="divide-y divide-border rounded-lg border border-border">
          {rules.map((rule, idx) => (
            <li key={rule.id} className="flex items-start gap-3 px-4 py-3">
              <div className="flex flex-col gap-0.5">
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-11"
                  aria-label="Move up"
                  disabled={idx === 0 || updateRule.isPending}
                  onClick={() => void moveRule(rule, "up")}
                >
                  <ArrowUp className="size-3" />
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="size-11"
                  aria-label="Move down"
                  disabled={idx === rules.length - 1 || updateRule.isPending}
                  onClick={() => void moveRule(rule, "down")}
                >
                  <ArrowDown className="size-3" />
                </Button>
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-medium">{rule.name}</span>
                  <span className="font-mono text-[10px] text-text-muted">#{rule.priority}</span>
                  {!rule.enabled ? <Badge variant="secondary">Disabled</Badge> : null}
                  {rule.continueAfterMatch ? (
                    <Badge variant="outline">Continue after match</Badge>
                  ) : null}
                </div>
                <p className="mt-1 text-xs text-text-muted">
                  {rule.conditions.length} condition{rule.conditions.length === 1 ? "" : "s"},{" "}
                  {rule.match === "ALL" ? "all must match" : "any may match"} ·{" "}
                  {rule.actions.length} action{rule.actions.length === 1 ? "" : "s"}
                </p>
              </div>
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Edit ${rule.name}`}
                onClick={() => setEditing(rule)}
              >
                <Pencil className="size-4" />
              </Button>
              <Button
                variant="ghost"
                size="icon"
                aria-label={`Delete ${rule.name}`}
                onClick={() => setConfirmDelete(rule)}
              >
                <Trash2 className="size-4 text-destructive" />
              </Button>
            </li>
          ))}
        </ul>
      )}

      {confirmDelete ? (
        <ConfirmDialog
          title={`Delete ${confirmDelete.name}?`}
          hint="Incoming mail will no longer be processed by this rule."
          busy={remove.isPending}
          onCancel={() => setConfirmDelete(undefined)}
          onConfirm={() => void doDelete()}
        />
      ) : null}
    </div>
  );
}

function ruleToInput(rule: RoutingRule, priority?: number): SaveRoutingRuleInput {
  return {
    name: rule.name,
    enabled: rule.enabled,
    priority: priority ?? rule.priority,
    match: rule.match,
    conditions: rule.conditions,
    actions: rule.actions,
    continueAfterMatch: rule.continueAfterMatch,
  };
}
