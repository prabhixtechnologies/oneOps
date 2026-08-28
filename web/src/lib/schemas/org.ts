import { z } from "zod";
import { cursorPageSchema, pageResponseSchema } from "./common";

export const memberSchema = z.object({
  id: z.string(),
  userId: z.string(),
  displayName: z.string(),
  email: z.string(),
  roleId: z.string(),
  roleName: z.string(),
  status: z.string(),
  department: z.string().nullable().optional(),
  employeeId: z.string().nullable().optional(),
  joinedAt: z.string(),
  lastActiveAt: z.string().nullable().optional(),
});

export const roleSchema = z.object({
  id: z.string(),
  roleKey: z.string().optional(),
  name: z.string(),
  description: z.string().nullable().optional(),
  system: z.boolean(),
  rank: z.number().optional(),
  permissions: z.array(z.string()),
  memberCount: z.number().optional(),
});

export const permissionCategorySchema = z.object({
  category: z.string(),
  permissions: z.array(
    z.object({
      code: z.string(),
      description: z.string(),
    }),
  ),
});

export const teamSchema = z.object({
  id: z.string(),
  slug: z.string().optional(),
  name: z.string(),
  description: z.string().nullable().optional(),
  leadUserId: z.string().nullable().optional(),
  memberCount: z.number(),
});

export const teamMemberSchema = z.object({
  userId: z.string(),
  displayName: z.string(),
  email: z.string(),
  role: z.string().optional(),
});

export const inviteSchema = z.object({
  id: z.string(),
  email: z.string(),
  roleId: z.string(),
  roleName: z.string(),
  invitedBy: z.string(),
  expiresAt: z.string(),
  createdAt: z.string(),
});

export type Member = z.infer<typeof memberSchema>;
export type Role = z.infer<typeof roleSchema>;
export type PermissionCategory = z.infer<typeof permissionCategorySchema>;
export type Team = z.infer<typeof teamSchema>;
export type TeamMember = z.infer<typeof teamMemberSchema>;
export type Invite = z.infer<typeof inviteSchema>;

export const memberListPageSchema = cursorPageSchema(memberSchema);
export const auditLogSchema = z.object({
  id: z.string(),
  action: z.string(),
  actor: z
    .object({ id: z.string(), name: z.string(), email: z.string() })
    .nullable()
    .optional(),
  actorUserId: z.string().nullable().optional(),
  actorLabel: z.string().nullable().optional(),
  resource: z.string().optional(),
  resourceType: z.string().optional(),
  resourceId: z.string().nullable().optional(),
  ipAddress: z.string().nullable().optional(),
  metadata: z.record(z.string(), z.unknown()).nullable().optional(),
  createdAt: z.string(),
});

export type AuditLogEntry = z.infer<typeof auditLogSchema>;
export const auditLogPageSchema = cursorPageSchema(auditLogSchema);

export const sessionSchema = z.object({
  id: z.string(),
  deviceName: z.string(),
  deviceType: z.string().nullable().optional(),
  ipAddress: z.string(),
  lastSeenAt: z.string(),
  createdAt: z.string(),
  current: z.boolean(),
});

export type Session = z.infer<typeof sessionSchema>;
export const sessionListSchema = z.array(sessionSchema);

export const apiKeySchema = z.object({
  id: z.string(),
  name: z.string(),
  prefix: z.string(),
  lastUsedAt: z.string().nullable(),
  createdAt: z.string(),
  expiresAt: z.string().nullable(),
});

export const createdApiKeySchema = z.object({
  id: z.string(),
  name: z.string(),
  prefix: z.string(),
  key: z.string(),
  createdAt: z.string(),
  expiresAt: z.string().nullable(),
});

export type ApiKey = z.infer<typeof apiKeySchema>;
export const apiKeyPageSchema = pageResponseSchema(apiKeySchema);
export const rolePageSchema = pageResponseSchema(roleSchema);
export const teamPageSchema = pageResponseSchema(teamSchema);
export const invitePageSchema = pageResponseSchema(inviteSchema);
export const teamMemberPageSchema = pageResponseSchema(teamMemberSchema);
