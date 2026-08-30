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
  useQuery: vi.fn(),
  useInfiniteQuery: vi.fn(),
}));

describe("AI API mutations", () => {
  beforeEach(() => {
    apiRequest.mockResolvedValue({});
  });

  it("posts chat rewrite with draft and action", async () => {
    const { useChatRewrite } = await import("@/features/ai/api");
    const mutate = useChatRewrite().mutateAsync as (input: {
      conversationId: string;
      draft: string;
      action: string;
    }) => Promise<unknown>;

    await mutate({
      conversationId: "conv-1",
      draft: "hello there",
      action: "Shorten",
    });

    expect(apiRequest).toHaveBeenCalledWith(
      "/chat/conversations/conv-1/ai/rewrite",
      expect.anything(),
      {
        method: "POST",
        body: { draft: "hello there", action: "Shorten" },
      },
    );
  });
});
