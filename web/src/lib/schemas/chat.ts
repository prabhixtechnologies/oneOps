import { z } from "zod";
import { arraySchema, cursorPageSchema } from "./common";

export const conversationStatusSchema = z.enum(["OPEN", "PENDING", "RESOLVED", "CLOSED"]);
export const conversationPrioritySchema = z.enum(["LOW", "NORMAL", "HIGH", "URGENT"]);
export const chatAvailabilitySchema = z.enum(["ONLINE", "AWAY", "OFFLINE"]);
export const messageSenderTypeSchema = z.enum(["VISITOR", "AGENT", "SYSTEM", "NOTE"]);

export const conversationSummarySchema = z.object({
  id: z.string(),
  status: conversationStatusSchema,
  priority: conversationPrioritySchema,
  // A visitor-initiated chat has no subject, and a conversation with no messages yet has no
  // lastMessageAt. Both are the normal case, and requiring them broke the whole inbox list.
  subject: z.string().nullish(),
  visitorName: z.string().optional(),
  visitorEmail: z.string().optional(),
  assignedAgentId: z.string().nullable().optional(),
  tags: z.array(z.string()).optional(),
  unreadAgentCount: z.number().optional(),
  lastMessageAt: z.string().nullish(),
  lastMessagePreview: z.string().optional(),
  visitorId: z.string().nullable().optional(),
});

export const chatMessageSchema = z.object({
  id: z.string(),
  senderType: messageSenderTypeSchema,
  senderUserId: z.string().nullable().optional(),
  body: z.string(),
  fileId: z.string().nullable().optional(),
  occurredAt: z.string(),
});

export const conversationDetailSchema = z.object({
  conversation: conversationSummarySchema,
  messages: z.array(chatMessageSchema),
});

export const chatInboxCountsSchema = z.object({
  unassigned: z.number(),
  mineUnread: z.number(),
});

export const chatCannedReplySchema = z.object({
  id: z.string(),
  shortcut: z.string().optional(),
  title: z.string(),
  body: z.string(),
});

export const chatSettingsSchema = z.object({
  availability: chatAvailabilitySchema.optional(),
  awayMessage: z.string().optional(),
  businessHours: z.record(z.string(), z.unknown()).optional(),
  preChatEnabled: z.boolean().optional(),
  offlineMailboxId: z.string().nullable().optional(),
});

export const chatStreamEventSchema = z
  .object({
    event: z.string(),
    conversationId: z.string().optional(),
    messageId: z.string().optional(),
  })
  .passthrough();

export type ConversationSummary = z.infer<typeof conversationSummarySchema>;
export type ChatMessage = z.infer<typeof chatMessageSchema>;
export type ConversationDetail = z.infer<typeof conversationDetailSchema>;
export type ChatInboxCounts = z.infer<typeof chatInboxCountsSchema>;
export type ChatCannedReply = z.infer<typeof chatCannedReplySchema>;
export type ChatSettings = z.infer<typeof chatSettingsSchema>;
export type ChatStreamEvent = z.infer<typeof chatStreamEventSchema>;

export const conversationListPageSchema = cursorPageSchema(conversationSummarySchema);
export const chatMessageListPageSchema = cursorPageSchema(chatMessageSchema);
export const chatCannedReplyListSchema = arraySchema(chatCannedReplySchema);

export function isConversationUnread(conversation: ConversationSummary): boolean {
  return (conversation.unreadAgentCount ?? 0) > 0;
}

export function isInternalNote(message: ChatMessage): boolean {
  return message.senderType === "NOTE";
}
