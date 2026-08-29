import { z } from "zod";
import { arraySchema, cursorPageSchema } from "./common";

export const threadStatusSchema = z.enum([
  "OPEN",
  "PENDING_CUSTOMER",
  "ON_HOLD",
  "RESOLVED",
  "CLOSED",
  "SPAM",
  "TRASH",
]);

export const threadPrioritySchema = z.enum(["LOW", "NORMAL", "HIGH", "URGENT"]);

export const replyModeSchema = z.enum(["REPLY", "REPLY_ALL", "FORWARD"]);
export type ReplyMode = z.infer<typeof replyModeSchema>;

export const threadSummarySchema = z.object({
  id: z.string(),
  mailboxId: z.string(),
  referenceKey: z.string().optional(),
  subject: z.string(),
  status: threadStatusSchema,
  priority: threadPrioritySchema,
  assigneeUserId: z.string().nullable().optional(),
  assigneeTeamId: z.string().nullable().optional(),
  customerEmail: z.string().nullable().optional(),
  snippet: z.string(),
  messageCount: z.number(),
  unreadCount: z.number(),
  hasAttachments: z.boolean(),
  lastMessageAt: z.string(),
  lastMessageDirection: z.enum(["INBOUND", "OUTBOUND"]).optional(),
  slaDueAt: z.string().nullable().optional(),
  slaBreachedAt: z.string().nullable().optional(),
});

export const messageSummarySchema = z.object({
  id: z.string(),
  direction: z.enum(["INBOUND", "OUTBOUND"]),
  fromAddress: z.string(),
  fromName: z.string().nullable().optional(),
  subject: z.string(),
  snippet: z.string().optional(),
  bodyText: z.string().nullable().optional(),
  bodyHtml: z.string().nullable().optional(),
  deliveryStatus: z.string().optional(),
  occurredAt: z.string(),
  attachmentCount: z.number(),
});

export const noteSummarySchema = z.object({
  id: z.string(),
  authorUserId: z.string(),
  bodyHtml: z.string(),
  createdAt: z.string(),
});

export const eventSummarySchema = z.object({
  eventType: z.string(),
  actorUserId: z.string().nullable().optional(),
  actorLabel: z.string().nullable().optional(),
  fromValue: z.string().nullable().optional(),
  toValue: z.string().nullable().optional(),
  createdAt: z.string(),
});

export const threadDetailResponseSchema = z.object({
  thread: threadSummarySchema,
  messages: z.array(messageSummarySchema),
  notes: z.array(noteSummarySchema),
  events: z.array(eventSummarySchema),
});

export const mailboxSchema = z.object({
  id: z.string(),
  address: z.string(),
  name: z.string(),
  kind: z.enum(["SHARED", "PERSONAL", "SYSTEM"]).optional(),
  status: z.enum(["ACTIVE", "PAUSED", "ARCHIVED"]).optional(),
  openThreadCount: z.number(),
  unassignedCount: z.number().optional(),
});

export const mailboxMemberSchema = z.object({
  userId: z.string(),
  name: z.string(),
  email: z.string(),
});

export const routingConditionSchema = z.object({
  field: z.string(),
  op: z.string(),
  value: z.unknown(),
});

export const routingActionSchema = z.object({
  type: z.string(),
  value: z.string(),
});

export const routingRuleSchema = z.object({
  id: z.string(),
  name: z.string(),
  priority: z.number(),
  conditions: z.array(routingConditionSchema),
  match: z.enum(["ALL", "ANY"]).optional(),
  actions: z.array(routingActionSchema),
  continue: z.boolean().optional(),
  enabled: z.boolean(),
});

export const businessHoursSchema = z.object({
  timezone: z.string(),
  workingDays: z.array(z.number()),
  startTime: z.string(),
  endTime: z.string(),
  holidays: z.array(z.string()),
});

export const mailboxDetailSchema = z.object({
  id: z.string(),
  name: z.string(),
  email: z.string().optional(),
  address: z.string().optional(),
  description: z.string().nullable().optional(),
  memberCount: z.number().optional(),
  openThreadCount: z.number(),
  slaPolicyId: z.string().nullable().optional(),
  signature: z.string().nullable().optional(),
  createdAt: z.string().optional(),
  members: z.array(mailboxMemberSchema).optional(),
  routingRules: z.array(routingRuleSchema).optional(),
  businessHours: businessHoursSchema.nullable().optional(),
});

export const mailDomainSchema = z.object({
  id: z.string(),
  domain: z.string(),
  status: z.enum(["PENDING", "VERIFYING", "VERIFIED", "FAILED", "DISABLED"]).optional(),
  mode: z.enum(["SELF_HOSTED", "EXTERNAL_IMAP", "RELAY_ONLY"]).optional(),
  isDefault: z.boolean().optional(),
  mxVerifiedAt: z.string().nullable().optional(),
  spfVerifiedAt: z.string().nullable().optional(),
  dkimVerifiedAt: z.string().nullable().optional(),
  dmarcVerifiedAt: z.string().nullable().optional(),
  ownershipVerifiedAt: z.string().nullable().optional(),
  verified: z.boolean().optional(),
  createdAt: z.string().optional(),
});

export const dnsRecordSchema = z.object({
  type: z.string(),
  host: z.string(),
  value: z.string(),
  priority: z.number().nullable().optional(),
  verified: z.boolean().optional(),
  status: z.enum(["VERIFIED", "PENDING", "FAILED"]).optional(),
});

export const dnsRecordsResponseSchema = z.object({
  domain: z.string(),
  records: z.array(dnsRecordSchema),
});

export const templateListItemSchema = z.object({
  id: z.string().optional(),
  templateKey: z.string().optional(),
  key: z.string().optional(),
  name: z.string(),
  locale: z.string(),
  subject: z.string().optional(),
  category: z.string().optional(),
  enabled: z.boolean().optional(),
});

export const templateDetailSchema = z.object({
  key: z.string(),
  name: z.string(),
  locale: z.string(),
  subject: z.string(),
  htmlBody: z.string(),
  textBody: z.string(),
  variables: z.array(z.object({ name: z.string(), description: z.string().optional() })).optional(),
  updatedAt: z.string(),
});

export const templatePreviewSchema = z.object({
  subject: z.string(),
  htmlBody: z.string(),
  textBody: z.string(),
});

export const cannedReplySchema = z.object({
  id: z.string(),
  mailboxId: z.string().nullable().optional(),
  shortcut: z.string().nullable().optional(),
  title: z.string(),
  subject: z.string().nullable().optional(),
  bodyHtml: z.string(),
  usageCount: z.number().optional(),
});

export const mailTagSchema = z.object({
  id: z.string(),
  slug: z.string(),
  name: z.string(),
  colour: z.string(),
  usageCount: z.number(),
});

export const fileUploadSchema = z.object({
  id: z.string(),
  filename: z.string(),
  contentType: z.string(),
  sizeBytes: z.number(),
  scanStatus: z.enum(["PENDING", "CLEAN", "INFECTED", "SKIPPED"]),
});

// GET /flags returns FlagDetail entries, not a key/boolean map. The map form exists on the server as
// a separate DTO that this endpoint does not use, so the record shape here never matched and the
// Feature flags page could not render at all.
export const flagDetailSchema = z.object({
  key: z.string(),
  enabled: z.boolean(),
  // "DEFAULT" or "OVERRIDE" today; left as a string so a new source does not break the page.
  source: z.string(),
  description: z.string().nullish(),
});

export const effectiveFlagsSchema = z.object({
  flags: z.array(flagDetailSchema),
});

export const presenceEventSchema = z.object({
  type: z.enum(["VIEWING", "TYPING", "LEFT"]),
  threadId: z.string(),
  userId: z.string(),
  userName: z.string(),
});

export const mailStreamEventSchema = z.discriminatedUnion("event", [
  z.object({ event: z.literal("thread.updated"), threadId: z.string() }),
  z.object({ event: z.literal("thread.new"), threadId: z.string() }),
  z.object({ event: z.literal("presence"), data: presenceEventSchema }),
  z.object({
    event: z.literal("assignment"),
    threadId: z.string(),
    assigneeId: z.string().nullish(),
  }),
]);

export type ThreadSummary = z.infer<typeof threadSummarySchema>;
export type ThreadDetailResponse = z.infer<typeof threadDetailResponseSchema>;
export type MessageSummary = z.infer<typeof messageSummarySchema>;
export type NoteSummary = z.infer<typeof noteSummarySchema>;
export type EventSummary = z.infer<typeof eventSummarySchema>;
export type Mailbox = z.infer<typeof mailboxSchema>;
export type MailboxDetail = z.infer<typeof mailboxDetailSchema>;
export type RoutingRule = z.infer<typeof routingRuleSchema>;
export type MailDomain = z.infer<typeof mailDomainSchema>;
export type DnsRecord = z.infer<typeof dnsRecordSchema>;
export type MailTemplateListItem = z.infer<typeof templateListItemSchema>;
export type MailTemplateDetail = z.infer<typeof templateDetailSchema>;
export type CannedReply = z.infer<typeof cannedReplySchema>;
export type MailTag = z.infer<typeof mailTagSchema>;
export type FileUpload = z.infer<typeof fileUploadSchema>;
export type MailStreamEvent = z.infer<typeof mailStreamEventSchema>;

export const threadListPageSchema = cursorPageSchema(threadSummarySchema);
export const mailboxListSchema = arraySchema(mailboxSchema);
export const domainListSchema = arraySchema(mailDomainSchema);
export const templateListSchema = arraySchema(templateListItemSchema);
export const cannedReplyListSchema = arraySchema(cannedReplySchema);
export const mailTagListSchema = arraySchema(mailTagSchema);

export function templateKey(item: MailTemplateListItem): string {
  return item.templateKey ?? item.key ?? "";
}

export function mailboxAddress(mb: Mailbox | MailboxDetail): string {
  return "address" in mb && mb.address ? mb.address : "email" in mb && mb.email ? mb.email : "";
}

export function isThreadUnread(thread: ThreadSummary): boolean {
  return thread.unreadCount > 0;
}

export function isSlaBreached(thread: ThreadSummary): boolean {
  return !!thread.slaBreachedAt;
}

export function eventDetail(evt: EventSummary): string {
  const parts = [evt.actorLabel, evt.fromValue, evt.toValue].filter(Boolean);
  if (parts.length > 0) return parts.join(" → ");
  return evt.eventType.replace(/_/g, " ").toLowerCase();
}
