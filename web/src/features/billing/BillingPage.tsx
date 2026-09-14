import { Download } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { Money } from "@/components/shared/Money";
import { CursorList } from "@/components/shared/CursorList";
import { ErrorState } from "@/components/shared/states";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { RAZORPAY_KEY_ID } from "@/lib/config";
import { formatPaise, type Invoice } from "@/lib/schemas/billing";
import { PERMISSIONS } from "@/lib/permissions";
import { getApiErrorMessage } from "@/lib/api-client";
import {
  useBillingAddress,
  useCancelSubscription,
  useChangeSeats,
  useCreateOrder,
  useDownloadInvoice,
  useEntitlements,
  useInvoices,
  usePaymentMethods,
  usePlans,
  useReactivateSubscription,
  useSubscription,
  useUpdateBillingAddress,
  useVerifyPayment,
} from "@/features/org/api";

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => { open: () => void };
  }
}

function loadRazorpayScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (window.Razorpay) {
      resolve();
      return;
    }
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Failed to load Razorpay"));
    document.body.appendChild(script);
  });
}

export default function BillingPage() {
  const subQuery = useSubscription();
  const entitlementsQuery = useEntitlements();
  const plansQuery = usePlans();
  const invoicesQuery = useInvoices();
  const paymentMethodsQuery = usePaymentMethods();
  const addressQuery = useBillingAddress();
  const createOrder = useCreateOrder();
  const verifyPayment = useVerifyPayment();
  const downloadInvoice = useDownloadInvoice();
  const cancelSub = useCancelSubscription();
  const reactivateSub = useReactivateSubscription();
  const changeSeats = useChangeSeats();
  const updateAddress = useUpdateBillingAddress();
  const [upgrading, setUpgrading] = useState(false);
  const [seats, setSeats] = useState("");
  const [addressForm, setAddressForm] = useState({
    line1: "",
    line2: "",
    city: "",
    state: "",
    pincode: "",
    country: "IN",
    gstin: "",
    billingEmail: "",
  });

  useEffect(() => {
    if (addressQuery.data) {
      setAddressForm({
        line1: addressQuery.data.line1 ?? "",
        line2: addressQuery.data.line2 ?? "",
        city: addressQuery.data.city ?? "",
        state: addressQuery.data.state ?? "",
        pincode: addressQuery.data.pincode ?? "",
        country: addressQuery.data.country ?? "IN",
        gstin: addressQuery.data.gstin ?? "",
        billingEmail: addressQuery.data.billingEmail ?? "",
      });
    }
  }, [addressQuery.data]);

  useEffect(() => {
    if (subQuery.data) setSeats(String(subQuery.data.seats));
  }, [subQuery.data]);

  const invoices = invoicesQuery.data?.pages.flatMap((p) => p.items) ?? [];

  const upgrade = async (planId: string) => {
    setUpgrading(true);
    try {
      const order = await createOrder.mutateAsync(planId);
      await loadRazorpayScript();
      if (!window.Razorpay) {
        toast.error("Razorpay checkout unavailable");
        return;
      }
      const amount = order.amountPaise ?? order.amount ?? 0;
      const rzp = new window.Razorpay({
        key: RAZORPAY_KEY_ID || order.keyId,
        amount,
        currency: order.currency,
        order_id: order.orderId,
        name: "Prabhix",
        description: order.planName ?? "Plan upgrade",
        handler: (response: {
          razorpay_payment_id: string;
          razorpay_order_id: string;
          razorpay_signature: string;
        }) => {
          void verifyPayment
            .mutateAsync({
              orderId: response.razorpay_order_id,
              paymentId: response.razorpay_payment_id,
              signature: response.razorpay_signature,
            })
            .then(() => toast.success("Payment verified — plan upgraded"));
        },
      });
      rzp.open();
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    } finally {
      setUpgrading(false);
    }
  };

  if (subQuery.isLoading) {
    return (
      <div className="p-6">
        <Skeleton className="h-96" />
      </div>
    );
  }

  if (subQuery.isError || !subQuery.data) {
    return <ErrorState message="Failed to load billing" onRetry={() => void subQuery.refetch()} />;
  }

  const sub = subQuery.data;

  return (
    <div className="space-y-8 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader title="Billing" description="Manage your subscription, usage, and invoices" />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div className="rounded-lg border border-border p-4">
          <p className="text-sm text-text-muted">Current plan</p>
          <p className="mt-1 text-xl font-semibold">{sub.planName}</p>
          <Badge className="mt-2" variant={sub.status === "ACTIVE" ? "success" : "warning"}>
            {sub.status}
          </Badge>
          {sub.cancelAtPeriodEnd && (
            <p className="mt-2 text-xs text-warning">Cancels at period end</p>
          )}
        </div>
        <div className="rounded-lg border border-border p-4">
          <p className="text-sm text-text-muted">Seats</p>
          <p className="mt-1 text-xl font-semibold">{sub.seats}</p>
          <PermissionGate permission={PERMISSIONS.BILLING_MANAGE}>
            <div className="mt-2 flex gap-2">
              <Input
                type="number"
                min={1}
                value={seats}
                onChange={(e) => setSeats(e.target.value)}
                className="h-8 w-20"
              />
              <Button
                size="sm"
                variant="outline"
                disabled={changeSeats.isPending}
                onClick={() =>
                  void changeSeats
                    .mutateAsync(Number(seats))
                    .then(() => toast.success("Seats updated"))
                    .catch((e) => toast.error(getApiErrorMessage(e)))
                }
              >
                Update
              </Button>
            </div>
          </PermissionGate>
        </div>
        <div className="rounded-lg border border-border p-4">
          <p className="text-sm text-text-muted">Current period ends</p>
          <p className="mt-1 text-lg font-semibold">
            {new Date(sub.currentPeriodEnd).toLocaleDateString()}
          </p>
        </div>
      </div>

      {entitlementsQuery.data && (
        <div className="rounded-lg border border-border p-4">
          <h2 className="mb-3 text-lg font-medium">Entitlements</h2>
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {Object.entries(entitlementsQuery.data).map(([k, v]) => (
              <div key={k} className="text-sm">
                <span className="font-mono text-text-muted">{k}</span>: {String(v)}
              </div>
            ))}
          </div>
        </div>
      )}

      <PermissionGate permission={PERMISSIONS.BILLING_MANAGE}>
        <div className="flex flex-wrap gap-2">
          {sub.status === "CANCELLED" || sub.cancelAtPeriodEnd ? (
            <Button
              variant="outline"
              disabled={reactivateSub.isPending}
              onClick={() =>
                void reactivateSub
                  .mutateAsync()
                  .then(() => toast.success("Subscription reactivated"))
                  .catch((e) => toast.error(getApiErrorMessage(e)))
              }
            >
              Reactivate subscription
            </Button>
          ) : (
            <Button
              variant="destructive"
              disabled={cancelSub.isPending}
              onClick={() =>
                void cancelSub
                  .mutateAsync({ atPeriodEnd: true })
                  .then(() => toast.success("Subscription will cancel at period end"))
                  .catch((e) => toast.error(getApiErrorMessage(e)))
              }
            >
              Cancel subscription
            </Button>
          )}
        </div>
      </PermissionGate>

      <div>
        <h2 className="mb-4 text-lg font-medium">Plans</h2>
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {plansQuery.data?.items.map((plan) => (
            <div key={plan.id} className="rounded-lg border border-border p-4" data-testid="billing-plan">
              <h3 className="font-semibold">{plan.name}</h3>
              <p className="text-sm text-text-muted">{plan.description}</p>
              <p className="mt-2 text-2xl font-bold">
                {plan.amountPaise > 0 ? (
                  <>
                    <Money amount={formatPaise(plan.amountPaise)} currency="INR" />
                    <span className="text-sm font-normal text-text-muted">/mo</span>
                  </>
                ) : (
                  "Custom"
                )}
              </p>
              <p className="mt-1 text-xs text-text-muted">{plan.includedSeats} seats included</p>
              <Button
                className="mt-4 w-full"
                variant={sub.planId === plan.id ? "secondary" : "default"}
                disabled={sub.planId === plan.id || upgrading}
                data-testid="plan-upgrade"
                data-plan-id={plan.id}
                onClick={() => void upgrade(plan.id)}
              >
                {sub.planId === plan.id ? "Current plan" : "Upgrade"}
              </Button>
            </div>
          ))}
        </div>
      </div>

      <div>
        <h2 className="mb-4 text-lg font-medium">Invoices</h2>
        <div className="h-64 rounded-lg border border-border md:h-80">
          <CursorList<Invoice>
            items={invoices}
            hasMore={!!invoicesQuery.hasNextPage}
            isLoading={invoicesQuery.isLoading}
            isError={invoicesQuery.isError}
            isFetchingNextPage={invoicesQuery.isFetchingNextPage}
            onLoadMore={() => void invoicesQuery.fetchNextPage()}
            getKey={(inv) => inv.id}
            emptyTitle="No invoices"
            renderItem={(inv) => (
              <div className="flex flex-col gap-2 border-b border-border px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="font-medium">{inv.invoiceNumber}</p>
                  <p className="text-text-muted">{inv.issueDate}</p>
                </div>
                <div className="flex items-center gap-3">
                  <Money amount={formatPaise(inv.totalPaise)} currency={inv.currency} />
                  <Badge variant={inv.status === "PAID" ? "success" : "warning"}>{inv.status}</Badge>
                  <PermissionGate permission={PERMISSIONS.BILLING_INVOICE_DOWNLOAD}>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label="Download PDF"
                      disabled={downloadInvoice.isPending}
                      onClick={() =>
                        void downloadInvoice
                          .mutateAsync(inv.id)
                          .catch((e) => toast.error(getApiErrorMessage(e)))
                      }
                    >
                      <Download className="h-4 w-4" />
                    </Button>
                  </PermissionGate>
                </div>
              </div>
            )}
          />
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <div>
          <h2 className="mb-4 text-lg font-medium">Payment methods</h2>
          {(paymentMethodsQuery.data ?? []).map((pm) => (
            <div key={pm.method + pm.label} className="mb-2 rounded-lg border border-border p-4 text-sm">
              {pm.label} ({pm.method})
              {pm.vaulted && <Badge className="ml-2" variant="secondary">Vaulted</Badge>}
            </div>
          ))}
        </div>
        <div>
          <h2 className="mb-4 text-lg font-medium">Billing address</h2>
          <div className="space-y-3 rounded-lg border border-border p-4 text-sm">
            {(["line1", "line2", "city", "state", "pincode", "country", "gstin", "billingEmail"] as const).map(
              (field) => (
                <div key={field} className="space-y-1">
                  <Label className="capitalize">{field}</Label>
                  <Input
                    value={addressForm[field]}
                    onChange={(e) => setAddressForm((f) => ({ ...f, [field]: e.target.value }))}
                  />
                </div>
              ),
            )}
            <PermissionGate permission={PERMISSIONS.BILLING_MANAGE}>
              <Button
                disabled={updateAddress.isPending}
                onClick={() =>
                  void updateAddress
                    .mutateAsync(addressForm)
                    .then(() => toast.success("Billing address updated"))
                    .catch((e) => toast.error(getApiErrorMessage(e)))
                }
              >
                Save address
              </Button>
            </PermissionGate>
          </div>
        </div>
      </div>
    </div>
  );
}
