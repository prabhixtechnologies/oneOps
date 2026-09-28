import { Link, useNavigate } from "react-router";
import { Eye } from "lucide-react";
import { useState } from "react";
import { PageHeader } from "@/components/shared/PageHeader";
import { MobileCard, MobileCardRow, ResponsiveTable } from "@/components/shared/ResponsiveTable";
import { Money } from "@/components/shared/Money";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { EmptyState, ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { RowActions, RowActionsTrigger, type RowAction } from "@/components/ui/actions";
import { copyVerb, filterVerb, verbs } from "@/lib/row-verbs";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useCommerceOrders } from "@/features/commerce/api";

const STATUSES = [
  "",
  "PENDING_PAYMENT",
  "PAID",
  "FULFILLED",
  "CANCELLED",
  "REFUNDED",
  "PAYMENT_FAILED",
];

export default function CommerceOrdersPage() {
  const navigate = useNavigate();
  const [status, setStatus] = useState("");
  const [search, setSearch] = useState("");
  const query = useCommerceOrders({
    status: status || undefined,
    search: search.trim() || undefined,
  });

  const orders = query.data?.pages.flatMap((p) => p.items) ?? [];

  // Fulfilment and refunds live on the order page, behind their own confirmations, so they are
  // not repeated here. What a row can offer honestly is the three identifiers somebody is
  // asked for on the phone — the order number, the customer's email, and the id support wants.
  const orderActions = (o: (typeof orders)[number]): RowAction[] =>
    verbs(
      { id: "open", label: "View order", icon: <Eye />, onSelect: () => void navigate(`/commerce/orders/${o.id}`) },
      filterVerb("customer", "Show this customer's orders", o.customerEmail, setSearch),
      copyVerb("number", "Copy order number", o.orderNumber),
      copyVerb("email", "Copy customer email", o.customerEmail),
      copyVerb("id", "Copy order ID", o.id),
    );

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader title="Orders" description="View and fulfil customer orders." />

      <div className="flex flex-wrap gap-2">
        <Input
          placeholder="Search email or order #…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="min-w-0 flex-1 sm:max-w-xs"
          aria-label="Search orders"
        />
        <Select value={status || "all"} onValueChange={(v) => setStatus(v === "all" ? "" : v)}>
          <SelectTrigger className="w-full sm:w-[180px]" aria-label="Order status">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            {STATUSES.filter(Boolean).map((s) => (
              <SelectItem key={s} value={s}>
                {s.replace(/_/g, " ")}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {query.isError && (
        <ErrorState message="Failed to load orders" onRetry={() => void query.refetch()} />
      )}

      {orders.length === 0 && !query.isLoading && (
        <EmptyState title="No orders" description="Orders appear here after customers checkout." />
      )}

      <ResponsiveTable
        mobile={orders.map((o) => (
          <RowActions key={o.id} actions={orderActions(o)} label={`Order ${o.orderNumber}`}>
          <MobileCard>
            <div className="flex items-start justify-between gap-2">
              <p className="font-mono font-medium">{o.orderNumber}</p>
              <Badge variant="secondary">{o.status.replace(/_/g, " ")}</Badge>
              <RowActionsTrigger />
            </div>
            <MobileCardRow label="Customer" value={o.customerEmail ?? "—"} />
            <MobileCardRow label="Total" value={<Money amount={o.totalPaise} />} />
            <MobileCardRow label="Created" value={<RelativeTime date={o.createdAt} />} />
            <Button variant="outline" size="sm" className="mt-3 w-full" asChild>
              <Link to={`/commerce/orders/${o.id}`} data-testid="commerce-order-open">View</Link>
            </Button>
          </MobileCard>
          </RowActions>
        ))}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Order</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Customer</TableHead>
              <TableHead>Total</TableHead>
              <TableHead>Created</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {orders.map((o) => (
              // `asChild` inside RowActions keeps this a plain `<tr>`; a wrapper would not be
              // valid inside `<tbody>`.
              <RowActions key={o.id} actions={orderActions(o)} label={`Order ${o.orderNumber}`}>
              <TableRow data-testid="commerce-order-row">
                <TableCell className="font-mono text-sm">{o.orderNumber}</TableCell>
                <TableCell>
                  <Badge variant="secondary">{o.status.replace(/_/g, " ")}</Badge>
                </TableCell>
                <TableCell>{o.customerEmail ?? "—"}</TableCell>
                <TableCell>
                  <Money amount={o.totalPaise} />
                </TableCell>
                <TableCell>
                  <RelativeTime date={o.createdAt} />
                </TableCell>
                <TableCell>
                  <div className="flex items-center justify-end gap-1">
                    <Button variant="ghost" size="sm" asChild>
                      <Link to={`/commerce/orders/${o.id}`} data-testid="commerce-order-open">View</Link>
                    </Button>
                    <RowActionsTrigger />
                  </div>
                </TableCell>
              </TableRow>
              </RowActions>
            ))}
          </TableBody>
        </Table>
      </ResponsiveTable>

      {query.hasNextPage && (
        <Button variant="outline" onClick={() => void query.fetchNextPage()} disabled={query.isFetchingNextPage}>
          {query.isFetchingNextPage ? "Loading…" : "Load more"}
        </Button>
      )}
    </div>
  );
}
