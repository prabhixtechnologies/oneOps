import { Check, Copy, Plus } from "lucide-react";
import { useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { ErrorState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { copyToClipboard } from "@/lib/utils";
import { useCreateDomain, useDomainDns, useDomains, useVerifyDomain } from "@/features/mail/api";
import { MobileCard, MobileCardRow, ResponsiveTable } from "@/components/shared/ResponsiveTable";

export default function DomainsPage() {
  const { data, isLoading, isError, refetch } = useDomains();
  const createDomain = useCreateDomain();
  const [newDomain, setNewDomain] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);

  const onAdd = async () => {
    await createDomain.mutateAsync(newDomain);
    toast.success("Domain added");
    setDialogOpen(false);
    setNewDomain("");
  };

  if (isLoading) return <div className="p-6"><Skeleton className="h-64" /></div>;
  if (isError || !data) return <ErrorState message="Failed to load domains" onRetry={() => void refetch()} />;

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title="Mail domains"
        description="Configure DNS for inbound email on your custom domains"
        actions={
          <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
            <DialogTrigger asChild>
              <Button><Plus className="h-4 w-4" /> Add domain</Button>
            </DialogTrigger>
            <DialogContent>
              <DialogHeader><DialogTitle>Add mail domain</DialogTitle></DialogHeader>
              <div className="space-y-2">
                <Label htmlFor="domain">Domain name</Label>
                <Input id="domain" placeholder="support.yourcompany.com" value={newDomain} onChange={(e) => setNewDomain(e.target.value)} />
              </div>
              <DialogFooter>
                <Button onClick={() => void onAdd()} disabled={!newDomain}>Add domain</Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        }
      />

      <ResponsiveTable
        mobile={data.map((d) => (
          <MobileCard key={d.id}>
            <p className="font-medium">{d.domain}</p>
            <MobileCardRow
              label="Status"
              value={
                <Badge variant={d.status === "VERIFIED" ? "success" : "warning"}>
                  {d.status ?? (d.verified ? "VERIFIED" : "PENDING")}
                </Badge>
              }
            />
            <MobileCardRow
              label="Added"
              value={d.createdAt ? new Date(d.createdAt).toLocaleDateString() : "—"}
            />
            <Button variant="outline" size="sm" className="mt-3 w-full" asChild>
              <Link to={`/domains/${d.id}`}>DNS setup</Link>
            </Button>
          </MobileCard>
        ))}
      >
        <div className="rounded-lg border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Domain</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Added</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.map((d) => (
                <TableRow key={d.id}>
                  <TableCell className="font-medium">{d.domain}</TableCell>
                  <TableCell>
                    <Badge variant={d.status === "VERIFIED" ? "success" : "warning"}>
                      {d.status ?? (d.verified ? "VERIFIED" : "PENDING")}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-text-muted">
                    {d.createdAt ? new Date(d.createdAt).toLocaleDateString() : "—"}
                  </TableCell>
                  <TableCell>
                    <Button variant="outline" size="sm" asChild>
                      <Link to={`/domains/${d.id}`}>DNS setup</Link>
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </ResponsiveTable>
    </div>
  );
}

export function DomainDnsPage() {
  const { id } = useParams<{ id: string }>();
  const { data, isLoading, isError, refetch } = useDomainDns(id);
  const verify = useVerifyDomain();
  const [copied, setCopied] = useState<string | null>(null);

  const copy = async (text: string, key: string) => {
    await copyToClipboard(text);
    setCopied(key);
    toast.success("Copied to clipboard");
    setTimeout(() => setCopied(null), 2000);
  };

  if (isLoading) return <div className="p-6"><Skeleton className="h-64" /></div>;
  if (isError || !data) return <ErrorState message="Failed to load DNS records" onRetry={() => void refetch()} />;

  return (
    <div className="space-y-6 p-6">
      <PageHeader
        title={`DNS setup — ${data.domain}`}
        description="Publish these records with your DNS provider. Verification runs automatically every hour."
        actions={
          <Button onClick={() => id && void verify.mutateAsync(id)} disabled={verify.isPending || !id}>
            Verify now
          </Button>
        }
      />

      <div className="rounded-lg border border-border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Type</TableHead>
              <TableHead>Host</TableHead>
              <TableHead>Value</TableHead>
              <TableHead>Status</TableHead>
              <TableHead />
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.records.map((rec, i) => {
              const key = `${rec.type}-${i}`;
              return (
                <TableRow key={key}>
                  <TableCell className="font-mono text-xs">{rec.type}</TableCell>
                  <TableCell className="max-w-[200px] truncate font-mono text-xs">{rec.host}</TableCell>
                  <TableCell className="max-w-[300px] truncate font-mono text-xs">{rec.value}</TableCell>
                  <TableCell>
                    <Badge variant={rec.status === "VERIFIED" ? "success" : rec.status === "FAILED" ? "destructive" : "warning"}>
                      {rec.status}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Button variant="ghost" size="icon" onClick={() => void copy(rec.value, key)} aria-label="Copy value">
                      {copied === key ? <Check className="h-4 w-4 text-success" /> : <Copy className="h-4 w-4" />}
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
