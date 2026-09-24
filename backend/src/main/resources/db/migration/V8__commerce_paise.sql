-- Commerce already stored integer paise. The columns were named *_minor, which is the same
-- unit billing calls *_paise, so the two modules read as two currencies. This rename changes
-- the name only. A value of 118000 stays 118000 and still means 1,180.00 rupees.
--
-- Constraints and indexes follow the column. Postgres rewrites them as part of RENAME COLUMN.

ALTER TABLE public.commerce_cart_items RENAME COLUMN unit_price_minor TO unit_price_paise;
ALTER TABLE public.commerce_cart_items RENAME COLUMN line_total_minor TO line_total_paise;

ALTER TABLE public.commerce_carts RENAME COLUMN subtotal_minor TO subtotal_paise;
ALTER TABLE public.commerce_carts RENAME COLUMN discount_minor TO discount_paise;
ALTER TABLE public.commerce_carts RENAME COLUMN tax_minor TO tax_paise;
ALTER TABLE public.commerce_carts RENAME COLUMN shipping_minor TO shipping_paise;
ALTER TABLE public.commerce_carts RENAME COLUMN total_minor TO total_paise;

ALTER TABLE public.commerce_discount_codes RENAME COLUMN amount_minor TO amount_paise;
ALTER TABLE public.commerce_discount_codes RENAME COLUMN min_order_minor TO min_order_paise;

ALTER TABLE public.commerce_discount_redemptions RENAME COLUMN amount_minor TO amount_paise;

ALTER TABLE public.commerce_invoices RENAME COLUMN subtotal_minor TO subtotal_paise;
ALTER TABLE public.commerce_invoices RENAME COLUMN discount_minor TO discount_paise;
ALTER TABLE public.commerce_invoices RENAME COLUMN cgst_minor TO cgst_paise;
ALTER TABLE public.commerce_invoices RENAME COLUMN sgst_minor TO sgst_paise;
ALTER TABLE public.commerce_invoices RENAME COLUMN igst_minor TO igst_paise;
ALTER TABLE public.commerce_invoices RENAME COLUMN total_minor TO total_paise;

ALTER TABLE public.commerce_order_items RENAME COLUMN unit_price_minor TO unit_price_paise;
ALTER TABLE public.commerce_order_items RENAME COLUMN line_subtotal_minor TO line_subtotal_paise;

ALTER TABLE public.commerce_orders RENAME COLUMN subtotal_minor TO subtotal_paise;
ALTER TABLE public.commerce_orders RENAME COLUMN discount_minor TO discount_paise;
ALTER TABLE public.commerce_orders RENAME COLUMN cgst_minor TO cgst_paise;
ALTER TABLE public.commerce_orders RENAME COLUMN sgst_minor TO sgst_paise;
ALTER TABLE public.commerce_orders RENAME COLUMN igst_minor TO igst_paise;
ALTER TABLE public.commerce_orders RENAME COLUMN shipping_minor TO shipping_paise;
ALTER TABLE public.commerce_orders RENAME COLUMN total_minor TO total_paise;

ALTER TABLE public.commerce_payments RENAME COLUMN amount_minor TO amount_paise;
ALTER TABLE public.commerce_payments RENAME COLUMN refunded_minor TO refunded_paise;

ALTER TABLE public.commerce_product_variants RENAME COLUMN price_minor TO price_paise;
ALTER TABLE public.commerce_product_variants RENAME COLUMN compare_at_price_minor TO compare_at_price_paise;

ALTER TABLE public.commerce_settings RENAME COLUMN flat_shipping_minor TO flat_shipping_paise;
ALTER TABLE public.commerce_settings RENAME COLUMN free_shipping_above_minor TO free_shipping_above_paise;

ALTER TABLE public.commerce_subscriptions RENAME COLUMN locked_price_minor TO locked_price_paise;
