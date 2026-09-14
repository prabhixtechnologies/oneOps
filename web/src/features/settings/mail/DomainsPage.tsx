import { useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AlertTriangle, Globe, Plus, Trash2 } from "lucide-react";
import { z } from "zod";
import { EmptyState } from "@/components/shared/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { apiRequest, apiRequestVoid, getApiErrorMessage } from "@/lib/api-client";
import { PERMISSIONS } from "@/lib/permissions";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { ConfirmDialog } from "./ConfirmDialog";
import { adminErrorHint } from "./MailSettingsLayout";

const domainModes = ["SELF_HOSTED", "EXTERNAL_IMAP", "RELAY_ONLY"] as const;

const domainSchema = z.object({
  id: z.string(),
  domain: z.string(),
  status: z.string(),
  mode: z.enum(domainModes),
  isDefault: z.boolean(),
  mxVerifiedAt: z.string().nullish(),
  spfVerifiedAt: z.string().nullish(),
  dkimVerifiedAt: z.string().nullish(),
  dmarcVerifiedAt: z.string().nullish(),
  ownershipVerifiedAt: z.string().nullish(),
});

const dnsRecordSchema = z.object({
  name: z.string(),
  type: z.string(),
  expected: z.string(),
  observed: z.string().nullish(),
  status: z.string(),
});

const dnsReportSchema = z.object({
  domainId: z.string(),
  records: z.array(dnsRecordSchema),
  domainStatus: z.string(),
});

type MailDomain = z.infer<typeof domainSchema>;

const domainKeys = {
  all: ["mail-domains"] as const,
  dns: (id: string) => ["mail-domains", "dns", id] as const,
};

function modeLabel(mode: MailDomain["mode"]): string {
  if (mode === "SELF_HOSTED") return "Self-hosted";
  if (mode === "EXTERNAL_IMAP") return "External IMAP";
  return "Relay only";
}

export default function DomainsPage() {
  const queryClient = useQueryClient();
  const domains = useQuery({
    queryKey: domainKeys.all,
    queryFn: () => apiRequest("/mail/domains", z.array(domainSchema)),
  });
  const create = useMutation({
    mutationFn: (vars: { domain: string; mode: MailDomain["mode"] }) =>
      apiRequest("/mail/domains", domainSchema, { body: vars }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: domainKeys.all }),
  });
  const remove = useMutation({
    mutationFn: (id: string) => apiRequestVoid(`/mail/domains/${id}`, { method: "DELETE" }),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: domainKeys.all }),
  });

  const [showCreate, setShowCreate] = useState(false);
  const [domain, setDomain] = useState("");
  const [mode, setMode] = useState<MailDomain["mode"]>("SELF_HOSTED");
  const [error, setError] = useState<string>();
  const [confirmDelete, setConfirmDelete] = useState<MailDomain>();
  const [expandedId, setExpandedId] = useState<string>();

  const submitCreate = async (e: FormEvent) => {
    e.preventDefault();
    setError(undefined);
    try {
      await create.mutateAsync({ domain: domain.trim().toLowerCase(), mode });
      setShowCreate(false);
      setDomain("");
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  const doDelete = async () => {
    if (!confirmDelete) return;
    setError(undefined);
    try {
      await remove.mutateAsync(confirmDelete.id);
      setConfirmDelete(undefined);
    } catch (err) {
      setError(getApiErrorMessage(err));
    }
  };

  return (
    <div className="mx-auto max-w-3xl p-6">
      <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="font-display text-xl font-semibold tracking-tight">Domains</h1>
          <p className="mt-1 text-sm text-text-muted">
            Sending domains, DNS records, and DKIM. Mailboxes on these domains send as your brand.
          </p>
        </div>
        <PermissionGate permission={PERMISSIONS.MAIL_DOMAIN_MANAGE}>
          <Button size="sm" onClick={() => setShowCreate((v) => !v)}>
            <Plus className="size-4" />
            Add domain
          </Button>
        </PermissionGate>
      </div>

      {error ? (
        <p className="mb-4 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {error}
        </p>
      ) : null}

      {showCreate ? (
        <form
          onSubmit={(e) => void submitCreate(e)}
          className="mb-6 space-y-3 rounded-lg border border-border p-4"
        >
          <h2 className="text-sm font-medium">New sending domain</h2>
          <div>
            <label className="mb-1 block text-xs text-text-muted" htmlFor="mail-domain">
              Domain
            </label>
            <Input
              id="mail-domain"
              value={domain}
              onChange={(e) => setDomain(e.target.value)}
              placeholder="mail.yourcompany.com"
              required
            />
          </div>
          <div>
            <label className="mb-1 block text-xs text-text-muted" htmlFor="mail-domain-mode">
              Mode
            </label>
            <select
              id="mail-domain-mode"
              value={mode}
              onChange={(e) => setMode(e.target.value as MailDomain["mode"])}
              className="w-full rounded-md border border-border bg-surface px-3 py-2 text-sm"
            >
              {domainModes.map((m) => (
                <option key={m} value={m}>
                  {modeLabel(m)}
                </option>
              ))}
            </select>
          </div>
          <div className="flex gap-2">
            <Button type="submit" disabled={create.isPending}>
              {create.isPending ? "Adding…" : "Add"}
            </Button>
            <Button type="button" variant="outline" onClick={() => setShowCreate(false)}>
              Cancel
            </Button>
          </div>
        </form>
      ) : null}

      {domains.isPending ? (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : domains.isError ? (
        <EmptyState
          icon={<AlertTriangle className="size-8" />}
          title="Domains could not be loaded"
          description={adminErrorHint(domains.error) ?? getApiErrorMessage(domains.error)}
        />
      ) : (domains.data ?? []).length === 0 ? (
        <EmptyState
          icon={<Globe className="size-8" />}
          title="No sending domains yet"
          description="Add a domain to publish MX, SPF, DKIM and DMARC records."
        />
      ) : (
        <ul className="divide-y divide-border rounded-lg border border-border">
          {(domains.data ?? []).map((row) => (
            <li key={row.id} className="px-4 py-3">
              <div className="flex items-center gap-3">
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium">{row.domain}</div>
                  <div className="truncate text-xs text-text-muted">{modeLabel(row.mode)}</div>
                </div>
                <Badge variant={row.status === "VERIFIED" ? "success" : "secondary"}>
                  {row.status.toLowerCase()}
                </Badge>
                {row.isDefault ? <Badge variant="outline">Default</Badge> : null}
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setExpandedId((id) => (id === row.id ? undefined : row.id))}
                >
                  DNS
                </Button>
                <PermissionGate permission={PERMISSIONS.MAIL_DOMAIN_MANAGE}>
                  <Button
                    variant="ghost"
                    size="icon"
                    aria-label={`Delete ${row.domain}`}
                    onClick={() => setConfirmDelete(row)}
                  >
                    <Trash2 className="size-4 text-destructive" />
                  </Button>
                </PermissionGate>
              </div>
              {expandedId === row.id ? <DomainDns domainId={row.id} /> : null}
            </li>
          ))}
        </ul>
      )}

      {confirmDelete ? (
        <ConfirmDialog
          title={`Remove ${confirmDelete.domain}?`}
          hint="Mail from this domain will stop authenticating until you add it again."
          busy={remove.isPending}
          onCancel={() => setConfirmDelete(undefined)}
          onConfirm={() => void doDelete()}
        />
      ) : null}
    </div>
  );
}

function DomainDns({ domainId }: { domainId: string }) {
  const queryClient = useQueryClient();
  const dns = useQuery({
    queryKey: domainKeys.dns(domainId),
    queryFn: () => apiRequest(`/mail/domains/${domainId}/dns`, dnsReportSchema),
  });
  const verify = useMutation({
    mutationFn: () =>
      apiRequest(`/mail/domains/${domainId}/verify`, dnsReportSchema, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: domainKeys.dns(domainId) });
      void queryClient.invalidateQueries({ queryKey: domainKeys.all });
    },
  });

  if (dns.isPending) {
    return <Skeleton className="mt-3 h-24 w-full" />;
  }
  if (dns.isError || !dns.data) {
    return (
      <p className="mt-3 text-xs text-destructive">
        {adminErrorHint(dns.error) ?? getApiErrorMessage(dns.error)}
      </p>
    );
  }

  return (
    <div className="mt-3 space-y-2">
      <div className="flex items-center justify-between">
        <p className="text-xs text-text-muted">
          Publish these records, then verify. DKIM is the CNAME or TXT whose value starts with
          v=DKIM1.
        </p>
        <PermissionGate permission={PERMISSIONS.MAIL_DOMAIN_MANAGE}>
          <Button
            size="sm"
            variant="outline"
            disabled={verify.isPending}
            onClick={() => void verify.mutateAsync().catch(() => undefined)}
          >
            {verify.isPending ? "Checking…" : "Verify DNS"}
          </Button>
        </PermissionGate>
      </div>
      <div className="overflow-x-auto rounded-md border border-border">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-border text-text-muted">
              <th className="px-2 py-1.5 font-medium">Type</th>
              <th className="px-2 py-1.5 font-medium">Name</th>
              <th className="px-2 py-1.5 font-medium">Expected</th>
              <th className="px-2 py-1.5 font-medium">Status</th>
            </tr>
          </thead>
          <tbody>
            {dns.data.records.map((record) => (
              <tr key={`${record.type}-${record.name}`} className="border-b border-border/60">
                <td className="px-2 py-1.5 font-mono">{record.type}</td>
                <td className="px-2 py-1.5 font-mono">{record.name}</td>
                <td className="max-w-[16rem] truncate px-2 py-1.5 font-mono" title={record.expected}>
                  {record.expected}
                </td>
                <td className="px-2 py-1.5">{record.status}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
