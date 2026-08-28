import { Link, useParams } from "react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Money } from "@/components/shared/Money";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  useAnnotateOrder,
  useCancelOrder,
  useCommerceOrder,
  useFulfillOrder,
  useOrderDownloads,
  useRefundOrder,
  useReissueDownload,
} from "@/features/commerce/api";
import { getApiErrorMessage } from "@/lib/api-client";
import { paiseToRupeesString, rupeesToPaise } from "@/lib/commerce-money";
import { PERMISSIONS } from "@/lib/permissions";

export default function CommerceOrderDetailPage() {
  const { id } = useParams();
  const orderQuery = useCommerceOrder(id);
  const downloadsQuery = useOrderDownloads(id);
  const fulfill = useFulfillOrder(id ?? "");
  const cancel = useCancelOrder(id ?? "");
  const annotate = useAnnotateOrder(id ?? "");
  const refund = useRefundOrder();
  const reissue = useReissueDownload(id ?? "");

  const [note, setNote] = useState("");
  const [refundAmount, setRefundAmount] = useState("");

  const order = orderQuery.data;
  const taxTotal = order ? order.cgstMinor + order.sgstMinor + order.igstMinor : 0;

  useEffect(() => {
    if (order?.internalNote) setNote(order.internalNote);
  }, [order?.internalNote]);

  if (orderQuery.isError) {
    return (
      <ErrorState message="Failed to load order" onRetry={() => void orderQuery.refetch()} />
    );
  }

  if (!order) {
    return <p className="p-6 text-sm text-text-muted">Loading order…</p>;
  }

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader
        title={order.orderNumber}
        description={order.customerEmail ?? undefined}
        actions={
          <Button variant="outline" asChild>
            <Link to="/commerce/orders">All orders</Link>
          </Button>
        }
      />

      <div className="flex flex-wrap gap-2">
        <Badge variant="secondary">{order.status.replace(/_/g, " ")}</Badge>
        {order.paidAt && (
          <span className="text-sm text-text-muted">
            Paid <RelativeTime date={order.paidAt} />
          </span>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="rounded-lg border border-border p-4">
          <h2 className="font-medium">Items</h2>
          <ul className="mt-3 divide-y divide-border text-sm">
            {order.items.map((item) => (
              <li key={item.id} className="flex justify-between py-2">
                <div>
                  <p className="font-medium">{item.productName}</p>
                  <p className="text-text-muted">{item.variantName} × {item.quantity}</p>
                  <Badge variant="outline" className="mt-1">{item.productType}</Badge>
                </div>
                <Money amount={item.lineSubtotalMinor} />
              </li>
            ))}
          </ul>
        </section>

        <section className="rounded-lg border border-border p-4">
          <h2 className="font-medium">Totals</h2>
          <dl className="mt-3 space-y-2 text-sm">
            <div className="flex justify-between">
              <dt className="text-text-muted">Subtotal</dt>
              <dd><Money amount={order.subtotalMinor} /></dd>
            </div>
            {order.discountMinor > 0 && (
              <div className="flex justify-between">
                <dt>Discount</dt>
                <dd>−<Money amount={order.discountMinor} /></dd>
              </div>
            )}
            <div className="flex justify-between">
              <dt className="text-text-muted">GST</dt>
              <dd><Money amount={taxTotal} /></dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-text-muted">Shipping</dt>
              <dd><Money amount={order.shippingMinor} /></dd>
            </div>
            <div className="flex justify-between border-t border-border pt-2 font-semibold">
              <dt>Total</dt>
              <dd><Money amount={order.totalMinor} /></dd>
            </div>
          </dl>
        </section>
      </div>

      {order.addresses.length > 0 && (
        <section className="rounded-lg border border-border p-4">
          <h2 className="font-medium">Addresses</h2>
          <div className="mt-3 grid gap-4 sm:grid-cols-2 text-sm">
            {order.addresses.map((a) => (
              <div key={a.addressType}>
                <p className="font-medium capitalize">{a.addressType.toLowerCase()}</p>
                <p>{a.name}</p>
                <p className="text-text-muted">
                  {a.line1}{a.line2 ? `, ${a.line2}` : ""}<br />
                  {a.city}, {a.state} {a.pincode}
                </p>
              </div>
            ))}
          </div>
        </section>
      )}

      <section className="rounded-lg border border-border p-4">
        <h2 className="font-medium">Timeline</h2>
        <ul className="mt-3 space-y-2 text-sm">
          {order.events.map((e) => (
            <li key={`${e.eventType}-${e.createdAt}`} className="flex flex-col gap-1 sm:flex-row sm:justify-between sm:gap-4">
              <span className="min-w-0 break-words">{e.message}</span>
              <RelativeTime date={e.createdAt} className="shrink-0 text-text-muted" />
            </li>
          ))}
        </ul>
      </section>

      {(downloadsQuery.data?.length ?? 0) > 0 && (
        <section className="rounded-lg border border-border p-4">
          <h2 className="font-medium">Digital downloads</h2>
          <ul className="mt-3 space-y-3 text-sm">
            {downloadsQuery.data?.map((d) => (
              <li key={d.orderItemId} className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="font-medium">{d.productName}</p>
                  <p className="text-text-muted">
                    {d.downloadCount}/{d.maxDownloadCount} downloads · expires{" "}
                    <RelativeTime date={d.linkExpiresAt} />
                  </p>
                </div>
                <PermissionGate permission={PERMISSIONS.COMMERCE_ORDER_MANAGE}>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={reissue.isPending}
                    onClick={() => {
                      void reissue.mutateAsync(d.orderItemId).then(() => toast.success("Download reissued"));
                    }}
                  >
                    Re-issue link
                  </Button>
                </PermissionGate>
              </li>
            ))}
          </ul>
        </section>
      )}

      <PermissionGate permission={PERMISSIONS.COMMERCE_ORDER_MANAGE}>
        <section className="space-y-4 rounded-lg border border-border p-4">
          <h2 className="font-medium">Actions</h2>
          <div className="flex flex-wrap gap-2">
            {order.status === "PAID" && (
              <Button
                disabled={fulfill.isPending}
                onClick={() => {
                  void fulfill.mutateAsync().then(() => toast.success("Order fulfilled"));
                }}
              >
                Mark fulfilled
              </Button>
            )}
            {order.status !== "CANCELLED" && order.status !== "REFUNDED" && (
              <Button
                variant="destructive"
                disabled={cancel.isPending}
                onClick={() => {
                  void cancel.mutateAsync().then(() => toast.success("Order cancelled"));
                }}
              >
                Cancel order
              </Button>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="note">Internal note</Label>
            <Textarea
              id="note"
              rows={3}
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
            <Button
              variant="secondary"
              size="sm"
              onClick={() => {
                void annotate.mutateAsync(note).then(() => toast.success("Note saved"));
              }}
            >
              Save note
            </Button>
          </div>
        </section>
      </PermissionGate>

      <PermissionGate permission={PERMISSIONS.COMMERCE_ORDER_REFUND}>
        {(order.status === "PAID" || order.status === "FULFILLED") && (
          <section className="rounded-lg border border-border p-4">
            <h2 className="font-medium">Refund</h2>
            <p className="mt-1 text-sm text-text-muted">
              Order total: ₹{paiseToRupeesString(order.totalMinor)}. Leave amount empty for full refund.
            </p>
            <div className="mt-3 flex flex-wrap gap-2">
              <Input
                placeholder="Amount in ₹"
                value={refundAmount}
                onChange={(e) => setRefundAmount(e.target.value)}
                className="max-w-[160px]"
              />
              <Button
                variant="destructive"
                disabled={refund.isPending}
                onClick={() => {
                  const body: { orderId: string; amountMinor?: number } = { orderId: order.id };
                  if (refundAmount.trim()) {
                    body.amountMinor = rupeesToPaise(refundAmount);
                  }
                  void refund
                    .mutateAsync(body)
                    .then(() => toast.success("Refund initiated"))
                    .catch((err) => toast.error(getApiErrorMessage(err)));
                }}
              >
                Issue refund
              </Button>
            </div>
          </section>
        )}
      </PermissionGate>
    </div>
  );
}
