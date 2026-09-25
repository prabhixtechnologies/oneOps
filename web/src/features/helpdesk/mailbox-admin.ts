import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import { apiRequest, apiRequestVoid } from "@/lib/api-client";
import { helpdeskKeys } from "./api";

/**
 * Shared-mailbox administration: configuration the queue reads but does not edit.
 *
 * Kept separate from helpdesk.ts, which is the agent working a ticket. An admin configuring SLAs
 * and a lead answering mail share an organization but not a screen, and mixing the two files would
 * make both harder to follow.
 */

export const memberAccessLevels = ["MEMBER", "LEAD"] as const;
export type MemberAccessLevel = (typeof memberAccessLevels)[number];

export const matchModes = ["ALL", "ANY"] as const;
export type MatchMode = (typeof matchModes)[number];

export const routingConditionFields = [
  "FROM",
  "FROM_DOMAIN",
  "TO",
  "CC",
  "SUBJECT",
  "BODY",
  "HAS_ATTACHMENT",
  "SPAM_SCORE",
  "HEADER",
] as const;

export type RoutingConditionField = (typeof routingConditionFields)[number];

export const routingOperators = ["EQUALS", "CONTAINS", "MATCHES", "IN", "GT", "LT"] as const;
export type RoutingOperator = (typeof routingOperators)[number];

export const numericRoutingFields: RoutingConditionField[] = ["SPAM_SCORE"];
export const numericRoutingOperators: RoutingOperator[] = ["GT", "LT"];

export const routingActionTypes = [
  "ASSIGN_USER",
  "ASSIGN_TEAM",
  "SET_PRIORITY",
  "SET_STATUS",
  "ADD_TAG",
  "APPLY_SLA",
  "MOVE_MAILBOX",
  "AUTO_REPLY",
  "MARK_SPAM",
] as const;

export type RoutingActionType = (typeof routingActionTypes)[number];

export const uuidRoutingActions: RoutingActionType[] = [
  "ASSIGN_USER",
  "ASSIGN_TEAM",
  "MOVE_MAILBOX",
];

export const PERMISSION_MAILBOX_MANAGE = "MAIL_MAILBOX_MANAGE";

/**
 * Tags and canned replies are guarded by MAIL_THREAD_UPDATE, not MAIL_MAILBOX_MANAGE.
 *
 * That is not an oversight on the server: an agent triaging a ticket creates tags and saves replies
 * as part of the work, so the permission follows the thread rather than the mailbox. Checking
 * MAIL_MAILBOX_MANAGE here instead would be wrong in both directions — hiding those sections from
 * the agents who use them, and offering them to an administrator whose writes the API then refuses.
 */
export const PERMISSION_THREAD_UPDATE = "MAIL_THREAD_UPDATE";

const businessHoursSchema = z.object({
  timezone: z.string().nullish(),
  workingDays: z.array(z.number()).default([]),
  startTime: z.string(),
  endTime: z.string(),
  holidays: z.array(z.string()).default([]),
});

export type BusinessHours = z.infer<typeof businessHoursSchema>;

export const mailboxMemberSchema = z.object({
  id: z.string(),
  userId: z.string().nullish(),
  teamId: z.string().nullish(),
  name: z.string(),
  email: z.string().nullish(),
  accessLevel: z.enum(memberAccessLevels),
});

export type MailboxMember = z.infer<typeof mailboxMemberSchema>;

const routingRuleSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  priority: z.number(),
  conditions: z.array(z.record(z.unknown())).default([]),
  match: z.enum(matchModes),
  actions: z.array(z.record(z.unknown())).default([]),
  continueAfterMatch: z.boolean(),
  enabled: z.boolean(),
});

export type RoutingRule = z.infer<typeof routingRuleSchema>;

export const mailboxDetailSchema = z.object({
  id: z.string(),
  name: z.string(),
  email: z.string(),
  description: z.string().nullish(),
  memberCount: z.number(),
  openThreadCount: z.number(),
  slaFirstResponseMins: z.number().nullish(),
  slaResolutionMins: z.number().nullish(),
  signature: z.string().nullish(),
  createdAt: z.string(),
  mailPasswordUpdatedAt: z.string().nullish(),
  members: z.array(mailboxMemberSchema).default([]),
  routingRules: z.array(routingRuleSchema).default([]),
  businessHours: businessHoursSchema.nullish(),
});

export type MailboxDetail = z.infer<typeof mailboxDetailSchema>;

export const mailboxAdminSummarySchema = z.object({
  id: z.string(),
  address: z.string(),
  name: z.string(),
  kind: z.enum(["SHARED", "PERSONAL", "SYSTEM"]),
  status: z.string(),
  openThreadCount: z.number(),
  unassignedCount: z.number(),
});

export type MailboxAdminSummary = z.infer<typeof mailboxAdminSummarySchema>;

const teamSchema = z.object({
  id: z.string(),
  slug: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  leadUserId: z.string().nullish(),
  memberCount: z.number(),
});

export type Team = z.infer<typeof teamSchema>;

const teamPageSchema = z.object({
  items: z.array(teamSchema),
});

export const mailboxAdminKeys = {
  all: ["mailbox-admin"] as const,
  mailboxes: ["mailbox-admin", "mailboxes"] as const,
  mailbox: (id: string) => ["mailbox-admin", "mailbox", id] as const,
  teams: ["mailbox-admin", "teams"] as const,
};

// -------------------------------------------------------------------------------------------------
// Queries
// -------------------------------------------------------------------------------------------------

export function useAdminMailboxes() {
  return useQuery({
    queryKey: mailboxAdminKeys.mailboxes,
    queryFn: () => apiRequest("/oneops/mail/mailboxes", z.array(mailboxAdminSummarySchema)),
    staleTime: 30_000,
  });
}

export function useMailboxDetail(mailboxId: string | undefined) {
  return useQuery({
    queryKey: mailboxId ? mailboxAdminKeys.mailbox(mailboxId) : ["mailbox-admin", "mailbox", "none"],
    queryFn: () => apiRequest(`/oneops/mail/mailboxes?id=${mailboxId}`, mailboxDetailSchema),
    enabled: !!mailboxId,
  });
}

/** Teams to grant mailbox access. Failure is expected without ORG_TEAM_READ — the add-member form hides team grants. */
export function useTeams() {
  return useQuery({
    queryKey: mailboxAdminKeys.teams,
    queryFn: () => apiRequest("/oneops/teams", teamPageSchema),
    retry: false,
    staleTime: 5 * 60_000,
  });
}

// -------------------------------------------------------------------------------------------------
// Mutations
// -------------------------------------------------------------------------------------------------

function useMailboxInvalidation() {
  const queryClient = useQueryClient();
  return (mailboxId?: string) => {
    if (mailboxId) {
      void queryClient.invalidateQueries({ queryKey: mailboxAdminKeys.mailbox(mailboxId) });
    }
    void queryClient.invalidateQueries({ queryKey: mailboxAdminKeys.mailboxes });
    void queryClient.invalidateQueries({ queryKey: helpdeskKeys.mailboxes });
  };
}

export function useUpdateMailbox() {
  const invalidate = useMailboxInvalidation();
  return useMutation({
    mutationFn: (vars: {
      mailboxId: string;
      name?: string;
      description?: string;
      signature?: string;
      slaFirstResponseMins?: number;
      slaResolutionMins?: number;
      businessHours?: BusinessHours;
    }) =>
      apiRequest(`/oneops/mail/mailboxes?id=${vars.mailboxId}`, mailboxDetailSchema, {
        method: "PATCH",
        body: {
          name: vars.name,
          description: vars.description,
          signature: vars.signature,
          slaFirstResponseMins: vars.slaFirstResponseMins,
          slaResolutionMins: vars.slaResolutionMins,
          businessHours: vars.businessHours,
        },
      }),
    onSuccess: (_data, vars) => invalidate(vars.mailboxId),
  });
}

export function useCreateMailbox() {
  const invalidate = useMailboxInvalidation();
  return useMutation({
    mutationFn: (vars: {
      address: string;
      name: string;
      description?: string;
      kind?: "SHARED" | "PERSONAL";
      ownerUserId?: string;
    }) =>
      apiRequest("/oneops/mail/mailboxes", mailboxAdminSummarySchema, {
        body: {
          address: vars.address,
          name: vars.name,
          description: vars.description,
          kind: vars.kind ?? "SHARED",
          ownerUserId: vars.ownerUserId,
        },
      }),
    onSuccess: () => invalidate(),
  });
}

export function useDeleteMailbox() {
  const invalidate = useMailboxInvalidation();
  return useMutation({
    mutationFn: (mailboxId: string) =>
      apiRequestVoid(`/oneops/mail/mailboxes?id=${mailboxId}`, { method: "DELETE" }),
    onSuccess: () => invalidate(),
  });
}

const issuedMailPasswordSchema = z.object({
  address: z.string(),
  password: z.string(),
  issuedAt: z.string(),
});

export type IssuedMailPassword = z.infer<typeof issuedMailPasswordSchema>;

export function useIssueMailPassword() {
  const invalidate = useMailboxInvalidation();
  return useMutation({
    mutationFn: (mailboxId: string) =>
      apiRequest(`/oneops/mail/mailboxes/mail-password?id=${mailboxId}`, issuedMailPasswordSchema, {
        method: "POST",
      }),
    onSuccess: (_data, mailboxId) => invalidate(mailboxId),
  });
}

export function useRevokeMailPassword() {
  const invalidate = useMailboxInvalidation();
  return useMutation({
    mutationFn: (mailboxId: string) =>
      apiRequestVoid(`/oneops/mail/mailboxes/mail-password?id=${mailboxId}`, { method: "DELETE" }),
    onSuccess: (_data, mailboxId) => invalidate(mailboxId),
  });
}

const mailDomainSchema = z.object({
  id: z.string(),
  domain: z.string(),
  status: z.string(),
});

export function useMailDomains() {
  return useQuery({
    queryKey: ["mailbox-admin", "domains"],
    queryFn: () => apiRequest("/oneops/mail/domains", z.array(mailDomainSchema)),
    staleTime: 60_000,
  });
}

export function useAddMailboxMember() {
  const invalidate = useMailboxInvalidation();
  return useMutation({
    mutationFn: (vars: {
      mailboxId: string;
      userId?: string;
      teamId?: string;
      accessLevel: MemberAccessLevel;
    }) =>
      apiRequest(`/oneops/mail/mailboxes/members?id=${vars.mailboxId}`, mailboxMemberSchema, {
        body: {
          userId: vars.userId,
          teamId: vars.teamId,
          accessLevel: vars.accessLevel,
        },
      }),
    onSuccess: (_data, vars) => invalidate(vars.mailboxId),
  });
}

export function useUpdateMailboxMember() {
  const invalidate = useMailboxInvalidation();
  return useMutation({
    mutationFn: (vars: {
      mailboxId: string;
      memberId: string;
      accessLevel: MemberAccessLevel;
    }) =>
      apiRequest(
        `/oneops/mail/mailboxes/members?id=${vars.mailboxId}&memberId=${vars.memberId}`,
        mailboxMemberSchema,
        { method: "PATCH", body: { accessLevel: vars.accessLevel } },
      ),
    onSuccess: (_data, vars) => invalidate(vars.mailboxId),
  });
}

export function useRemoveMailboxMember() {
  const invalidate = useMailboxInvalidation();
  return useMutation({
    mutationFn: (vars: { mailboxId: string; memberId: string }) =>
      apiRequestVoid(`/oneops/mail/mailboxes/members?id=${vars.mailboxId}&memberId=${vars.memberId}`, {
        method: "DELETE",
      }),
    onSuccess: (_data, vars) => invalidate(vars.mailboxId),
  });
}

export interface SaveRoutingRuleInput {
  name: string;
  description?: string;
  enabled?: boolean;
  priority?: number;
  match?: MatchMode;
  conditions: Record<string, unknown>[];
  actions: Record<string, unknown>[];
  continueAfterMatch?: boolean;
}

export function useCreateRoutingRule() {
  const invalidate = useMailboxInvalidation();
  return useMutation({
    mutationFn: (vars: { mailboxId: string; rule: SaveRoutingRuleInput }) =>
      apiRequest(`/oneops/mail/mailboxes/routing-rules?id=${vars.mailboxId}`, routingRuleSchema, {
        body: vars.rule,
      }),
    onSuccess: (_data, vars) => invalidate(vars.mailboxId),
  });
}

export function useUpdateRoutingRule() {
  const invalidate = useMailboxInvalidation();
  return useMutation({
    mutationFn: (vars: { mailboxId: string; ruleId: string; rule: SaveRoutingRuleInput }) =>
      apiRequest(
        `/oneops/mail/mailboxes/routing-rules?id=${vars.mailboxId}&ruleId=${vars.ruleId}`,
        routingRuleSchema,
        { method: "PATCH", body: vars.rule },
      ),
    onSuccess: (_data, vars) => invalidate(vars.mailboxId),
  });
}

export function useDeleteRoutingRule() {
  const invalidate = useMailboxInvalidation();
  return useMutation({
    mutationFn: (vars: { mailboxId: string; ruleId: string }) =>
      apiRequestVoid(`/oneops/mail/mailboxes/routing-rules?id=${vars.mailboxId}&ruleId=${vars.ruleId}`, {
        method: "DELETE",
      }),
    onSuccess: (_data, vars) => invalidate(vars.mailboxId),
  });
}

// -------------------------------------------------------------------------------------------------
// Routing rule helpers
// -------------------------------------------------------------------------------------------------

export function operatorsForField(field: RoutingConditionField): RoutingOperator[] {
  if (field === "HEADER") return ["EQUALS", "CONTAINS", "MATCHES", "IN"];
  if (field === "HAS_ATTACHMENT") return ["EQUALS"];
  if (field === "SPAM_SCORE") return ["GT", "LT", "EQUALS"];
  return routingOperators.filter((op) => !numericRoutingOperators.includes(op));
}

export function fieldLabel(field: RoutingConditionField): string {
  if (field === "HEADER") return "Custom header";
  return field.charAt(0) + field.slice(1).toLowerCase().replace(/_/g, " ");
}

export function actionLabel(type: RoutingActionType): string {
  return type.charAt(0) + type.slice(1).toLowerCase().replace(/_/g, " ");
}

/** Builds the wire-format condition map the validator accepts. */
export function buildCondition(
  field: RoutingConditionField,
  headerName: string,
  op: RoutingOperator,
  rawValue: string,
  listValues: string[],
): Record<string, unknown> {
  const wireField = field === "HEADER" ? `HEADER:${headerName.trim()}` : field;
  let value: unknown;
  if (op === "IN") {
    value = listValues.map((v) => v.trim()).filter(Boolean);
  } else if (field === "HAS_ATTACHMENT") {
    value = rawValue === "true";
  } else if (field === "SPAM_SCORE" && op !== "MATCHES") {
    value = Number(rawValue);
  } else {
    value = rawValue;
  }
  return { field: wireField, op, value };
}

export function parseCondition(condition: Record<string, unknown>): {
  field: RoutingConditionField;
  headerName: string;
  op: RoutingOperator;
  rawValue: string;
  listValues: string[];
} {
  const wireField = String(condition.field ?? "");
  const header = wireField.startsWith("HEADER:");
  const field: RoutingConditionField = header ? "HEADER" : (wireField as RoutingConditionField);
  const headerName = header ? wireField.slice("HEADER:".length) : "";
  const op = String(condition.op ?? "EQUALS") as RoutingOperator;
  const value = condition.value;
  if (op === "IN" && Array.isArray(value)) {
    return { field, headerName, op, rawValue: "", listValues: value.map(String) };
  }
  if (typeof value === "boolean") {
    return { field, headerName, op, rawValue: value ? "true" : "false", listValues: [] };
  }
  return { field, headerName, op, rawValue: value == null ? "" : String(value), listValues: [] };
}

export function buildAction(type: RoutingActionType, rawValue: string): Record<string, unknown> {
  if (type === "MARK_SPAM") return { type };
  if (type === "APPLY_SLA") return { type, value: Number(rawValue) };
  return { type, value: rawValue };
}

export function parseAction(action: Record<string, unknown>): {
  type: RoutingActionType;
  rawValue: string;
} {
  const type = String(action.type ?? "MARK_SPAM") as RoutingActionType;
  if (type === "MARK_SPAM") return { type, rawValue: "" };
  const value = action.value;
  return { type, rawValue: value == null ? "" : String(value) };
}
