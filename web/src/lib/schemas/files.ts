import { z } from "zod";

export const fileUploadSchema = z.object({
  id: z.string(),
  filename: z.string(),
  contentType: z.string(),
  sizeBytes: z.number(),
  scanStatus: z.enum(["PENDING", "CLEAN", "INFECTED", "SKIPPED"]),
});

export type FileUpload = z.infer<typeof fileUploadSchema>;

/** Purposes the console may assign on upload; mail-specific values live in Mailroom. */
export type FilePurpose =
  | "AVATAR"
  | "DOCUMENT"
  | "LOGO"
  | "EXPORT"
  | "IMPORT"
  | "INVOICE"
  | "CHAT_ATTACHMENT";
