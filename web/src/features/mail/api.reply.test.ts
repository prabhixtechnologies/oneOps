import { describe, expect, it, vi, beforeEach } from "vitest";

const apiRequest = vi.fn();

vi.mock("@/lib/api-client", () => ({
  apiRequest: (...args: unknown[]) => apiRequest(...args),
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
  useMutation: (opts: { mutationFn: (...args: unknown[]) => unknown }) => ({
    mutateAsync: opts.mutationFn,
  }),
}));

describe("useReplyToThread payload", () => {
  beforeEach(() => {
    apiRequest.mockResolvedValue({});
  });

  it("includes attachmentIds and replyMode in the POST body", async () => {
    const { useReplyToThread } = await import("@/features/mail/api");
    const mutate = useReplyToThread().mutateAsync as (input: {
      threadId: string;
      to: string[];
      bodyHtml: string;
      attachmentIds: string[];
      replyMode: "REPLY_ALL";
    }) => Promise<unknown>;

    await mutate({
      threadId: "thread-1",
      to: ["a@example.com"],
      bodyHtml: "<p>Hi</p>",
      attachmentIds: ["file-1", "file-2"],
      replyMode: "REPLY_ALL",
    });

    expect(apiRequest).toHaveBeenCalledWith(
      "/mail/threads/thread-1/reply",
      expect.anything(),
      {
        method: "POST",
        body: {
          to: ["a@example.com"],
          bodyHtml: "<p>Hi</p>",
          cc: undefined,
          subject: undefined,
          replyMode: "REPLY_ALL",
          attachmentIds: ["file-1", "file-2"],
        },
      },
    );
  });
});
