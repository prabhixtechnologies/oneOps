import { Link, useParams } from "react-router";
import { PageHeader } from "@/components/shared/PageHeader";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { EmptyState, ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { MobileCard, MobileCardRow, ResponsiveTable } from "@/components/shared/ResponsiveTable";
import { Money } from "@/components/shared/Money";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useCommerceCustomer, useCommerceOrders } from "@/features/commerce/api";

export default function CommerceCustomerDetailPage() {
  const { id } = useParams();
  const customerQuery = useCommerceCustomer(id);
  const ordersQuery = useCommerceOrders({
    search: customerQuery.data?.email,
  });

  const customer = customerQuery.data;
  const orders = ordersQuery.data?.pages.flatMap((p) => p.items) ?? [];

  if (customerQuery.isError) {
    return <ErrorState message="Customer not found" onRetry={() => void customerQuery.refetch()} />;
  }

  if (!customer) {
    return (
      <div className="space-y-3 p-6">
        <Skeleton className="h-8 w-56" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader
        title={customer.name ?? customer.email}
        description={customer.email}
        actions={
          <Button variant="outline" asChild>
            <Link to="/commerce/customers">All customers</Link>
          </Button>
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <div className="rounded-lg border border-border p-4 text-sm">
          <p className="text-text-muted">Phone</p>
          <p className="font-medium">{customer.phone ?? "—"}</p>
        </div>
        <div className="rounded-lg border border-border p-4 text-sm">
          <p className="text-text-muted">Marketing</p>
          <Badge variant={customer.marketingConsent ? "success" : "secondary"}>
            {customer.marketingConsent ? "Opted in" : "No"}
          </Badge>
        </div>
        <div className="rounded-lg border border-border p-4 text-sm">
          <p className="text-text-muted">Customer since</p>
          <RelativeTime date={customer.createdAt} />
        </div>
      </div>

      <section>
        <h2 className="mb-3 font-medium">Order history</h2>
        {orders.length === 0 ? (
          <EmptyState
            className="py-8"
            title="No orders yet"
            description="Orders for this email appear here after checkout."
          />
        ) : (
          <ResponsiveTable
            mobile={orders.map((o) => (
              <MobileCard key={o.id}>
                <p className="font-mono font-medium">{o.orderNumber}</p>
                <MobileCardRow label="Total" value={<Money amount={o.totalMinor} />} />
                <MobileCardRow label="Status" value={o.status} />
                <Button variant="outline" size="sm" className="mt-3 w-full" asChild>
                  <Link to={`/commerce/orders/${o.id}`}>View order</Link>
                </Button>
              </MobileCard>
            ))}
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Order</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Total</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {orders.map((o) => (
                  <TableRow key={o.id}>
                    <TableCell className="font-mono text-sm">{o.orderNumber}</TableCell>
                    <TableCell>{o.status}</TableCell>
                    <TableCell>
                      <Money amount={o.totalMinor} />
                    </TableCell>
                    <TableCell>
                      <RelativeTime date={o.createdAt} />
                    </TableCell>
                    <TableCell>
                      <Button variant="ghost" size="sm" asChild>
                        <Link to={`/commerce/orders/${o.id}`}>View</Link>
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </ResponsiveTable>
        )}
      </section>
    </div>
  );
}
