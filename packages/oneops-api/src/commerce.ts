import { z } from "zod";

/**
 * Runtime commerce schemas shared by the OneOps console and the marketing storefront.
 *
 * Types are the app-facing contract. The generated OpenAPI document (`schema.ts`) is the
 * machine-readable source; these Zod objects stay because both apps parse at the response
 * boundary, and openapi-typescript emits types only.
 *
 * Jackson `NON_NULL` omits nulls, so optional fields are `.nullish()` unless a key is always sent.
 */

export const productTypeSchema = z.enum([
  "SUBSCRIPTION",
  "DIGITAL",
  "SERVICE",
  "PHYSICAL",
]);

export const productStatusSchema = z.enum(["DRAFT", "ACTIVE", "ARCHIVED"]);

export const billingIntervalSchema = z.enum(["MONTHLY", "ANNUAL", "ONE_TIME"]);

export const discountTypeSchema = z.enum(["PERCENTAGE", "FIXED_AMOUNT"]);

export function cursorPageSchema<T extends z.ZodTypeAny>(item: T) {
  return z.object({
    items: z.array(item),
    nextCursor: z
      .string()
      .nullish()
      .transform((value) => value ?? null),
    hasMore: z.boolean().default(false),
  });
}

export const productSummarySchema = z.object({
  id: z.string().uuid(),
  slug: z.string(),
  name: z.string(),
  tagline: z.string().nullable().optional(),
  productType: productTypeSchema,
  featured: z.boolean(),
  heroImageFileId: z.string().uuid().nullable().optional(),
  heroImageUrl: z.string().nullable().optional(),
  fromPricePaise: z.number(),
  currency: z.string(),
});

export const variantViewSchema = z.object({
  id: z.string().uuid(),
  name: z.string(),
  sku: z.string(),
  pricePaise: z.number(),
  compareAtPricePaise: z.number().nullable().optional(),
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
  status: z.union([productStatusSchema, z.string()]),
  featured: z.boolean(),
  heroImageFileId: z.string().uuid().nullable().optional(),
  heroImageUrl: z.string().nullable().optional(),
  galleryFileIds: z.array(z.string().uuid()).optional(),
  galleryImageUrls: z.array(z.string()).optional(),
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

export const cartItemSchema = z.object({
  id: z.string().uuid(),
  variantId: z.string().uuid(),
  productName: z.string(),
  variantName: z.string(),
  sku: z.string(),
  quantity: z.number(),
  unitPricePaise: z.number(),
  lineTotalPaise: z.number(),
});

export const cartViewSchema = z.object({
  // Present on the commerce API; the shop BFF strips it before the browser sees the cart.
  cartToken: z.string().optional(),
  currency: z.string(),
  items: z.array(cartItemSchema),
  subtotalPaise: z.number(),
  discountPaise: z.number(),
  taxPaise: z.number(),
  shippingPaise: z.number(),
  totalPaise: z.number(),
  discountCode: z.string().nullable().optional(),
  expiresAt: z.string(),
});

export const createCartResponseSchema = z.object({
  cartToken: z.string(),
  cart: cartViewSchema,
});

export const checkoutResponseSchema = z.object({
  orderId: z.string().uuid(),
  orderNumber: z.string(),
  accessToken: z.string().optional(),
  totalPaise: z.number(),
  currency: z.string(),
  razorpayOrderId: z.string(),
  razorpayKeyId: z.string(),
  razorpayNotes: z.record(z.string()).optional(),
  subscriptionCheckout: z.boolean().optional(),
});

export const verifyPaymentResponseSchema = z.object({
  orderId: z.string().uuid(),
  orderNumber: z.string(),
  status: z.string(),
});

export const orderSummarySchema = z.object({
  id: z.string().uuid(),
  orderNumber: z.string(),
  status: z.string(),
  totalPaise: z.number(),
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
  unitPricePaise: z.number(),
  lineSubtotalPaise: z.number(),
});

export const orderDetailSchema = z.object({
  id: z.string().uuid(),
  orderNumber: z.string(),
  status: z.string(),
  accessToken: z.string().optional(),
  subtotalPaise: z.number(),
  discountPaise: z.number(),
  cgstPaise: z.number(),
  sgstPaise: z.number(),
  igstPaise: z.number(),
  shippingPaise: z.number(),
  totalPaise: z.number(),
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
  amountPaise: z.number().nullable().optional(),
  minOrderPaise: z.number(),
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
  flatShippingPaise: z.number(),
  freeShippingAbovePaise: z.number().nullable().optional(),
});

export const dashboardViewSchema = z.object({
  revenuePaise30d: z.number(),
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
  refundedPaise: z.number(),
  totalRefundedPaise: z.number(),
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

export const downloadLinkResponseSchema = z.object({
  downloadUrl: z.string(),
  expiresAt: z.string(),
});

export const productListPageSchema = cursorPageSchema(productSummarySchema);
export const orderListPageSchema = cursorPageSchema(orderSummarySchema);
export const customerListPageSchema = cursorPageSchema(customerSummarySchema);
export const discountListSchema = z.array(discountViewSchema);
export const categoryListSchema = z.array(categoryViewSchema);
export const downloadListSchema = z.array(downloadViewSchema);

export type ProductType = z.infer<typeof productTypeSchema>;
export type ProductStatus = z.infer<typeof productStatusSchema>;
export type BillingInterval = z.infer<typeof billingIntervalSchema>;
export type ProductSummary = z.infer<typeof productSummarySchema>;
export type ProductDetail = z.infer<typeof productDetailSchema>;
export type VariantView = z.infer<typeof variantViewSchema>;
export type CartView = z.infer<typeof cartViewSchema>;
export type OrderSummary = z.infer<typeof orderSummarySchema>;
export type OrderDetail = z.infer<typeof orderDetailSchema>;
export type DiscountView = z.infer<typeof discountViewSchema>;
export type CommerceSettings = z.infer<typeof settingsViewSchema>;
export type CategoryView = z.infer<typeof categoryViewSchema>;
