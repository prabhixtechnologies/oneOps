import { z } from "zod";
import { cursorPageSchema } from "./common";

/**
 * Schemas for the platform operator hub. Everything here comes from `/api/v1/admin/**`, which the
 * server gates on PLATFORM_ADMIN, so these shapes are only ever fetched by staff.
 */

export const platformOverviewSchema = z.object({
  tenants: z.object({
    total: z.number(),
    active: z.number(),
    trial: z.number(),
    suspended: z.number(),
    cancelled: z.number(),
    createdLast30Days: z.number(),
  }),
  accounts: z.object({
    total: z.number(),
    active: z.number(),
    invited: z.number(),
    disabled: z.number(),
    lockedOut: z.number(),
    platformAdmins: z.number(),
    createdLast30Days: z.number(),
  }),
  queues: z.object({
    mailPending: z.number(),
    mailFailed: z.number(),
    activeSessions: z.number(),
  }),
  activity: z.object({
    errorsLast24h: z.number(),
    securityEventsLast24h: z.number(),
  }),
  generatedAt: z.string(),
});

export const tenantSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string(),
  status: z.string(),
  memberCount: z.number(),
  seatLimit: z.number(),
  trialEndsAt: z.string().nullable().optional(),
  createdAt: z.string(),
});

export const leadSummarySchema = z.object({
  id: z.string(),
  name: z.string().nullable().optional(),
  email: z.string(),
  company: z.string().nullable().optional(),
  interest: z.string().nullable().optional(),
  interestRaw: z.string().nullable().optional(),
  status: z.string(),
  source: z.string().nullable().optional(),
  createdAt: z.string(),
});

export const leadDetailSchema = leadSummarySchema.extend({
  phone: z.string().nullable().optional(),
  employeeCount: z.string().nullable().optional(),
  message: z.string().nullable().optional(),
  utm: z.record(z.string(), z.unknown()).nullable().optional(),
  referrer: z.string().nullable().optional(),
  assignedTo: z.string().nullable().optional(),
  internalNotes: z.string().nullable().optional(),
  contactedAt: z.string().nullable().optional(),
});

export const subscriberSummarySchema = z.object({
  id: z.string(),
  email: z.string(),
  name: z.string().nullable().optional(),
  status: z.string(),
  source: z.string().nullable().optional(),
  confirmedAt: z.string().nullable().optional(),
  createdAt: z.string(),
});

export const applicationSummarySchema = z.object({
  id: z.string(),
  roleSlug: z.string(),
  name: z.string().nullable().optional(),
  email: z.string(),
  status: z.string(),
  createdAt: z.string(),
});

export const applicationDetailSchema = applicationSummarySchema.extend({
  phone: z.string().nullable().optional(),
  portfolioUrl: z.string().nullable().optional(),
  linkedinUrl: z.string().nullable().optional(),
  coverLetter: z.string().nullable().optional(),
  resumeFileId: z.string().nullable().optional(),
  internalNotes: z.string().nullable().optional(),
});

export const tenantPageSchema = cursorPageSchema(tenantSummarySchema);
export const leadPageSchema = cursorPageSchema(leadSummarySchema);
export const subscriberPageSchema = cursorPageSchema(subscriberSummarySchema);
export const applicationPageSchema = cursorPageSchema(applicationSummarySchema);

export type PlatformOverview = z.infer<typeof platformOverviewSchema>;
export type TenantSummary = z.infer<typeof tenantSummarySchema>;
export type LeadSummary = z.infer<typeof leadSummarySchema>;
export type LeadDetail = z.infer<typeof leadDetailSchema>;
export type SubscriberSummary = z.infer<typeof subscriberSummarySchema>;
export type ApplicationSummary = z.infer<typeof applicationSummarySchema>;
export type ApplicationDetail = z.infer<typeof applicationDetailSchema>;

export const LEAD_STATUSES = [
  "NEW",
  "CONTACTED",
  "QUALIFIED",
  "DEMO_BOOKED",
  "WON",
  "LOST",
  "SPAM",
] as const;

export const SUBSCRIBER_STATUSES = [
  "PENDING",
  "CONFIRMED",
  "UNSUBSCRIBED",
  "BOUNCED",
] as const;

export const APPLICATION_STATUSES = [
  "RECEIVED",
  "SCREENING",
  "INTERVIEWING",
  "OFFERED",
  "HIRED",
  "REJECTED",
  "WITHDRAWN",
] as const;

export const TENANT_STATUSES = [
  "ACTIVE",
  "TRIAL",
  "SUSPENDED",
  "CANCELLED",
] as const;
