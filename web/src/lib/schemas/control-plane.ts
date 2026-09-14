import { z } from "zod";

export const productRevenueSchema = z.object({
  product: z.string(),
  capturedTotal: z.coerce.number().optional().default(0),
  pendingTotal: z.coerce.number().optional().default(0),
  capturedCount: z.coerce.number().optional().default(0),
  mrr: z.coerce.number().optional().default(0),
  activeSubscriptions: z.coerce.number().optional().default(0),
  churnRate: z.coerce.number().optional().default(0),
  cancelledLast30Days: z.coerce.number().optional().default(0),
  note: z.string().nullable().optional(),
});

export const revenueSnapshotSchema = z.object({
  capturedTotal: z.coerce.number(),
  pendingTotal: z.coerce.number(),
  capturedCount: z.coerce.number(),
  pendingCount: z.coerce.number(),
  failedCount: z.coerce.number(),
  currency: z.string().optional(),
  asOf: z.string().optional(),
  mrr: z.coerce.number().optional(),
  churnRate: z.coerce.number().optional(),
  activeSubscriptions: z.coerce.number().optional(),
  cancelledLast30Days: z.coerce.number().optional(),
  products: z.array(productRevenueSchema).optional(),
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

export const rdsListSchema = z.object({
  ok: z.boolean().optional(),
  region: z.string().optional(),
  error: z.record(z.string(), z.string()).nullable().optional(),
  instances: z
    .array(
      z.object({
        id: z.string().nullable().optional(),
        engine: z.string().nullable().optional(),
        status: z.string().nullable().optional(),
        clazz: z.string().nullable().optional(),
        endpoint: z.string().nullable().optional(),
      }),
    )
    .optional()
    .default([]),
});

export const cacheListSchema = z.object({
  ok: z.boolean().optional(),
  region: z.string().optional(),
  error: z.record(z.string(), z.string()).nullable().optional(),
  clusters: z
    .array(
      z.object({
        id: z.string().nullable().optional(),
        engine: z.string().nullable().optional(),
        status: z.string().nullable().optional(),
        nodeType: z.string().nullable().optional(),
      }),
    )
    .optional()
    .default([]),
});

export const ecrListSchema = z.object({
  ok: z.boolean().optional(),
  region: z.string().optional(),
  error: z.record(z.string(), z.string()).nullable().optional(),
  images: z
    .array(
      z.object({
        repository: z.string(),
        tag: z.string().nullable().optional(),
        pushedAt: z.string().nullable().optional(),
        digest: z.string().nullable().optional(),
      }),
    )
    .optional()
    .default([]),
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

export const githubPullsSchema = z.object({
  ok: z.boolean().optional(),
  note: z.string().nullable().optional(),
  pulls: z
    .array(
      z.object({
        repo: z.string(),
        number: z.coerce.number().nullable().optional(),
        title: z.string().nullable().optional(),
        user: z.string().nullable().optional(),
        htmlUrl: z.string().nullable().optional(),
        state: z.string().nullable().optional(),
      }),
    )
    .optional()
    .default([]),
});

export const githubDeploysSchema = z.object({
  ok: z.boolean().optional(),
  note: z.string().nullable().optional(),
  runs: z
    .array(
      z.object({
        id: z.coerce.number().nullable().optional(),
        name: z.string().nullable().optional(),
        status: z.string().nullable().optional(),
        conclusion: z.string().nullable().optional(),
        htmlUrl: z.string().nullable().optional(),
        headSha: z.string().nullable().optional(),
        createdAt: z.string().nullable().optional(),
      }),
    )
    .optional()
    .default([]),
});

export const promoteResponseSchema = z.object({
  ok: z.boolean(),
  message: z.string().nullable().optional(),
  htmlUrl: z.string().nullable().optional(),
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

export const mailHealthSchema = z.object({
  ses: z
    .object({
      ok: z.boolean().nullable().optional(),
      note: z.string().nullable().optional(),
      max24HourSend: z.coerce.number().nullable().optional(),
      maxSendRate: z.coerce.number().nullable().optional(),
      sentLast24Hours: z.coerce.number().nullable().optional(),
      productionAccess: z.boolean().nullable().optional(),
    })
    .optional(),
  outbox: z
    .object({
      inFlight: z.coerce.number().optional().default(0),
      failed: z.coerce.number().optional().default(0),
    })
    .optional(),
  domains: z
    .object({
      total: z.coerce.number().optional().default(0),
      verified: z.coerce.number().optional().default(0),
      pending: z.coerce.number().optional().default(0),
    })
    .optional(),
  mailboxes: z.coerce.number().optional().default(0),
  suppressions: z
    .object({
      bounces: z.coerce.number().optional().default(0),
      complaints: z.coerce.number().optional().default(0),
    })
    .optional(),
  asOf: z.string().optional(),
});

export const staffMeSchema = z.object({
  roles: z.array(z.string()).optional().default([]),
});

export const staffGrantSchema = z.object({
  id: z.string(),
  userId: z.string(),
  role: z.string(),
  grantedAt: z.string().nullable().optional(),
  grantedBy: z.string().nullable().optional(),
  note: z.string().nullable().optional(),
});

export const identityPageSchema = z.object({
  items: z.array(z.record(z.string(), z.unknown())).optional().default([]),
  nextCursor: z.string().nullable().optional(),
  total: z.coerce.number().optional(),
});

export const identityUserDetailSchema = z.record(z.string(), z.unknown());

/** Proxied Identity / MobiStack lists: a JSON array, or a Spring page. */
export const jsonRecordsSchema = z.preprocess((raw) => {
  if (Array.isArray(raw)) return raw;
  if (raw && typeof raw === "object") {
    const obj = raw as Record<string, unknown>;
    if (Array.isArray(obj.items)) return obj.items;
    if (Array.isArray(obj.content)) return obj.content;
  }
  return [];
}, z.array(z.record(z.string(), z.unknown())));

export const jsonUnknownSchema = { parse: (data: unknown) => data };

export const STAFF_ROLES = ["SUPPORT", "BILLING", "OPERATOR", "SECURITY", "OWNER"] as const;

export const PROMOTE_SERVICES = [
  { value: "backend", label: "oneOps backend" },
  { value: "web", label: "oneOps web" },
  { value: "admin", label: "Admin web" },
  { value: "marketing", label: "Marketing" },
  { value: "identity", label: "Identity" },
  { value: "mailroom", label: "Mailroom" },
  { value: "mobistack", label: "MobiStack backend" },
  { value: "mobistack-web", label: "MobiStack web" },
  { value: "app-store", label: "App store" },
] as const;
