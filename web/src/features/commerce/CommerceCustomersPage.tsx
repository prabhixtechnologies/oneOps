import { Link } from "react-router";
import { PageHeader } from "@/components/shared/PageHeader";
import { MobileCard, MobileCardRow, ResponsiveTable } from "@/components/shared/ResponsiveTable";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { EmptyState, ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useCommerceCustomers } from "@/features/commerce/api";

export default function CommerceCustomersPage() {
  const query = useCommerceCustomers();
  const customers = query.data?.pages.flatMap((p) => p.items) ?? [];

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader title="Customers" description="Storefront customers and their contact details." />

      {query.isError && (
        <ErrorState message="Failed to load customers" onRetry={() => void query.refetch()} />
      )}

      {customers.length === 0 && !query.isLoading && (
        <EmptyState title="No customers yet" description="Customers are created at checkout." />
      )}

      <ResponsiveTable
        mobile={customers.map((c) => (
          <MobileCard key={c.id}>
            <p className="font-medium">{c.name ?? c.email}</p>
            <p className="text-text-muted">{c.email}</p>
            <MobileCardRow
              label="Marketing"
              value={
                <Badge variant={c.marketingConsent ? "success" : "secondary"}>
                  {c.marketingConsent ? "Opted in" : "No"}
                </Badge>
              }
            />
            <MobileCardRow label="Since" value={<RelativeTime date={c.createdAt} />} />
            <Button variant="outline" size="sm" className="mt-3 w-full" asChild>
              <Link to={`/commerce/customers/${c.id}`}>View</Link>
            </Button>
          </MobileCard>
        ))}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Phone</TableHead>
              <TableHead>Marketing</TableHead>
              <TableHead>Since</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {customers.map((c) => (
              <TableRow key={c.id}>
                <TableCell className="font-medium">{c.name ?? "—"}</TableCell>
                <TableCell>{c.email}</TableCell>
                <TableCell>{c.phone ?? "—"}</TableCell>
                <TableCell>
                  <Badge variant={c.marketingConsent ? "success" : "secondary"}>
                    {c.marketingConsent ? "Yes" : "No"}
                  </Badge>
                </TableCell>
                <TableCell>
                  <RelativeTime date={c.createdAt} />
                </TableCell>
                <TableCell>
                  <Button variant="ghost" size="sm" asChild>
                    <Link to={`/commerce/customers/${c.id}`}>View</Link>
                  </Button>
                </TableCell>
              </TableRow>
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
