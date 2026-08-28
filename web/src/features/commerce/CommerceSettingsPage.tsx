import { useEffect, useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  useCommerceSettings,
  useUpdateCommerceSettings,
} from "@/features/commerce/api";
import { getApiErrorMessage } from "@/lib/api-client";
import { paiseToRupeesString, rupeesToPaise } from "@/lib/commerce-money";
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

  useEffect(() => {
    const s = settingsQuery.data;
    if (!s) return;
    setSellerName(s.sellerName ?? "");
    setSellerGstin(s.sellerGstin ?? "");
    setSellerState(s.sellerState ?? "");
    setSellerAddress(s.sellerAddress ?? "");
    setOrderPrefix(s.orderNumberPrefix ?? "");
    setGstPercent(String(s.gstPercent));
    setFlatShipping(paiseToRupeesString(s.flatShippingMinor));
    setFreeShippingAbove(
      s.freeShippingAboveMinor != null
        ? paiseToRupeesString(s.freeShippingAboveMinor)
        : "",
    );
  }, [settingsQuery.data]);

  const save = async () => {
    try {
      await update.mutateAsync({
        sellerName: sellerName.trim() || undefined,
        sellerGstin: sellerGstin.trim() || undefined,
        sellerState: sellerState.trim() || undefined,
        sellerAddress: sellerAddress.trim() || undefined,
        orderNumberPrefix: orderPrefix.trim() || undefined,
        gstPercent: Number(gstPercent),
        flatShippingMinor: rupeesToPaise(flatShipping || "0"),
        freeShippingAboveMinor: freeShippingAbove.trim()
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
      <PageHeader
        title="Shop settings"
        description="Tax, shipping, and seller details for invoices and checkout."
      />

      <PermissionGate
        permission={PERMISSIONS.COMMERCE_SETTINGS_MANAGE}
        fallback={<p className="text-sm text-text-muted">You cannot edit shop settings.</p>}
      >
        {settingsQuery.isLoading && <p className="text-sm text-text-muted">Loading…</p>}
        <form
          className="mx-auto max-w-xl space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            void save();
          }}
        >
          <div className="space-y-2">
            <Label>Seller name</Label>
            <Input value={sellerName} onChange={(e) => setSellerName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>GSTIN</Label>
            <Input value={sellerGstin} onChange={(e) => setSellerGstin(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>Seller state</Label>
            <Input value={sellerState} onChange={(e) => setSellerState(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>Seller address</Label>
            <Textarea rows={3} value={sellerAddress} onChange={(e) => setSellerAddress(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>Order number prefix</Label>
            <Input value={orderPrefix} onChange={(e) => setOrderPrefix(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>GST %</Label>
            <Input type="number" min={0} max={100} value={gstPercent} onChange={(e) => setGstPercent(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>Flat shipping (₹)</Label>
            <Input inputMode="decimal" value={flatShipping} onChange={(e) => setFlatShipping(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>Free shipping above (₹)</Label>
            <Input inputMode="decimal" value={freeShippingAbove} onChange={(e) => setFreeShippingAbove(e.target.value)} />
          </div>
          <Button type="submit" disabled={update.isPending}>
            Save settings
          </Button>
        </form>
      </PermissionGate>
    </div>
  );
}
