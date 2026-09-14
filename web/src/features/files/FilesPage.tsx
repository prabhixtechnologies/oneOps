import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { EmptyState, ErrorState } from "@/components/shared/states";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { useDeleteFile, useFiles, useUploadFile } from "@/features/files/api";
import { apiDownload, getApiErrorMessage, triggerBlobDownload } from "@/lib/api-client";
import { PERMISSIONS } from "@/lib/permissions";
import { formatBytes } from "@/lib/utils";

export default function FilesPage() {
  const files = useFiles();
  const upload = useUploadFile();
  const del = useDeleteFile();
  const rows = files.data?.pages.flatMap((p) => p.items) ?? [];

  const onUpload = async (file: File) => {
    try {
      const result = await upload.mutateAsync({ file, purpose: "DOCUMENT" });
      toast.success(`Uploaded ${result.filename}`);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const onDownload = async (id: string, fallbackName: string) => {
    try {
      const { blob, filename } = await apiDownload(`/files/${id}`);
      triggerBlobDownload(blob, filename || fallbackName);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const onDelete = async (id: string) => {
    try {
      await del.mutateAsync(id);
      toast.success("File deleted");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader title="Files" description="Upload, download, and manage organization files." />

      <PermissionGate permission={PERMISSIONS.FILE_UPLOAD}>
        <div
          className="rounded-lg border-2 border-dashed border-border p-8 text-center"
          onDragOver={(e) => e.preventDefault()}
          onDrop={(e) => {
            e.preventDefault();
            const f = e.dataTransfer.files[0];
            if (f) void onUpload(f);
          }}
        >
          <p className="text-sm text-text-muted">Drag and drop a file, or choose one</p>
          <Input
            type="file"
            className="mx-auto mt-4 max-w-xs"
            onChange={(e) => {
              const f = e.target.files?.[0];
              if (f) void onUpload(f);
            }}
          />
          {upload.isPending && <p className="mt-2 text-sm">Uploading…</p>}
        </div>
      </PermissionGate>

      {files.isPending ? (
        <div className="space-y-2">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      ) : files.isError ? (
        <ErrorState message="Failed to load files" onRetry={() => void files.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState title="No files yet" description="Upload a document to keep it with this organization." />
      ) : (
        <ul className="divide-y divide-border rounded-lg border border-border">
          {rows.map((file) => (
            <li
              key={file.id}
              className="flex flex-col gap-2 px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="min-w-0">
                <p className="truncate font-medium">{file.filename}</p>
                <p className="text-text-muted">
                  {formatBytes(file.sizeBytes)} · {file.purpose.toLowerCase()} · Scan: {file.scanStatus}
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                <PermissionGate permission={PERMISSIONS.FILE_READ}>
                  <Button variant="outline" size="sm" onClick={() => void onDownload(file.id, file.filename)}>
                    Download
                  </Button>
                </PermissionGate>
                <PermissionGate permission={PERMISSIONS.FILE_DELETE}>
                  <Button
                    variant="destructive"
                    size="sm"
                    disabled={del.isPending}
                    onClick={() => void onDelete(file.id)}
                  >
                    Delete
                  </Button>
                </PermissionGate>
              </div>
            </li>
          ))}
        </ul>
      )}

      {files.hasNextPage ? (
        <Button
          variant="outline"
          disabled={files.isFetchingNextPage}
          onClick={() => void files.fetchNextPage()}
        >
          {files.isFetchingNextPage ? "Loading…" : "Load more"}
        </Button>
      ) : null}
    </div>
  );
}
