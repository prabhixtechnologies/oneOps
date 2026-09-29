import { useRef, useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { MobileCard, MobileCardRow, ResponsiveTable } from "@/components/shared/ResponsiveTable";
import { Money } from "@/components/shared/Money";
import { EmptyState, ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { RowActions, RowActionsTrigger, type RowAction } from "@/components/ui/actions";
import { copyVerb, verbs } from "@/lib/row-verbs";
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
import { rupeesToPaise, paiseToRupeesString } from "@prabhixtechnologies/oneops-api";
import { useAuth } from "@/lib/auth";
import { PERMISSIONS, hasPermission } from "@/lib/permissions";
import type { DiscountView } from "@/lib/schemas/commerce";

export default function CommerceDiscountsPage() {
  const query = useCommerceDiscounts();
  const create = useCreateDiscount();

  const codeRef = useRef<HTMLInputElement>(null);
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
        minOrderPaise: minOrder.trim() ? rupeesToPaise(minOrder) : 0,
      };
      if (discountType === "PERCENTAGE") {
        body.percentage = Number(percentage);
      } else {
        body.amountPaise = rupeesToPaise(amount);
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
            <Input ref={codeRef} id="discount-code" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} />
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
        <EmptyState
          title="No discount codes"
          description="Codes appear here once you create one. The form above is where they start."
          // The form is already on the page, so the useful action is to put the cursor in it
          // rather than to repeat the button that is sitting at the bottom of it.
          action={{
            label: "Write the first code",
            onClick: () => {
              codeRef.current?.scrollIntoView({ block: "center", behavior: "smooth" });
              codeRef.current?.focus();
            },
          }}
        />
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

/**
 * One action list for the desktop row and the mobile card.
 *
 * A hook rather than a function because the mutation is one, and because both layouts had
 * already written the same `onCheckedChange` twice — with different feedback. The row toasted
 * on success and the card said nothing.
 */
function useDiscountActions(discount: DiscountView): RowAction[] {
  const { permissions } = useAuth();
  const canManage = hasPermission(permissions, PERMISSIONS.COMMERCE_DISCOUNT_MANAGE);
  const update = useUpdateDiscount(discount.id);

  const setActive = async (active: boolean) => {
    try {
      await update.mutateAsync({ active });
      toast.success(active ? `${discount.code} is live` : `${discount.code} is switched off`);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  return verbs(
    canManage && {
      id: "active",
      label: discount.active ? "Switch off" : "Switch on",
      // Turning a live code off can strand a customer mid-checkout, so it asks. Turning one on
      // cannot hurt anybody and does not.
      ...(discount.active
        ? {
            risk: "confirm" as const,
            confirm: {
              title: `Switch off ${discount.code}?`,
              message:
                "Anyone part-way through checkout with this code loses the discount at the last step. Switching it back on takes effect immediately.",
              confirmLabel: "Switch off",
            },
          }
        : {}),
      onSelect: () => setActive(!discount.active),
    },
    copyVerb("code", "Copy code", discount.code),
  );
}

function DiscountRow({ discount }: { discount: DiscountView }) {
  const update = useUpdateDiscount(discount.id);
  const actions = useDiscountActions(discount);

  return (
    <RowActions actions={actions} label={`Discount ${discount.code}`}>
    <TableRow>
      <TableCell className="font-mono font-medium">{discount.code}</TableCell>
      <TableCell>{discount.discountType}</TableCell>
      <TableCell>
        {discount.discountType === "PERCENTAGE"
          ? `${discount.percentage}%`
          : discount.amountPaise != null
            ? `₹${paiseToRupeesString(discount.amountPaise)}`
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
        <div className="flex items-center justify-end gap-2">
          <PermissionGate permission={PERMISSIONS.COMMERCE_DISCOUNT_MANAGE}>
            <Switch
              checked={discount.active}
              aria-label={`${discount.code} active`}
              onCheckedChange={(active) => {
                void update.mutateAsync({ active }).then(() => toast.success("Updated"));
              }}
            />
          </PermissionGate>
          <RowActionsTrigger />
        </div>
      </TableCell>
    </TableRow>
    </RowActions>
  );
}

function DiscountMobileCard({ discount }: { discount: DiscountView }) {
  const update = useUpdateDiscount(discount.id);
  const actions = useDiscountActions(discount);
  return (
    <RowActions actions={actions} label={`Discount ${discount.code}`}>
    <MobileCard>
      <div className="flex items-start justify-between gap-2">
        <p className="font-mono font-medium">{discount.code}</p>
        <RowActionsTrigger />
      </div>
      <MobileCardRow label="Type" value={discount.discountType} />
      <MobileCardRow
        label="Value"
        value={
          discount.discountType === "PERCENTAGE"
            ? `${discount.percentage}%`
            : discount.amountPaise != null
              ? <Money amount={discount.amountPaise} />
              : "—"
        }
      />
      <MobileCardRow label="Uses" value={String(discount.usesCount)} />
      <PermissionGate permission={PERMISSIONS.COMMERCE_DISCOUNT_MANAGE}>
        <div className="mt-3 flex items-center gap-2">
          <Switch
            checked={discount.active}
            aria-label={`${discount.code} active`}
            onCheckedChange={(active) => {
              void update.mutateAsync({ active });
            }}
          />
          <span className="text-sm">Active</span>
        </div>
      </PermissionGate>
    </MobileCard>
    </RowActions>
  );
}
