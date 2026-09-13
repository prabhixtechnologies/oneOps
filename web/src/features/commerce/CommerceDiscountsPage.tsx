import { useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { MobileCard, MobileCardRow, ResponsiveTable } from "@/components/shared/ResponsiveTable";
import { Money } from "@/components/shared/Money";
import { EmptyState, ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  useCommerceDiscounts,
  useCreateDiscount,
  useUpdateDiscount,
} from "@/features/commerce/api";
import { getApiErrorMessage } from "@/lib/api-client";
import { rupeesToPaise, paiseToRupeesString } from "@/lib/commerce-money";
import { PERMISSIONS } from "@/lib/permissions";
import type { DiscountView } from "@/lib/schemas/commerce";

export default function CommerceDiscountsPage() {
  const query = useCommerceDiscounts();
  const create = useCreateDiscount();

  const [code, setCode] = useState("");
  const [description, setDescription] = useState("");
  const [discountType, setDiscountType] = useState("PERCENTAGE");
  const [percentage, setPercentage] = useState("");
  const [amount, setAmount] = useState("");
  const [minOrder, setMinOrder] = useState("");
  const [maxUsesTotal, setMaxUsesTotal] = useState("");
  const [maxUsesPerCustomer, setMaxUsesPerCustomer] = useState("");

  const createDiscount = async () => {
    try {
      const body: Record<string, unknown> = {
        code: code.trim().toUpperCase(),
        description: description.trim() || undefined,
        discountType,
        minOrderMinor: minOrder.trim() ? rupeesToPaise(minOrder) : 0,
      };
      if (discountType === "PERCENTAGE") {
        body.percentage = Number(percentage);
      } else {
        body.amountMinor = rupeesToPaise(amount);
      }
      if (maxUsesTotal.trim()) body.maxUsesTotal = Number(maxUsesTotal);
      if (maxUsesPerCustomer.trim()) body.maxUsesPerCustomer = Number(maxUsesPerCustomer);
      await create.mutateAsync(body);
      toast.success("Discount created");
      setCode("");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader title="Discount codes" description="Create and manage storefront discount codes." />

      <PermissionGate permission={PERMISSIONS.COMMERCE_DISCOUNT_MANAGE}>
        <section className="grid gap-4 rounded-lg border border-border p-4 md:grid-cols-2">
          <h2 className="font-medium md:col-span-2">New discount</h2>
          <div className="space-y-2">
            <Label htmlFor="discount-code">Code</Label>
            <Input id="discount-code" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="discount-type">Type</Label>
            <Select value={discountType} onValueChange={setDiscountType}>
              <SelectTrigger id="discount-type" aria-label="Discount type">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="PERCENTAGE">Percentage</SelectItem>
                <SelectItem value="FIXED_AMOUNT">Fixed amount</SelectItem>
              </SelectContent>
            </Select>
          </div>
          {discountType === "PERCENTAGE" ? (
            <div className="space-y-2">
              <Label htmlFor="discount-percentage">Percentage</Label>
              <Input id="discount-percentage" type="number" min={1} max={100} value={percentage} onChange={(e) => setPercentage(e.target.value)} />
            </div>
          ) : (
            <div className="space-y-2">
              <Label htmlFor="discount-amount">Amount (₹)</Label>
              <Input id="discount-amount" inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value)} />
            </div>
          )}
          <div className="space-y-2">
            <Label htmlFor="discount-min-order">Min order (₹)</Label>
            <Input id="discount-min-order" inputMode="decimal" value={minOrder} onChange={(e) => setMinOrder(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="discount-max-uses">Max uses (total)</Label>
            <Input id="discount-max-uses" type="number" value={maxUsesTotal} onChange={(e) => setMaxUsesTotal(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="discount-max-per-customer">Max uses per customer</Label>
            <Input id="discount-max-per-customer" type="number" value={maxUsesPerCustomer} onChange={(e) => setMaxUsesPerCustomer(e.target.value)} />
          </div>
          <div className="space-y-2 md:col-span-2">
            <Label htmlFor="discount-description">Description</Label>
            <Input id="discount-description" value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>
          <Button onClick={() => void createDiscount()} disabled={create.isPending}>
            Create discount
          </Button>
        </section>
      </PermissionGate>

      {query.isError && (
        <ErrorState message="Failed to load discounts" onRetry={() => void query.refetch()} />
      )}

      {(query.data?.length ?? 0) === 0 && !query.isLoading && (
        <EmptyState title="No discount codes" />
      )}

      <ResponsiveTable
        mobile={(query.data ?? []).map((d) => (
          <DiscountMobileCard key={d.id} discount={d} />
        ))}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Code</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>Value</TableHead>
              <TableHead>Uses</TableHead>
              <TableHead>Active</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {(query.data ?? []).map((d) => (
              <DiscountRow key={d.id} discount={d} />
            ))}
          </TableBody>
        </Table>
      </ResponsiveTable>
    </div>
  );
}

function DiscountRow({ discount }: { discount: DiscountView }) {
  const update = useUpdateDiscount(discount.id);

  return (
    <TableRow>
      <TableCell className="font-mono font-medium">{discount.code}</TableCell>
      <TableCell>{discount.discountType}</TableCell>
      <TableCell>
        {discount.discountType === "PERCENTAGE"
          ? `${discount.percentage}%`
          : discount.amountMinor != null
            ? `₹${paiseToRupeesString(discount.amountMinor)}`
            : "—"}
      </TableCell>
      <TableCell>
        {discount.usesCount}
        {discount.maxUsesTotal != null ? ` / ${discount.maxUsesTotal}` : ""}
      </TableCell>
      <TableCell>
        <Badge variant={discount.active ? "success" : "secondary"}>
          {discount.active ? "Active" : "Inactive"}
        </Badge>
      </TableCell>
      <TableCell>
        <PermissionGate permission={PERMISSIONS.COMMERCE_DISCOUNT_MANAGE}>
          <Switch
            checked={discount.active}
            onCheckedChange={(active) => {
              void update.mutateAsync({ active }).then(() => toast.success("Updated"));
            }}
          />
        </PermissionGate>
      </TableCell>
    </TableRow>
  );
}

function DiscountMobileCard({ discount }: { discount: DiscountView }) {
  const update = useUpdateDiscount(discount.id);
  return (
    <MobileCard>
      <p className="font-mono font-medium">{discount.code}</p>
      <MobileCardRow label="Type" value={discount.discountType} />
      <MobileCardRow
        label="Value"
        value={
          discount.discountType === "PERCENTAGE"
            ? `${discount.percentage}%`
            : discount.amountMinor != null
              ? <Money amount={discount.amountMinor} />
              : "—"
        }
      />
      <MobileCardRow label="Uses" value={String(discount.usesCount)} />
      <PermissionGate permission={PERMISSIONS.COMMERCE_DISCOUNT_MANAGE}>
        <div className="mt-3 flex items-center gap-2">
          <Switch
            checked={discount.active}
            onCheckedChange={(active) => {
              void update.mutateAsync({ active });
            }}
          />
          <span className="text-sm">Active</span>
        </div>
      </PermissionGate>
    </MobileCard>
  );
}
