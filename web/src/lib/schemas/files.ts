import { z } from "zod";
import { cursorPageSchema } from "./common";

export const fileUploadSchema = z.object({
  id: z.string(),
  filename: z.string(),
  contentType: z.string(),
  sizeBytes: z.number(),
  scanStatus: z.enum(["PENDING", "CLEAN", "INFECTED", "SKIPPED"]),
});

export type FileUpload = z.infer<typeof fileUploadSchema>;

export const fileSummarySchema = z.object({
  id: z.string(),
  filename: z.string(),
  contentType: z.string(),
  sizeBytes: z.number(),
  purpose: z.string(),
  scanStatus: z.enum(["PENDING", "CLEAN", "INFECTED", "SKIPPED"]),
  createdAt: z.string(),
  uploadedBy: z.string().nullish(),
});

export type FileSummary = z.infer<typeof fileSummarySchema>;

export const fileListPageSchema = cursorPageSchema(fileSummarySchema);

/** Purposes the console may assign on upload; mail-specific values live on the mail module. */
export type FilePurpose =
  | "AVATAR"
  | "DOCUMENT"
  | "LOGO"
  | "EXPORT"
  | "IMPORT"
  | "INVOICE"
  | "CHAT_ATTACHMENT";
