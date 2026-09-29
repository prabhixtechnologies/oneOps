import { Link, useNavigate } from "react-router";
import { ArrowDown, ArrowUp, ChevronsUpDown, Pencil, Plus, Star, StarOff } from "lucide-react";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import { useList, useUrlString } from "@prabhixtechnologies/ui";
import { useCommands } from "@/components/layout/CommandPalette";
import { PageHeader } from "@/components/shared/PageHeader";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { MobileCard, MobileCardRow, ResponsiveTable } from "@/components/shared/ResponsiveTable";
import { Money } from "@/components/shared/Money";
import { EmptyState, ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
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
import { useCommerceProducts } from "@/features/commerce/api";
import { apiRequest } from "@/lib/api-client";
import { getApiErrorMessage } from "@/lib/api-client";
import { productDetailSchema, type ProductSummary } from "@/lib/schemas/commerce";
import { useAuth } from "@/lib/auth";
import { PERMISSIONS, hasPermission } from "@/lib/permissions";

export default function CommerceProductsPage() {
  const navigate = useNavigate();
  const { permissions } = useAuth();
  const canManage = hasPermission(permissions, PERMISSIONS.COMMERCE_CATALOG_MANAGE);
  // Status and type are server-side filters, so they stay separate from the list engine and go
  // straight into the query. They live in the URL all the same: a catalogue narrowed to archived
  // digital products is a view worth linking to, and it used to vanish on refresh.
  const [status, setStatus] = useUrlString("status");
  const [type, setType] = useUrlString("type");
  const [bulkStatus, setBulkStatus] = useState("ACTIVE");
  const [bulkBusy, setBulkBusy] = useState(false);

  const query = useCommerceProducts({
    status: status || undefined,
    type: type || undefined,
  });

  const products = useMemo(
    () => query.data?.pages.flatMap((p) => p.items) ?? [],
    [query.data],
  );

  const list = useList<ProductSummary>({
    rows: products,
    getRowId: (p) => p.id,
    getSearchText: (p) => `${p.name} ${p.slug}`,
    sortAccessors: {
      name: (p) => p.name,
      productType: (p) => p.productType,
      fromPricePaise: (p) => p.fromPricePaise,
      slug: (p) => p.slug,
    },
    // The server already pages this, and "Load more" appends to the same array. Paging it a
    // second time on the client would hide rows that were just fetched, so the engine is used
    // for search, sort and selection only and `matched` is rendered whole.
    pageSize: Number.MAX_SAFE_INTEGER,
  });

  const { search, setSearch, selected, toggleRow, selectRange } = list;
  const filtered = list.matched;
  const toggle = (id: string) => toggleRow(id);

  const sortIcon = (column: string) =>
    list.sort?.column !== column ? (
      <ChevronsUpDown className="size-3.5 opacity-40" aria-hidden />
    ) : list.sort.direction === "asc" ? (
      <ArrowUp className="size-3.5" aria-hidden />
    ) : (
      <ArrowDown className="size-3.5" aria-hidden />
    );

  /**
   * A sortable header. `aria-sort` is what tells a screen reader the column is sortable and which
   * way it currently runs; without it the arrow is decoration only, which is what it was.
   */
  const sortable = (column: string, label: string) => (
    <TableHead
      aria-sort={
        list.sort?.column !== column
          ? "none"
          : list.sort.direction === "asc"
            ? "ascending"
            : "descending"
      }
    >
      <button
        type="button"
        onClick={() => list.toggleSort(column)}
        className="-mx-2 inline-flex items-center gap-1.5 rounded px-2 py-1 font-medium hover:text-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        {label}
        {sortIcon(column)}
      </button>
    </TableHead>
  );

  useCommands(
    [
      {
        id: "products:new",
        label: "New product",
        group: "Products",
        keywords: ["create", "add", "item", "sku"],
        icon: <Plus className="h-4 w-4" />,
        perform: () => void navigate("/commerce/products/new"),
      },
      ...["ACTIVE", "DRAFT", "ARCHIVED"].map((value) => ({
        id: `products:status:${value}`,
        label: `Show ${value.toLowerCase()} products`,
        group: "Products",
        keywords: ["filter", "status", value.toLowerCase()],
        perform: () => setStatus(value),
      })),
      {
        id: "products:clear",
        label: "Clear product filters",
        group: "Products",
        keywords: ["reset", "all", "show everything"],
        perform: () => {
          setStatus("");
          setType("");
          setSearch("");
        },
      },
    ],
    [navigate, setStatus, setType, setSearch],
  );

  // One list, handed to the desktop row and the mobile card. Writing it twice is how the two
  // drift: in Members, suspend had a pending state on one and not the other.
  //
  // Featuring is here and publishing is not, because the list payload carries `featured` and
  // not `status` — a menu cannot offer "Publish" when it does not know whether the product
  // already is. That belongs on the detail page, which the first item goes to.
  const productActions = (p: ProductSummary): RowAction[] =>
    verbs(
      { id: "edit", label: "Edit product", icon: <Pencil />, onSelect: () => void navigate(`/commerce/products/${p.id}`) },
      canManage && {
        id: "feature",
        label: p.featured ? "Remove from featured" : "Feature on the shop",
        icon: p.featured ? <StarOff /> : <Star />,
        onSelect: () => void setFeatured(p),
      },
      {
        id: "select",
        label: selected.has(p.id) ? "Deselect" : "Select",
        onSelect: () => toggle(p.id),
      },
      filterVerb("name", "Show only this product", p.name, setSearch),
      copyVerb("name", "Copy name", p.name),
      copyVerb("slug", "Copy slug", p.slug),
      copyVerb("id", "Copy product ID", p.id),
    );

  const setFeatured = async (p: ProductSummary) => {
    try {
      await apiRequest(`/oneops/commerce/products?id=${p.id}`, productDetailSchema, {
        method: "PUT",
        body: { featured: !p.featured },
      });
      toast.success(p.featured ? `${p.name} is no longer featured` : `${p.name} is now featured`);
      void query.refetch();
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const applyBulk = async () => {
    if (selected.size === 0) return;
    setBulkBusy(true);
    try {
      for (const id of selected) {
        await apiRequest(`/oneops/commerce/products?id=${id}`, productDetailSchema, {
          method: "PUT",
          body: { status: bulkStatus },
        });
      }
      toast.success(`Updated ${selected.size} product(s)`);
      list.clearSelection();
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
          <div className="flex flex-wrap items-center gap-2 rounded-lg border border-accent-subtle-border bg-accent-subtle p-3 text-accent-subtle-ink">
            <span className="text-sm font-medium">
              {selected.size} of {filtered.length} selected
            </span>
            <Button variant="ghost" size="sm" onClick={list.clearSelection}>
              Clear
            </Button>
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

      {/* Two different situations that used to share one message. "No products" under a search
          that matched nothing reads as "the catalogue is empty", and the fix it suggests -
          create a product - is the wrong one. */}
      {filtered.length === 0 && !query.isLoading && (
        list.emptyBecauseFiltered || status || type ? (
          <EmptyState
            title="No products match"
            description="Nothing here fits the current search and filters."
            action={{
              label: "Clear filters",
              onClick: () => {
                setSearch("");
                setStatus("");
                setType("");
              },
            }}
          />
        ) : (
          <EmptyState
            title="No products"
            description={
              canManage
                ? "Create a product to list it on the shop."
                : "Nothing is listed yet. Someone with catalogue access can add the first one."
            }
            // Only offered to someone who can act on it. A button that leads to a permission
            // denial is worse than no button, because it costs a click to learn the same thing.
            action={
              canManage
                ? { label: "New product", onClick: () => navigate("/commerce/products/new") }
                : undefined
            }
          />
        )
      )}

      <ResponsiveTable
        mobile={filtered.map((p) => (
          <RowActions key={p.id} actions={productActions(p)} label={p.name}>
            <MobileCard>
              <div className="flex items-start justify-between gap-2">
                <p className="min-w-0 font-medium">{p.name}</p>
                <RowActionsTrigger />
              </div>
              <MobileCardRow label="Type" value={<Badge variant="secondary">{p.productType}</Badge>} />
              <MobileCardRow label="From" value={<Money amount={p.fromPricePaise} />} />
              <Button variant="outline" size="sm" className="mt-3 w-full" asChild>
                <Link to={`/commerce/products/${p.id}`}>Edit</Link>
              </Button>
            </MobileCard>
          </RowActions>
        ))}
      >
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-10">
                <PermissionGate permission={PERMISSIONS.COMMERCE_CATALOG_MANAGE}>
                  {/* There was no way to select everything at all: the only route to a bulk
                      change was ticking rows one at a time. */}
                  <Checkbox
                    checked={list.allSelected}
                    onCheckedChange={() => list.toggleAll()}
                    aria-label={
                      list.allSelected === true
                        ? "Deselect all products"
                        : `Select all ${filtered.length} products`
                    }
                  />
                </PermissionGate>
              </TableHead>
              {sortable("name", "Name")}
              {sortable("productType", "Type")}
              {sortable("fromPricePaise", "From price")}
              {sortable("slug", "Slug")}
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {filtered.map((p) => (
              // `asChild` on the context-menu trigger inside RowActions, so this stays a plain
              // `<tr>` — a wrapping element here would not be valid inside `<tbody>`.
              <RowActions key={p.id} actions={productActions(p)} label={p.name}>
                <TableRow>
                <TableCell>
                  <PermissionGate permission={PERMISSIONS.COMMERCE_CATALOG_MANAGE}>
                    {/* Shift-click selects the run between this row and the last one touched.
                        Handled on click rather than on change because the change event does
                        not carry the modifier keys. */}
                    <Checkbox
                      checked={selected.has(p.id)}
                      onClick={(event) => {
                        if (!event.shiftKey) return;
                        event.preventDefault();
                        selectRange(p.id);
                      }}
                      onCheckedChange={() => toggle(p.id)}
                      aria-label={`Select ${p.name}`}
                    />
                  </PermissionGate>
                </TableCell>
                <TableCell className="font-medium">{p.name}</TableCell>
                <TableCell>
                  <Badge variant="secondary">{p.productType}</Badge>
                </TableCell>
                <TableCell>
                  <Money amount={p.fromPricePaise} />
                </TableCell>
                <TableCell className="font-mono text-xs">{p.slug}</TableCell>
                <TableCell>
                  <div className="flex items-center justify-end gap-1">
                    <Button variant="ghost" size="sm" asChild>
                      <Link to={`/commerce/products/${p.id}`}>Edit</Link>
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
