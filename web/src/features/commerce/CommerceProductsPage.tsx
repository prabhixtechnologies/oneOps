import { Link } from "react-router";
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useCommerceProducts } from "@/features/commerce/api";
import { apiRequest } from "@/lib/api-client";
import { getApiErrorMessage } from "@/lib/api-client";
import { productDetailSchema } from "@/lib/schemas/commerce";
import { PERMISSIONS } from "@/lib/permissions";

export default function CommerceProductsPage() {
  const [status, setStatus] = useState("");
  const [type, setType] = useState("");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [bulkStatus, setBulkStatus] = useState("ACTIVE");
  const [bulkBusy, setBulkBusy] = useState(false);

  const query = useCommerceProducts({
    status: status || undefined,
    type: type || undefined,
  });

  const products = query.data?.pages.flatMap((p) => p.items) ?? [];
  const filtered = search.trim()
    ? products.filter(
        (p) =>
          p.name.toLowerCase().includes(search.toLowerCase()) ||
          p.slug.toLowerCase().includes(search.toLowerCase()),
      )
    : products;

  const toggle = (id: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const applyBulk = async () => {
    if (selected.size === 0) return;
    setBulkBusy(true);
    try {
      for (const id of selected) {
        await apiRequest(`/commerce/products/${id}`, productDetailSchema, {
          method: "PUT",
          body: { status: bulkStatus },
        });
      }
      toast.success(`Updated ${selected.size} product(s)`);
      setSelected(new Set());
      void query.refetch();
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    } finally {
      setBulkBusy(false);
    }
  };

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader
        title="Products"
        description="Manage your storefront catalog, variants, and pricing."
        actions={
          <PermissionGate permission={PERMISSIONS.COMMERCE_CATALOG_MANAGE}>
            <Button asChild>
              <Link to="/commerce/products/new">New product</Link>
            </Button>
          </PermissionGate>
        }
      />

      <div className="flex flex-wrap gap-2">
        <Input
          placeholder="Search name or slug…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="min-w-0 flex-1 sm:max-w-xs"
          aria-label="Search products"
        />
        <Select value={status || "all"} onValueChange={(v) => setStatus(v === "all" ? "" : v)}>
          <SelectTrigger className="w-full sm:w-[140px]" aria-label="Product status">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All status</SelectItem>
            <SelectItem value="DRAFT">Draft</SelectItem>
            <SelectItem value="ACTIVE">Active</SelectItem>
            <SelectItem value="ARCHIVED">Archived</SelectItem>
          </SelectContent>
        </Select>
        <Select value={type || "all"} onValueChange={(v) => setType(v === "all" ? "" : v)}>
          <SelectTrigger className="w-full sm:w-[160px]" aria-label="Product type">
            <SelectValue placeholder="Type" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All types</SelectItem>
            <SelectItem value="PHYSICAL">Physical</SelectItem>
            <SelectItem value="DIGITAL">Digital</SelectItem>
            <SelectItem value="SERVICE">Service</SelectItem>
            <SelectItem value="SUBSCRIPTION">Subscription</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <PermissionGate permission={PERMISSIONS.COMMERCE_CATALOG_MANAGE}>
        {selected.size > 0 && (
          <div className="flex flex-wrap items-center gap-2 rounded-lg border border-border p-3">
            <span className="text-sm">{selected.size} selected</span>
            <Select value={bulkStatus} onValueChange={setBulkStatus}>
              <SelectTrigger className="w-full sm:w-[140px]" aria-label="Bulk product status">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="ACTIVE">Active</SelectItem>
                <SelectItem value="DRAFT">Draft</SelectItem>
                <SelectItem value="ARCHIVED">Archived</SelectItem>
              </SelectContent>
            </Select>
            <Button size="sm" disabled={bulkBusy} onClick={() => void applyBulk()}>
              {bulkBusy ? "Updating…" : "Apply"}
            </Button>
          </div>
        )}
      </PermissionGate>

      {query.isError && (
        <ErrorState message="Failed to load products" onRetry={() => void query.refetch()} />
      )}

      {filtered.length === 0 && !query.isLoading && (
        <EmptyState title="No products" description="Create a product to list it on the shop." />
      )}

      <ResponsiveTable
        mobile={filtered.map((p) => (
          <MobileCard key={p.id}>
            <p className="font-medium">{p.name}</p>
            <MobileCardRow label="Type" value={<Badge variant="secondary">{p.productType}</Badge>} />
            <MobileCardRow label="From" value={<Money amount={p.fromPriceMinor} />} />
            <Button variant="outline" size="sm" className="mt-3 w-full" asChild>
              <Link to={`/commerce/products/${p.id}`}>Edit</Link>
            </Button>
          </MobileCard>
        ))}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-10" />
              <TableHead>Name</TableHead>
              <TableHead>Type</TableHead>
              <TableHead>From price</TableHead>
              <TableHead>Slug</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {filtered.map((p) => (
              <TableRow key={p.id}>
                <TableCell>
                  <PermissionGate permission={PERMISSIONS.COMMERCE_CATALOG_MANAGE}>
                    <input
                      type="checkbox"
                      checked={selected.has(p.id)}
                      onChange={() => toggle(p.id)}
                      aria-label={`Select ${p.name}`}
                    />
                  </PermissionGate>
                </TableCell>
                <TableCell className="font-medium">{p.name}</TableCell>
                <TableCell>
                  <Badge variant="secondary">{p.productType}</Badge>
                </TableCell>
                <TableCell>
                  <Money amount={p.fromPriceMinor} />
                </TableCell>
                <TableCell className="font-mono text-xs">{p.slug}</TableCell>
                <TableCell>
                  <Button variant="ghost" size="sm" asChild>
                    <Link to={`/commerce/products/${p.id}`}>Edit</Link>
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </ResponsiveTable>

      {query.hasNextPage && (
        <Button
          variant="outline"
          onClick={() => void query.fetchNextPage()}
          disabled={query.isFetchingNextPage}
        >
          {query.isFetchingNextPage ? "Loading…" : "Load more"}
        </Button>
      )}
    </div>
  );
}
