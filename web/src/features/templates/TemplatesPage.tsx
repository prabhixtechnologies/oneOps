import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { SanitizedEmailHtml } from "@/components/shared/email-content";
import { MobileCard, MobileCardRow, ResponsiveTable } from "@/components/shared/ResponsiveTable";
import { ErrorState } from "@/components/shared/states";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  templateKey,
  usePreviewTemplate,
  useTemplate,
  useTemplates,
  useUpdateTemplate,
} from "@/features/mail/api";
import { getApiErrorMessage } from "@/lib/api-client";
import { PERMISSIONS } from "@/lib/permissions";

export default function TemplatesPage() {
  const { data, isLoading, isError, refetch } = useTemplates();

  if (isLoading) {
    return (
      <div className="p-6">
        <Skeleton className="h-64" />
      </div>
    );
  }
  if (isError || !data) {
    return <ErrorState message="Failed to load templates" onRetry={() => void refetch()} />;
  }

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader title="Email templates" description="Transactional templates for auth, billing, and notifications" />
      <ResponsiveTable
        mobile={data.map((t) => {
          const key = templateKey(t);
          return (
            <MobileCard key={key}>
              <p className="break-all font-mono text-xs font-medium">{key}</p>
              <MobileCardRow label="Name" value={t.name} />
              <MobileCardRow label="Locale" value={t.locale} />
              <Button variant="outline" size="sm" className="mt-3 w-full" asChild>
                <Link to={`/templates/${encodeURIComponent(key)}`}>Edit</Link>
              </Button>
            </MobileCard>
          );
        })}
      >
        <div className="overflow-x-auto rounded-lg border border-border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Key</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Locale</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.map((t) => {
                const key = templateKey(t);
                return (
                  <TableRow key={key}>
                    <TableCell className="max-w-[12rem] break-all font-mono text-xs">{key}</TableCell>
                    <TableCell>{t.name}</TableCell>
                    <TableCell>{t.locale}</TableCell>
                    <TableCell>
                      <Button variant="outline" size="sm" asChild>
                        <Link to={`/templates/${encodeURIComponent(key)}`}>Edit</Link>
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      </ResponsiveTable>
    </div>
  );
}

function variableName(v: { name: string; description?: string } | string): string {
  return typeof v === "string" ? v : v.name;
}

export function TemplateEditorPage() {
  const { key } = useParams<{ key: string }>();
  const decodedKey = key ? decodeURIComponent(key) : undefined;
  const { data, isLoading, isError, refetch } = useTemplate(decodedKey);
  const preview = usePreviewTemplate();
  const updateTemplate = useUpdateTemplate();
  const [subject, setSubject] = useState("");
  const [htmlBody, setHtmlBody] = useState("");
  const [textBody, setTextBody] = useState("");
  const [variables, setVariables] = useState<Record<string, string>>({});
  const [previewHtml, setPreviewHtml] = useState<string | null>(null);

  useEffect(() => {
    if (data) {
      setSubject(data.subject);
      setHtmlBody(data.htmlBody);
      setTextBody(data.textBody);
      const defaults: Record<string, string> = {};
      for (const v of data.variables ?? []) {
        const name = variableName(v);
        defaults[name] = name === "orgName" ? "Acme Corporation" : `Sample ${name}`;
      }
      setVariables(defaults);
    }
  }, [data]);

  const runPreview = async () => {
    if (!decodedKey) return;
    try {
      const result = await preview.mutateAsync({ key: decodedKey, variables });
      setPreviewHtml(result.htmlBody);
      toast.success("Preview rendered");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const save = async () => {
    if (!decodedKey) return;
    try {
      await updateTemplate.mutateAsync({
        key: decodedKey,
        data: { subject, htmlBody, textBody },
      });
      toast.success("Template saved");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  if (isLoading) {
    return (
      <div className="p-6">
        <Skeleton className="h-96" />
      </div>
    );
  }
  if (isError || !data) {
    return <ErrorState message="Failed to load template" onRetry={() => void refetch()} />;
  }

  return (
    <div className="space-y-6 p-4 pb-[calc(1rem+env(safe-area-inset-bottom))] md:p-6">
      <PageHeader title={data.name} description={data.key} />

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="space-y-4">
          <div className="space-y-2">
            <Label>Subject</Label>
            <Input value={subject} onChange={(e) => setSubject(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>HTML body</Label>
            <Textarea value={htmlBody} onChange={(e) => setHtmlBody(e.target.value)} className="min-h-[200px] font-mono text-xs" />
          </div>
          <div className="space-y-2">
            <Label>Text body</Label>
            <Textarea value={textBody} onChange={(e) => setTextBody(e.target.value)} className="min-h-[100px] font-mono text-xs" />
          </div>
          <div className="space-y-2">
            <Label>Preview variables</Label>
            {(data.variables ?? []).map((v) => {
              const name = variableName(v);
              return (
                <div key={name} className="flex flex-col gap-2 sm:flex-row sm:items-center">
                  <Badge variant="secondary" className="w-fit shrink-0">{name}</Badge>
                  <Input
                    className="min-w-0 flex-1"
                    value={variables[name] ?? ""}
                    onChange={(e) => setVariables((prev) => ({ ...prev, [name]: e.target.value }))}
                  />
                </div>
              );
            })}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => void runPreview()} disabled={preview.isPending}>
              Preview
            </Button>
            <PermissionGate permission={PERMISSIONS.MAIL_TEMPLATE_MANAGE}>
              <Button variant="secondary" onClick={() => void save()} disabled={updateTemplate.isPending}>
                Save
              </Button>
            </PermissionGate>
          </div>
        </div>
        <div>
          <Label className="mb-2 block">Live preview</Label>
          {previewHtml ? (
            <SanitizedEmailHtml html={previewHtml} />
          ) : (
            <div className="flex h-64 items-center justify-center rounded-lg border border-dashed border-border text-sm text-text-muted">
              Click Preview to render
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
