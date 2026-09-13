import { z } from "zod";

export const revenueSnapshotSchema = z.object({
  capturedTotal: z.coerce.number(),
  pendingTotal: z.coerce.number(),
  capturedCount: z.coerce.number(),
  pendingCount: z.coerce.number(),
  failedCount: z.coerce.number(),
  currency: z.string().optional(),
  asOf: z.string().optional(),
});

export type RevenueSnapshot = z.infer<typeof revenueSnapshotSchema>;

export const mobiPaymentSchema = z.object({
  id: z.string(),
  shopId: z.string().optional(),
  shopName: z.string().optional(),
  priceCode: z.string().optional(),
  amount: z.coerce.number(),
  currency: z.string().optional(),
  status: z.string(),
  gateway: z.string().optional(),
  createdAt: z.string().optional(),
  paidAt: z.string().nullable().optional(),
});

export const mobiWorkspaceSchema = z.object({
  id: z.string(),
  name: z.string(),
  city: z.string().nullable().optional(),
  active: z.boolean(),
  members: z.coerce.number().optional(),
  extraScreens: z.coerce.number().optional(),
});

export const awsSummarySchema = z.object({
  ok: z.boolean().optional(),
  costs: z
    .object({
      mtdUsd: z.coerce.number().optional(),
      totalUsd: z.coerce.number().optional(),
      ok: z.boolean().optional(),
    })
    .optional(),
  instances: z
    .object({
      running: z.coerce.number().optional(),
      total: z.coerce.number().optional(),
      ok: z.boolean().optional(),
    })
    .optional(),
});

export const ec2ListSchema = z.object({
  ok: z.boolean().optional(),
  instances: z.array(
    z.object({
      instanceId: z.string(),
      name: z.string().nullable().optional(),
      type: z.string().nullable().optional(),
      state: z.string(),
      az: z.string().nullable().optional(),
      privateIp: z.string().nullable().optional(),
      cpuAverage1h: z.coerce.number().nullable().optional(),
    }),
  ),
});

export const productHealthSchema = z.object({
  products: z.array(
    z.object({
      name: z.string(),
      ok: z.boolean(),
      statusCode: z.number().nullable().optional(),
      latencyMs: z.number().nullable().optional(),
      error: z.string().nullable().optional(),
    }),
  ),
});

export const githubChecksSchema = z.object({
  checks: z
    .array(
      z.object({
        repo: z.string(),
        name: z.string().nullable().optional(),
        status: z.string().nullable().optional(),
        conclusion: z.string().nullable().optional(),
        htmlUrl: z.string().nullable().optional(),
      }),
    )
    .optional()
    .default([]),
  note: z.string().optional(),
});

export const pnlSchema = z.object({
  ok: z.boolean().optional(),
  revenue: z.object({
    mobiCaptured: z.coerce.number(),
    oneopsCaptured: z.coerce.number(),
    total: z.coerce.number(),
  }),
  awsMtd: z.coerce.number().nullable().optional(),
  contribution: z.coerce.number().optional(),
  note: z.string().optional(),
});
