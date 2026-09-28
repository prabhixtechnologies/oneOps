import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { useDirtyTracker } from "@prabhix/ui";
import { PageHeader } from "@/components/shared/PageHeader";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { UnsavedChanges } from "@/components/shared/UnsavedChanges";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import {
  useCommerceSettings,
  useUpdateCommerceSettings,
} from "@/features/commerce/api";
import { getApiErrorMessage } from "@/lib/api-client";
import { paiseToRupeesString, rupeesToPaise } from "@prabhix/oneops-api";
import { PERMISSIONS } from "@/lib/permissions";

export default function CommerceSettingsPage() {
  const settingsQuery = useCommerceSettings();
  const update = useUpdateCommerceSettings();

  const [sellerName, setSellerName] = useState("");
  const [sellerGstin, setSellerGstin] = useState("");
  const [sellerState, setSellerState] = useState("");
  const [sellerAddress, setSellerAddress] = useState("");
  const [orderPrefix, setOrderPrefix] = useState("");
  const [gstPercent, setGstPercent] = useState("18");
  const [flatShipping, setFlatShipping] = useState("");
  const [freeShippingAbove, setFreeShippingAbove] = useState("");

  /*
    What the server last told us, in the same shape and formatting as the fields. Derived
    from the query rather than captured on first load, so a successful save re-baselines
    the form and stops it claiming to be dirty over changes that are now persisted.
  */
  const saved = useMemo(() => {
    const s = settingsQuery.data;
    return {
      sellerName: s?.sellerName ?? "",
      sellerGstin: s?.sellerGstin ?? "",
      sellerState: s?.sellerState ?? "",
      sellerAddress: s?.sellerAddress ?? "",
      orderPrefix: s?.orderNumberPrefix ?? "",
      gstPercent: s ? String(s.gstPercent) : "18",
      flatShipping: s ? paiseToRupeesString(s.flatShippingPaise) : "",
      freeShippingAbove:
        s?.freeShippingAbovePaise != null ? paiseToRupeesString(s.freeShippingAbovePaise) : "",
    };
  }, [settingsQuery.data]);

  useEffect(() => {
    if (!settingsQuery.data) return;
    setSellerName(saved.sellerName);
    setSellerGstin(saved.sellerGstin);
    setSellerState(saved.sellerState);
    setSellerAddress(saved.sellerAddress);
    setOrderPrefix(saved.orderPrefix);
    setGstPercent(saved.gstPercent);
    setFlatShipping(saved.flatShipping);
    setFreeShippingAbove(saved.freeShippingAbove);
  }, [saved, settingsQuery.data]);

  const dirty = useDirtyTracker(
    {
      sellerName,
      sellerGstin,
      sellerState,
      sellerAddress,
      orderPrefix,
      gstPercent,
      flatShipping,
      freeShippingAbove,
    },
    saved,
  );

  const save = async () => {
    try {
      await update.mutateAsync({
        sellerName: sellerName.trim() || undefined,
        sellerGstin: sellerGstin.trim() || undefined,
        sellerState: sellerState.trim() || undefined,
        sellerAddress: sellerAddress.trim() || undefined,
        orderNumberPrefix: orderPrefix.trim() || undefined,
        gstPercent: Number(gstPercent),
        flatShippingPaise: rupeesToPaise(flatShipping || "0"),
        freeShippingAbovePaise: freeShippingAbove.trim()
          ? rupeesToPaise(freeShippingAbove)
          : undefined,
      });
      toast.success("Settings saved");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <UnsavedChanges
        when={dirty}
        description="The seller, tax and shipping details you changed have not been saved."
      />
      <PageHeader
        title="Shop settings"
        description="Tax, shipping, and seller details for invoices and checkout."
      />

      <PermissionGate
        permission={PERMISSIONS.COMMERCE_SETTINGS_MANAGE}
        fallback={<p className="text-sm text-text-muted">You cannot edit shop settings.</p>}
      >
        {settingsQuery.isLoading && (
          <div className="mx-auto max-w-xl space-y-3">
            <Skeleton className="h-11 w-full" />
            <Skeleton className="h-11 w-full" />
            <Skeleton className="h-24 w-full" />
            <Skeleton className="h-11 w-40" />
          </div>
        )}
        <form
          className="mx-auto max-w-xl space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            void save();
          }}
        >
          <div className="space-y-2">
            <Label htmlFor="seller-name">Seller name</Label>
            <Input id="seller-name" value={sellerName} onChange={(e) => setSellerName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seller-gstin">GSTIN</Label>
            <Input id="seller-gstin" value={sellerGstin} onChange={(e) => setSellerGstin(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seller-state">Seller state</Label>
            <Input id="seller-state" value={sellerState} onChange={(e) => setSellerState(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="seller-address">Seller address</Label>
            <Textarea id="seller-address" rows={3} value={sellerAddress} onChange={(e) => setSellerAddress(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="order-prefix">Order number prefix</Label>
            <Input id="order-prefix" value={orderPrefix} onChange={(e) => setOrderPrefix(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="gst-percent">GST %</Label>
            <Input id="gst-percent" type="number" min={0} max={100} value={gstPercent} onChange={(e) => setGstPercent(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="flat-shipping">Flat shipping (₹)</Label>
            <Input id="flat-shipping" inputMode="decimal" value={flatShipping} onChange={(e) => setFlatShipping(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="free-shipping-above">Free shipping above (₹)</Label>
            <Input id="free-shipping-above" inputMode="decimal" value={freeShippingAbove} onChange={(e) => setFreeShippingAbove(e.target.value)} />
          </div>
          <Button type="submit" disabled={update.isPending}>
            Save settings
          </Button>
        </form>
      </PermissionGate>
    </div>
  );
}
