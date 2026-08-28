import { z } from "zod";
import { cursorPageSchema } from "./common";

export const eventLogSchema = z.object({
  id: z.string(),
  occurredAt: z.string(),
  organizationId: z.string().nullable().optional(),
  eventCode: z.string(),
  category: z.string(),
  severity: z.string(),
  correlationId: z.string(),
  actorUserId: z.string().nullable().optional(),
  actorType: z.string(),
  actorLabel: z.string().nullable().optional(),
  targetType: z.string().nullable().optional(),
  targetId: z.string().nullable().optional(),
  payload: z.record(z.unknown()).optional(),
  ipAddress: z.string().nullable().optional(),
  userAgent: z.string().nullable().optional(),
  securityEvent: z.boolean(),
  containsPii: z.boolean(),
});

export type EventLogEntry = z.infer<typeof eventLogSchema>;
export const eventLogPageSchema = cursorPageSchema(eventLogSchema);

export const eventLogStatsSchema = z.object({
  errorsOverTime: z.array(z.object({
    bucketStart: z.string(),
    errorCount: z.number(),
  })),
  topEventCodes: z.array(z.object({
    eventCode: z.string(),
    count: z.number(),
  })),
});

export type EventLogStats = z.infer<typeof eventLogStatsSchema>;

export const traceEntrySchema = z.object({
  source: z.string(),
  timestamp: z.string(),
  actionOrCode: z.string(),
  severity: z.string(),
  actorUserId: z.string().nullable().optional(),
  actorLabel: z.string().nullable().optional(),
  targetType: z.string().nullable().optional(),
  targetId: z.string().nullable().optional(),
  details: z.record(z.unknown()).optional(),
});

export const traceViewSchema = z.object({
  correlationId: z.string(),
  entries: z.array(traceEntrySchema),
});

export type TraceView = z.infer<typeof traceViewSchema>;
