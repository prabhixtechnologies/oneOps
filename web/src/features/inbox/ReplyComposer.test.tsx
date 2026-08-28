import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ReplyComposer } from "@/features/inbox/ReplyComposer";

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
}));

vi.mock("@/lib/auth", () => ({
  useAuth: () => ({ permissions: [] }),
}));

vi.mock("@/features/ai/api", () => ({
  useAiStatus: () => ({ data: { enabled: true, configured: true, tokensUsedThisMonth: 0, monthlyQuota: 1000 } }),
  useMailAdaptCannedReply: () => ({ mutateAsync: vi.fn(), isPending: false }),
}));

vi.mock("@/features/ai/hooks/useStreamSuggest", () => ({
  useStreamSuggest: () => ({
    text: "",
    streaming: false,
    cancel: vi.fn(),
    startMailSuggest: vi.fn(),
  }),
}));

describe("ReplyComposer", () => {
  const baseProps = {
    threadId: "thread-1",
    threadSubject: "Support request",
    customerEmail: "customer@example.com",
    cannedReplies: [],
    isSending: false,
    isUploading: false,
  };

  it("passes uploaded attachment IDs to onSend", async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockResolvedValue(undefined);
    const onUpload = vi.fn().mockResolvedValue({ id: "file-abc", filename: "invoice.pdf" });

    render(<ReplyComposer {...baseProps} onSend={onSend} onUpload={onUpload} />);

    await user.type(screen.getByPlaceholderText(/write your reply/i), "Please see attached.");

    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(["pdf"], "invoice.pdf", { type: "application/pdf" });
    await user.upload(fileInput, file);

    await waitFor(() => {
      expect(screen.getByText("invoice.pdf")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: /send reply/i }));

    await waitFor(() => {
      expect(onSend).toHaveBeenCalledWith(
        expect.objectContaining({
          bodyHtml: "Please see attached.",
          attachmentIds: ["file-abc"],
          replyMode: "REPLY",
          to: ["customer@example.com"],
        }),
      );
    });
  });

  it("clears attachments after a successful send", async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockResolvedValue(undefined);
    const onUpload = vi.fn().mockResolvedValue({ id: "file-1", filename: "notes.txt" });

    render(<ReplyComposer {...baseProps} onSend={onSend} onUpload={onUpload} />);

    await user.type(screen.getByPlaceholderText(/write your reply/i), "Done");
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(fileInput, new File(["x"], "notes.txt", { type: "text/plain" }));

    await waitFor(() => expect(screen.getByText("notes.txt")).toBeInTheDocument());

    await user.click(screen.getByRole("button", { name: /send reply/i }));

    await waitFor(() => expect(onSend).toHaveBeenCalled());
    expect(screen.queryByText("notes.txt")).not.toBeInTheDocument();
  });

  it("allows removing a pending attachment before send", async () => {
    const user = userEvent.setup();
    const onSend = vi.fn().mockResolvedValue(undefined);
    const onUpload = vi.fn().mockResolvedValue({ id: "file-x", filename: "draft.docx" });

    render(<ReplyComposer {...baseProps} onSend={onSend} onUpload={onUpload} />);

    await user.type(screen.getByPlaceholderText(/write your reply/i), "Hello");
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    await user.upload(fileInput, new File(["x"], "draft.docx", { type: "application/vnd.openxmlformats-officedocument.wordprocessingml.document" }));

    await waitFor(() => expect(screen.getByText("draft.docx")).toBeInTheDocument());
    await user.click(screen.getByRole("button", { name: /remove draft\.docx/i }));
    expect(screen.queryByText("draft.docx")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /send reply/i }));

    await waitFor(() => {
      expect(onSend).toHaveBeenCalledWith(expect.objectContaining({ attachmentIds: [] }));
    });
  });
});
