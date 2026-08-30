import { useState } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/shared/PageHeader";
import { PermissionGate } from "@/components/shared/PermissionGate";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useDeleteFile, useUploadFile } from "@/features/files/api";
import { apiDownload, getApiErrorMessage, triggerBlobDownload } from "@/lib/api-client";
import { PERMISSIONS } from "@/lib/permissions";
import { formatBytes } from "@/lib/utils";

export default function FilesPage() {
  const [fileId, setFileId] = useState("");
  const upload = useUploadFile();
  const del = useDeleteFile();
  const [lastUpload, setLastUpload] = useState<{
    id: string;
    filename: string;
    sizeBytes: number;
    scanStatus: string;
  } | null>(null);

  const onUpload = async (file: File) => {
    try {
      const result = await upload.mutateAsync({ file, purpose: "DOCUMENT" });
      setLastUpload(result);
      setFileId(result.id);
      toast.success(`Uploaded ${result.filename}`);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const onDownload = async () => {
    if (!fileId) return;
    try {
      const { blob, filename } = await apiDownload(`/files/${fileId}`);
      triggerBlobDownload(blob, filename);
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  const onDelete = async () => {
    if (!fileId) return;
    try {
      await del.mutateAsync(fileId);
      toast.success("File deleted");
      setLastUpload(null);
      setFileId("");
    } catch (err) {
      toast.error(getApiErrorMessage(err));
    }
  };

  return (
    <div className="space-y-6 p-4 md:p-6">
      <PageHeader
        title="Files"
        description="Upload, download, and manage organization files. Browse-by-list is not available from the API yet — use upload or enter a file ID."
      />

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

      {lastUpload && (
        <div className="rounded-lg border border-border p-4 text-sm">
          <p className="font-medium">{lastUpload.filename}</p>
          <p className="text-text-muted">
            {formatBytes(lastUpload.sizeBytes)} · Scan: {lastUpload.scanStatus}
          </p>
          <p className="font-mono text-xs">{lastUpload.id}</p>
        </div>
      )}

      <div className="max-w-md space-y-4">
        <div className="space-y-2">
          <Label htmlFor="fileId">File ID</Label>
          <Input id="fileId" value={fileId} onChange={(e) => setFileId(e.target.value)} placeholder="UUID" />
        </div>
        <div className="flex flex-wrap gap-2">
          <PermissionGate permission={PERMISSIONS.FILE_READ}>
            <Button variant="outline" onClick={() => void onDownload()} disabled={!fileId}>
              Download
            </Button>
          </PermissionGate>
          <PermissionGate permission={PERMISSIONS.FILE_DELETE}>
            <Button variant="destructive" onClick={() => void onDelete()} disabled={!fileId || del.isPending}>
              Delete
            </Button>
          </PermissionGate>
        </div>
      </div>
    </div>
  );
}
