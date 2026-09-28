import { Link, useNavigate } from "react-router";
import { Eye } from "lucide-react";
import { PageHeader } from "@/components/shared/PageHeader";
import { MobileCard, MobileCardRow, ResponsiveTable } from "@/components/shared/ResponsiveTable";
import { RelativeTime } from "@/components/shared/RelativeTime";
import { EmptyState, ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { RowActions, RowActionsTrigger, type RowAction } from "@/components/ui/actions";
import { copyVerb, verbs } from "@/lib/row-verbs";
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
  const navigate = useNavigate();
  const query = useCommerceCustomers();
  const customers = query.data?.pages.flatMap((p) => p.items) ?? [];

  // Contact details are the whole point of this list — it exists so somebody can reach a
  // customer — and until now reaching one meant selecting an address out of a table cell.
  // `mailto:` rather than only a copy, because that is what the copy was for.
  const customerActions = (c: (typeof customers)[number]): RowAction[] =>
    verbs(
      { id: "open", label: "View customer", icon: <Eye />, onSelect: () => void navigate(`/commerce/customers/${c.id}`) },
      c.email ? { id: "email", label: "Send an email", onSelect: () => { window.location.href = `mailto:${c.email}`; } } : null,
      copyVerb("email", "Copy email", c.email),
      copyVerb("phone", "Copy phone", c.phone),
      copyVerb("name", "Copy name", c.name),
      copyVerb("id", "Copy customer ID", c.id),
    );

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
          <RowActions key={c.id} actions={customerActions(c)} label={c.name ?? c.email}>
          <MobileCard>
            <div className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="font-medium">{c.name ?? c.email}</p>
                <p className="break-all text-text-muted">{c.email}</p>
              </div>
              <RowActionsTrigger />
            </div>
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
          </RowActions>
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
              <RowActions key={c.id} actions={customerActions(c)} label={c.name ?? c.email}>
              <TableRow>
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
                  <div className="flex items-center justify-end gap-1">
                    <Button variant="ghost" size="sm" asChild>
                      <Link to={`/commerce/customers/${c.id}`}>View</Link>
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
