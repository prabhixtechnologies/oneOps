import { z } from "zod";
import { cursorPageSchema } from "./common";

export const productTypeSchema = z.enum([
  "SUBSCRIPTION",
  "DIGITAL",
  "SERVICE",
  "PHYSICAL",
]);

export const productStatusSchema = z.enum(["DRAFT", "ACTIVE", "ARCHIVED"]);

export const billingIntervalSchema = z.enum(["MONTHLY", "ANNUAL", "ONE_TIME"]);

export const discountTypeSchema = z.enum(["PERCENTAGE", "FIXED_AMOUNT"]);

export const productSummarySchema = z.object({
  id: z.string().uuid(),
  slug: z.string(),
  name: z.string(),
  tagline: z.string().nullable().optional(),
  productType: productTypeSchema,
  featured: z.boolean(),
  heroImageFileId: z.string().uuid().nullable().optional(),
  fromPriceMinor: z.number(),
  currency: z.string(),
});

export const variantViewSchema = z.object({
  id: z.string().uuid(),
  name: z.string(),
  sku: z.string(),
  priceMinor: z.number(),
  compareAtPriceMinor: z.number().nullable().optional(),
  currency: z.string(),
  trackInventory: z.boolean(),
  stockAvailable: z.number().nullable().optional(),
  billingInterval: billingIntervalSchema.nullable().optional(),
  downloadFileId: z.string().uuid().nullable().optional(),
  serviceDurationDays: z.number().nullable().optional(),
  deliverySlaDays: z.number().nullable().optional(),
  active: z.boolean(),
});

export const productDetailSchema = z.object({
  id: z.string().uuid(),
  slug: z.string(),
  name: z.string(),
  tagline: z.string().nullable().optional(),
  description: z.string().nullable().optional(),
  productType: productTypeSchema,
  status: productStatusSchema,
  featured: z.boolean(),
  heroImageFileId: z.string().uuid().nullable().optional(),
  galleryFileIds: z.array(z.string().uuid()).optional(),
  seoTitle: z.string().nullable().optional(),
  seoDescription: z.string().nullable().optional(),
  hsnCode: z.string().nullable().optional(),
  attributes: z.record(z.unknown()).nullable().optional(),
  variants: z.array(variantViewSchema),
  categoryIds: z.array(z.string().uuid()).optional(),
  publishedAt: z.string().nullable().optional(),
});

export const categoryViewSchema = z.object({
  id: z.string().uuid(),
  slug: z.string(),
  name: z.string(),
  description: z.string().nullable().optional(),
  sortOrder: z.number(),
});

export const orderSummarySchema = z.object({
  id: z.string().uuid(),
  orderNumber: z.string(),
  status: z.string(),
  totalMinor: z.number(),
  currency: z.string(),
  customerEmail: z.string().nullable().optional(),
  createdAt: z.string(),
  paidAt: z.string().nullable().optional(),
});

export const orderItemSchema = z.object({
  id: z.string().uuid(),
  productName: z.string(),
  variantName: z.string(),
  sku: z.string(),
  productType: productTypeSchema,
  quantity: z.number(),
  unitPriceMinor: z.number(),
  lineSubtotalMinor: z.number(),
});

export const orderDetailSchema = z.object({
  id: z.string().uuid(),
  orderNumber: z.string(),
  status: z.string(),
  accessToken: z.string(),
  subtotalMinor: z.number(),
  discountMinor: z.number(),
  cgstMinor: z.number(),
  sgstMinor: z.number(),
  igstMinor: z.number(),
  shippingMinor: z.number(),
  totalMinor: z.number(),
  currency: z.string(),
  customerEmail: z.string().nullable().optional(),
  customerName: z.string().nullable().optional(),
  items: z.array(orderItemSchema),
  addresses: z.array(
    z.object({
      addressType: z.string(),
      name: z.string(),
      line1: z.string(),
      line2: z.string().nullable().optional(),
      city: z.string(),
      state: z.string(),
      pincode: z.string(),
      phone: z.string().nullable().optional(),
    }),
  ),
  events: z.array(
    z.object({
      eventType: z.string(),
      message: z.string(),
      createdAt: z.string(),
    }),
  ),
  invoiceId: z.string().uuid().nullable().optional(),
  paidAt: z.string().nullable().optional(),
  fulfilledAt: z.string().nullable().optional(),
  internalNote: z.string().nullable().optional(),
});

export const customerSummarySchema = z.object({
  id: z.string().uuid(),
  email: z.string(),
  name: z.string().nullable().optional(),
  phone: z.string().nullable().optional(),
  marketingConsent: z.boolean(),
  createdAt: z.string(),
});

export const customerDetailSchema = customerSummarySchema.extend({
  visitorId: z.string().uuid().nullable().optional(),
});

export const discountViewSchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  description: z.string().nullable().optional(),
  discountType: discountTypeSchema,
  percentage: z.number().nullable().optional(),
  amountMinor: z.number().nullable().optional(),
  minOrderMinor: z.number(),
  maxUsesTotal: z.number().nullable().optional(),
  maxUsesPerCustomer: z.number().nullable().optional(),
  usesCount: z.number(),
  validFrom: z.string().nullable().optional(),
  validUntil: z.string().nullable().optional(),
  productIds: z.array(z.string().uuid()).optional(),
  categoryIds: z.array(z.string().uuid()).optional(),
  active: z.boolean(),
});

export const settingsViewSchema = z.object({
  sellerState: z.string().nullable().optional(),
  sellerName: z.string().nullable().optional(),
  sellerGstin: z.string().nullable().optional(),
  sellerAddress: z.string().nullable().optional(),
  orderNumberPrefix: z.string().nullable().optional(),
  gstPercent: z.number(),
  flatShippingMinor: z.number(),
  freeShippingAboveMinor: z.number().nullable().optional(),
});

export const dashboardViewSchema = z.object({
  revenueMinor30d: z.number(),
  orderCount30d: z.number(),
  topProducts: z.array(
    z.object({
      productId: z.string().uuid(),
      productName: z.string(),
      quantitySold: z.number(),
    }),
  ),
  conversionRate: z.number(),
});

export const refundViewSchema = z.object({
  paymentId: z.string().uuid(),
  refundedMinor: z.number(),
  totalRefundedMinor: z.number(),
  status: z.string(),
});

export const downloadViewSchema = z.object({
  orderItemId: z.string().uuid(),
  productName: z.string(),
  downloadCount: z.number(),
  maxDownloadCount: z.number(),
  linkExpiresAt: z.string(),
  downloadUrl: z.string().nullable().optional(),
});

export const productListPageSchema = cursorPageSchema(productSummarySchema);
export const orderListPageSchema = cursorPageSchema(orderSummarySchema);
export const customerListPageSchema = cursorPageSchema(customerSummarySchema);
export const discountListSchema = z.array(discountViewSchema);

export type ProductSummary = z.infer<typeof productSummarySchema>;
export type ProductDetail = z.infer<typeof productDetailSchema>;
export type OrderDetail = z.infer<typeof orderDetailSchema>;
export type DiscountView = z.infer<typeof discountViewSchema>;
export type CommerceSettings = z.infer<typeof settingsViewSchema>;
