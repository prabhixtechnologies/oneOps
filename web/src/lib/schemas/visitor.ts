import { z } from "zod";
import { cursorPageSchema } from "./common";

export const visitorConsentSchema = z.enum(["FULL", "MINIMAL", "DELETED"]);

export const visitorSummarySchema = z.object({
  id: z.string(),
  externalKey: z.string().optional(),
  consentStatus: visitorConsentSchema.optional(),
  firstSeenAt: z.string(),
  lastSeenAt: z.string(),
  email: z.string().nullable().optional(),
  displayName: z.string().nullable().optional(),
  identified: z.boolean().optional(),
});

export const liveVisitorSchema = z.object({
  visitorId: z.string(),
  externalKey: z.string().optional(),
  currentPath: z.string().optional(),
  currentTitle: z.string().optional(),
  since: z.string(),
  email: z.string().nullable().optional(),
  displayName: z.string().nullable().optional(),
});

export const visitorSessionSchema = z.object({
  id: z.string(),
  startedAt: z.string(),
  endedAt: z.string().nullable().optional(),
  durationSeconds: z.number().nullable().optional(),
  entryUrl: z.string().nullable().optional(),
  exitUrl: z.string().nullable().optional(),
  referrer: z.string().nullable().optional(),
  deviceType: z.string().nullable().optional(),
  browser: z.string().nullable().optional(),
  os: z.string().nullable().optional(),
  geoCountry: z.string().nullable().optional(),
  geoCity: z.string().nullable().optional(),
});

export const pageViewSchema = z.object({
  id: z.string(),
  url: z.string(),
  path: z.string(),
  title: z.string().nullable().optional(),
  viewedAt: z.string(),
  durationMs: z.number().nullable().optional(),
  entry: z.boolean().optional(),
  exit: z.boolean().optional(),
});

export const visitorEventSchema = z.object({
  id: z.string(),
  name: z.string(),
  properties: z.record(z.string(), z.unknown()).optional(),
  occurredAt: z.string(),
});

export const visitorDetailSchema = z.object({
  visitor: visitorSummarySchema,
  sessions: z.array(visitorSessionSchema),
});

export const dimensionCountSchema = z.object({
  dimension: z.string(),
  count: z.number(),
});

export const timeSeriesPointSchema = z.object({
  date: z.string(),
  count: z.number(),
});

export const visitorAnalyticsSummarySchema = z.object({
  topPages: z.array(dimensionCountSchema).optional(),
  topReferrers: z.array(dimensionCountSchema).optional(),
  sessionsOverTime: z.array(timeSeriesPointSchema).optional(),
  totalVisitors: z.number().optional(),
  identifiedVisitors: z.number().optional(),
  chatConversions: z.number().optional(),
});

export type VisitorSummary = z.infer<typeof visitorSummarySchema>;
export type LiveVisitor = z.infer<typeof liveVisitorSchema>;
export type VisitorSession = z.infer<typeof visitorSessionSchema>;
export type PageView = z.infer<typeof pageViewSchema>;
export type VisitorEvent = z.infer<typeof visitorEventSchema>;
export type VisitorDetail = z.infer<typeof visitorDetailSchema>;
export type VisitorAnalyticsSummary = z.infer<typeof visitorAnalyticsSummarySchema>;

export const visitorListPageSchema = cursorPageSchema(visitorSummarySchema);
export const pageViewListPageSchema = cursorPageSchema(pageViewSchema);
export const visitorEventListPageSchema = cursorPageSchema(visitorEventSchema);
export const liveVisitorListSchema = z.array(liveVisitorSchema);
