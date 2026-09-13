import { Link, useNavigate, useParams } from "react-router";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { EmptyState } from "@/components/shared/states";
import { Money } from "@/components/shared/Money";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import {
  useCommerceProduct,
  useCreateCommerceProduct,
  useCreateVariant,
  useUpdateCommerceProduct,
  useUploadCommerceFile,
} from "@/features/commerce/api";
import { getApiErrorMessage } from "@/lib/api-client";
import { paiseToRupeesString, rupeesToPaise } from "@/lib/commerce-money";
import { PERMISSIONS } from "@/lib/permissions";
import type { ProductDetail } from "@/lib/schemas/commerce";

export default function ProductEditPage() {
  const { id } = useParams();
  const isNew = id === "new";
  const navigate = useNavigate();
  const productQuery = useCommerceProduct(isNew ? undefined : id);
  const createProduct = useCreateCommerceProduct();
  const updateProduct = useUpdateCommerceProduct(id ?? "");
  const createVariant = useCreateVariant(id ?? "");
  const uploadFile = useUploadCommerceFile();

  const [slug, setSlug] = useState("");
  const [name, setName] = useState("");
  const [tagline, setTagline] = useState("");
  const [description, setDescription] = useState("");
  const [productType, setProductType] = useState<string>("DIGITAL");
  const [status, setStatus] = useState("DRAFT");
  const [featured, setFeatured] = useState(false);
  const [hsnCode, setHsnCode] = useState("");
  const [seoTitle, setSeoTitle] = useState("");
  const [seoDescription, setSeoDescription] = useState("");
  const [heroImageFileId, setHeroImageFileId] = useState("");

  const [variantName, setVariantName] = useState("");
  const [variantSku, setVariantSku] = useState("");
  const [variantPrice, setVariantPrice] = useState("");
  const [variantCompare, setVariantCompare] = useState("");
  const [trackInventory, setTrackInventory] = useState(false);
  const [stockOnHand, setStockOnHand] = useState("");
  const [billingInterval, setBillingInterval] = useState("ONE_TIME");
  const [serviceDurationDays, setServiceDurationDays] = useState("");
  const [deliverySlaDays, setDeliverySlaDays] = useState("");
  const [downloadFileId, setDownloadFileId] = useState("");

  useEffect(() => {
    const p = productQuery.data;
    if (!p) return;
    setSlug(p.slug);
    setName(p.name);
    setTagline(p.tagline ?? "");
    setDescription(p.description ?? "");
    setProductType(p.productType);
    setStatus(p.status);
    setFeatured(p.featured);
    setHsnCode(p.hsnCode ?? "");
    setSeoTitle(p.seoTitle ?? "");
    setSeoDescription(p.seoDescription ?? "");
    setHeroImageFileId(p.heroImageFileId ?? "");
  }, [productQuery.data]);

  const saveProduct = async () => {
    try {
      if (isNew) {
        const created = await createProduct.mutateAsync({
          slug: slug.trim(),
          name: name.trim(),
          tagline: tagline.trim() || undefined,
          description: description.trim() || undefined,
          productType,
          hsnCode: hsnCode.trim() || undefined,
        });
        toast.success("Product created");
        void navigate(`/commerce/products/${created.id}`, { replace: true });
        return;
      }
      await updateProduct.mutateAsync({
        name: name.trim(),
        tagline: tagline.trim() || undefined,
        description: description.trim() || undefined,
        status,
        featured,
        hsnCode: hsnCode.trim() || undefined,
        seoTitle: seoTitle.trim() || undefined,
        seoDescription: seoDescription.trim() || undefined,
        heroImageFileId: heroImageFileId.trim() || undefined,
      });
      toast.success("Product saved");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const addVariant = async () => {
    if (isNew) {
      toast.error("Save the product first");
      return;
    }
    try {
      const body: Record<string, unknown> = {
        name: variantName.trim(),
        sku: variantSku.trim(),
        priceMinor: rupeesToPaise(variantPrice),
        trackInventory,
      };
      if (variantCompare.trim()) {
        body.compareAtPriceMinor = rupeesToPaise(variantCompare);
      }
      if (trackInventory && stockOnHand.trim()) {
        body.stockOnHand = Number(stockOnHand);
      }
      if (productType === "SUBSCRIPTION") {
        body.billingInterval = billingInterval;
      }
      if (productType === "DIGITAL" && downloadFileId.trim()) {
        body.downloadFileId = downloadFileId.trim();
      }
      if (productType === "SERVICE" && serviceDurationDays.trim()) {
        body.serviceDurationDays = Number(serviceDurationDays);
      }
      if (productType === "PHYSICAL" && deliverySlaDays.trim()) {
        body.deliverySlaDays = Number(deliverySlaDays);
      }
      await createVariant.mutateAsync(body);
      toast.success("Variant added");
      setVariantName("");
      setVariantSku("");
      setVariantPrice("");
      void productQuery.refetch();
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const onHeroUpload = async (file: File) => {
    try {
      const result = await uploadFile.mutateAsync({ file, purpose: "LOGO" });
      setHeroImageFileId(result.id);
      toast.success("Image uploaded");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader
        title={isNew ? "New product" : (productQuery.data?.name ?? "Edit product")}
        description="Prices are entered in rupees and stored as integer paise."
        actions={
          <Button variant="outline" asChild>
            <Link to="/commerce/products">Back to products</Link>
          </Button>
        }
      />

      <PermissionGate
        permission={PERMISSIONS.COMMERCE_CATALOG_MANAGE}
        fallback={<p className="text-sm text-text-muted">Read-only access</p>}
      >
        <div className="grid gap-6 lg:grid-cols-2">
          <section className="space-y-4 rounded-lg border border-border p-4">
            <h2 className="font-medium">Basics</h2>
            {isNew && (
              <>
                <div className="space-y-2">
                  <Label htmlFor="slug">Slug</Label>
                  <Input id="slug" value={slug} onChange={(e) => setSlug(e.target.value)} />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="type">Product type</Label>
                  <Select value={productType} onValueChange={setProductType}>
                    <SelectTrigger id="type">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="PHYSICAL">Physical</SelectItem>
                      <SelectItem value="DIGITAL">Digital</SelectItem>
                      <SelectItem value="SERVICE">Service</SelectItem>
                      <SelectItem value="SUBSCRIPTION">Subscription</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </>
            )}
            <div className="space-y-2">
              <Label htmlFor="name">Name</Label>
              <Input id="name" value={name} onChange={(e) => setName(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="tagline">Tagline</Label>
              <Input id="tagline" value={tagline} onChange={(e) => setTagline(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">Description</Label>
              <Textarea id="description" rows={5} value={description} onChange={(e) => setDescription(e.target.value)} />
            </div>
            {!isNew && (
              <>
                <div className="space-y-2">
                  <Label htmlFor="status">Status</Label>
                  <Select value={status} onValueChange={setStatus}>
                    <SelectTrigger id="status">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="DRAFT">Draft</SelectItem>
                      <SelectItem value="ACTIVE">Active</SelectItem>
                      <SelectItem value="ARCHIVED">Archived</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="flex items-center gap-2">
                  <Switch id="featured" checked={featured} onCheckedChange={setFeatured} />
                  <Label htmlFor="featured">Featured on shop</Label>
                </div>
              </>
            )}
            <div className="space-y-2">
              <Label htmlFor="hsn">HSN code</Label>
              <Input id="hsn" value={hsnCode} onChange={(e) => setHsnCode(e.target.value)} />
            </div>
            <Button onClick={() => void saveProduct()} disabled={createProduct.isPending || updateProduct.isPending}>
              {isNew ? "Create product" : "Save changes"}
            </Button>
          </section>

          {!isNew && (
            <section className="space-y-4 rounded-lg border border-border p-4">
              <h2 className="font-medium">SEO & media</h2>
              <div className="space-y-2">
                <Label htmlFor="seoTitle">SEO title</Label>
                <Input id="seoTitle" value={seoTitle} onChange={(e) => setSeoTitle(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="seoDescription">SEO description</Label>
                <Textarea id="seoDescription" rows={3} value={seoDescription} onChange={(e) => setSeoDescription(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="heroId">Hero image file ID</Label>
                <Input id="heroId" value={heroImageFileId} onChange={(e) => setHeroImageFileId(e.target.value)} />
                <Input
                  type="file"
                  accept="image/*"
                  onChange={(e) => {
                    const f = e.target.files?.[0];
                    if (f) void onHeroUpload(f);
                  }}
                />
              </div>
            </section>
          )}
        </div>

        {!isNew && productQuery.data && (
          <section className="space-y-4 rounded-lg border border-border p-4">
            <h2 className="font-medium">Variants</h2>
            <VariantsList product={productQuery.data} />
            <div className="grid gap-4 border-t border-border pt-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="variant-name">Variant name</Label>
                <Input id="variant-name" value={variantName} onChange={(e) => setVariantName(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="variant-sku">SKU</Label>
                <Input id="variant-sku" value={variantSku} onChange={(e) => setVariantSku(e.target.value)} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="variant-price">Price (₹)</Label>
                <Input id="variant-price" inputMode="decimal" value={variantPrice} onChange={(e) => setVariantPrice(e.target.value)} placeholder="99.00" />
              </div>
              <div className="space-y-2">
                <Label htmlFor="variant-compare">Compare at (₹)</Label>
                <Input id="variant-compare" inputMode="decimal" value={variantCompare} onChange={(e) => setVariantCompare(e.target.value)} />
              </div>
              {productType === "PHYSICAL" && (
                <>
                  <div className="flex items-center gap-2">
                    <Switch id="track-inventory" checked={trackInventory} onCheckedChange={setTrackInventory} />
                    <Label htmlFor="track-inventory">Track inventory</Label>
                  </div>
                  {trackInventory && (
                    <div className="space-y-2">
                      <Label htmlFor="stock-on-hand">Stock on hand</Label>
                      <Input id="stock-on-hand" type="number" value={stockOnHand} onChange={(e) => setStockOnHand(e.target.value)} />
                    </div>
                  )}
                  <div className="space-y-2">
                    <Label htmlFor="delivery-sla">Delivery SLA (days)</Label>
                    <Input id="delivery-sla" type="number" value={deliverySlaDays} onChange={(e) => setDeliverySlaDays(e.target.value)} />
                  </div>
                </>
              )}
              {productType === "SUBSCRIPTION" && (
                <div className="space-y-2">
                  <Label htmlFor="billing-interval">Billing interval</Label>
                  <Select value={billingInterval} onValueChange={setBillingInterval}>
                    <SelectTrigger id="billing-interval" aria-label="Billing interval">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="MONTHLY">Monthly</SelectItem>
                      <SelectItem value="ANNUAL">Annual</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              )}
              {productType === "SERVICE" && (
                <div className="space-y-2">
                  <Label htmlFor="service-duration">Service duration (days)</Label>
                  <Input id="service-duration" type="number" value={serviceDurationDays} onChange={(e) => setServiceDurationDays(e.target.value)} />
                </div>
              )}
              {productType === "DIGITAL" && (
                <div className="space-y-2 md:col-span-2">
                  <Label htmlFor="download-file-id">Download file ID</Label>
                  <Input id="download-file-id" value={downloadFileId} onChange={(e) => setDownloadFileId(e.target.value)} />
                  <Input
                    type="file"
                    aria-label="Upload download file"
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) {
                        void uploadFile.mutateAsync({ file: f }).then((r) => {
                          setDownloadFileId(r.id);
                          toast.success("File uploaded");
                        });
                      }
                    }}
                  />
                </div>
              )}
              <div className="md:col-span-2">
                <Button variant="secondary" onClick={() => void addVariant()} disabled={createVariant.isPending}>
                  Add variant
                </Button>
              </div>
            </div>
          </section>
        )}
      </PermissionGate>
    </div>
  );
}

function VariantsList({ product }: { product: ProductDetail }) {
  if (product.variants.length === 0) {
    return <EmptyState title="No variants yet" description="Add a variant with a price to sell this product." />;
  }
  return (
    <ul className="divide-y divide-border rounded-lg border border-border text-sm">
      {product.variants.map((v) => (
        <li key={v.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
          <div>
            <p className="font-medium">{v.name}</p>
            <p className="font-mono text-xs text-text-muted">{v.sku}</p>
          </div>
          <div className="text-right">
            <Money amount={v.priceMinor} />
            {v.compareAtPriceMinor != null && (
              <p className="text-xs text-text-muted line-through">
                ₹{paiseToRupeesString(v.compareAtPriceMinor)}
              </p>
            )}
            {v.trackInventory && (
              <p className="text-xs text-text-muted">Stock: {v.stockAvailable ?? 0}</p>
            )}
          </div>
        </li>
      ))}
    </ul>
  );
}
