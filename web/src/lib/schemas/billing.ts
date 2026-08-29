import { z } from "zod";
import { cursorPageSchema, pageResponseSchema } from "./common";

export const planSchema = z.object({
  id: z.string(),
  planKey: z.string().optional(),
  name: z.string(),
  description: z.string().nullable().optional(),
  intervalType: z.enum(["MONTHLY", "ANNUAL", "ONE_TIME", "CUSTOM"]).optional(),
  amountPaise: z.number(),
  perSeatPaise: z.number().optional(),
  includedSeats: z.number(),
  maxSeats: z.number().optional(),
  trialDays: z.number().optional(),
  entitlements: z.record(z.string(), z.unknown()).optional(),
});

export const subscriptionSchema = z.object({
  id: z.string().optional(),
  planId: z.string(),
  planName: z.string(),
  status: z.enum(["TRIALING", "ACTIVE", "PAST_DUE", "PAUSED", "CANCELLED", "EXPIRED"]),
  seats: z.number(),
  currentPeriodStart: z.string().optional(),
  currentPeriodEnd: z.string(),
  trialEndsAt: z.string().nullish(),
  cancelAtPeriodEnd: z.boolean().optional(),
  nextBillingAt: z.string().nullable().optional(),
  lockedAmountPaise: z.number().optional(),
  lockedPerSeatPaise: z.number().optional(),
  pendingPlanId: z.string().nullable().optional(),
  pendingSeats: z.number().nullable().optional(),
  pendingChangeAt: z.string().nullable().optional(),
});

export const entitlementsSchema = z.record(z.string(), z.unknown());

export const orderSchema = z.object({
  orderId: z.string(),
  amount: z.number().optional(),
  amountPaise: z.number().optional(),
  currency: z.string(),
  keyId: z.string(),
  planId: z.string(),
  planName: z.string().optional(),
});

export const invoiceSchema = z.object({
  id: z.string(),
  invoiceNumber: z.string(),
  status: z.enum(["DRAFT", "ISSUED", "PAID", "VOID", "REFUNDED"]),
  issueDate: z.string(),
  totalPaise: z.number(),
  currency: z.string(),
  paidAt: z.string().nullable().optional(),
});

export const paymentMethodSchema = z.object({
  method: z.string(),
  label: z.string(),
  lastUsedAt: z.string().nullable().optional(),
  vaulted: z.boolean(),
});

// Every field is optional because an org that has not entered a billing address yet gets `{}` back
// rather than a 404, and requiring line1/city/state/pincode/country made the Billing page fail
// outright for exactly the accounts that still need to fill the form in.
export const billingAddressSchema = z.object({
  line1: z.string().nullish(),
  line2: z.string().nullish(),
  city: z.string().nullish(),
  state: z.string().nullish(),
  pincode: z.string().nullish(),
  country: z.string().nullish(),
  gstin: z.string().nullish(),
  billingEmail: z.string().nullish(),
});

// Writing an address is a different contract from reading one: the server rejects a PUT that leaves
// out the postal fields, so the mutation input keeps them required even though the response cannot.
export const billingAddressInputSchema = z.object({
  line1: z.string(),
  line2: z.string().optional(),
  city: z.string(),
  state: z.string(),
  pincode: z.string(),
  country: z.string(),
  gstin: z.string().optional(),
  billingEmail: z.string().optional(),
});

export type Plan = z.infer<typeof planSchema>;
export type Subscription = z.infer<typeof subscriptionSchema>;
export type Entitlements = z.infer<typeof entitlementsSchema>;
export type Order = z.infer<typeof orderSchema>;
export type Invoice = z.infer<typeof invoiceSchema>;
export type PaymentMethod = z.infer<typeof paymentMethodSchema>;
export type BillingAddress = z.infer<typeof billingAddressSchema>;

export const invoiceListPageSchema = cursorPageSchema(invoiceSchema);
export const planPageSchema = pageResponseSchema(planSchema);
export const paymentMethodListSchema = z.array(paymentMethodSchema);

export const dashboardKpisSchema = z.object({
  openThreads: z.number(),
  avgFirstResponseMinutes: z.number(),
  slaBreaches: z.number(),
  seatsUsed: z.number(),
  seatsLimit: z.number(),
  mrr: z.number(),
  currency: z.string(),
});

export const dashboardActivitySchema = z.object({
  id: z.string(),
  type: z.string(),
  description: z.string(),
  // System-generated activity has no actor. The API omits null fields rather than sending them, so
  // nullable() alone rejected the whole response and the dashboard rendered as an error page — but
  // only once the org had activity to show, which is why it looked fine when the account was new.
  actor: z.string().nullish(),
  createdAt: z.string(),
});

export const dashboardChartPointSchema = z.object({
  date: z.string(),
  value: z.number(),
});

export const dashboardSchema = z.object({
  kpis: dashboardKpisSchema,
  recentActivity: z.array(dashboardActivitySchema),
  threadsTrend: z.array(dashboardChartPointSchema),
  responseTimeTrend: z.array(dashboardChartPointSchema),
});

export type Dashboard = z.infer<typeof dashboardSchema>;

export function formatPaise(paise: number): number {
  return paise / 100;
}
