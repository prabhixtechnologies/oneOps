import { API_V1 } from "./config";
import type { AiStreamEvent } from "./schemas/ai";
import type { ChatStreamEvent } from "./schemas/chat";
import { aiStreamEventSchema } from "./schemas/ai";
import { getAccessToken, getOrgId } from "./auth-token-bridge";

export type ChatStreamListener = (event: ChatStreamEvent) => void;
export type AiStreamListener = (event: AiStreamEvent) => void;

export type StreamConnectionState = "connecting" | "connected" | "disconnected" | "error";

interface StreamOptions<T> {
  path: string;
  onEvent: (event: T) => void;
  onError?: (error: Error) => void;
  onStateChange?: (state: StreamConnectionState) => void;
  reconnectMs?: number;
  /** When false, the stream closes after the first disconnect instead of reconnecting. */
  reconnect?: boolean;
  parse?: (raw: unknown) => T | null;
}

function connectEventStream<T>({
  path,
  onEvent,
  onError,
  onStateChange,
  reconnectMs = 5000,
  reconnect = true,
  parse,
}: StreamOptions<T>): () => void {
  const controller = new AbortController();
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  let stopped = false;

  const setState = (state: StreamConnectionState) => onStateChange?.(state);

  const run = async () => {
    if (stopped) return;
    setState("connecting");

    try {
      const token = getAccessToken();
      const orgId = getOrgId();

      const response = await fetch(`${API_V1}${path}`, {
        headers: {
          Authorization: token ? `Bearer ${token}` : "",
          "X-Prabhix-Org": orgId ?? "",
          Accept: "text/event-stream",
        },
        signal: controller.signal,
      });

      if (!response.ok || !response.body) {
        throw new Error(`Failed to connect to ${path}`);
      }

      setState("connected");
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";

      while (!stopped) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() ?? "";
        for (const line of lines) {
          if (line.startsWith("data: ")) {
            try {
              const raw: unknown = JSON.parse(line.slice(6));
              const event = parse ? parse(raw) : (raw as T);
              if (event != null) onEvent(event);
            } catch {
              // ignore malformed events
            }
          }
        }
      }

      if (!stopped && reconnect) {
        setState("disconnected");
        reconnectTimer = setTimeout(() => void run(), reconnectMs);
      } else if (!stopped) {
        setState("disconnected");
      }
    } catch (err) {
      if (controller.signal.aborted || stopped) return;
      setState("error");
      if (onError && err instanceof Error) onError(err);
      if (reconnect) {
        reconnectTimer = setTimeout(() => void run(), reconnectMs);
      }
    }
  };

  void run();

  return () => {
    stopped = true;
    controller.abort();
    if (reconnectTimer) clearTimeout(reconnectTimer);
    setState("disconnected");
  };
}

function parseAiStreamEvent(raw: unknown): AiStreamEvent | null {
  const parsed = aiStreamEventSchema.safeParse(raw);
  return parsed.success ? parsed.data : null;
}

export function connectChatStream(
  onEvent: ChatStreamListener,
  onError?: (error: Error) => void,
  onStateChange?: (state: StreamConnectionState) => void,
): () => void {
  return connectEventStream<ChatStreamEvent>({
    path: "/chat/stream",
    onEvent,
    onError,
    onStateChange,
  });
}

export function connectAiStream(
  onEvent: AiStreamListener,
  onError?: (error: Error) => void,
  onStateChange?: (state: StreamConnectionState) => void,
): () => void {
  return connectEventStream<AiStreamEvent>({
    path: "/ai/stream",
    onEvent,
    onError,
    onStateChange,
    parse: parseAiStreamEvent,
  });
}

export interface AiSuggestStreamOptions {
  path: string;
  threadId?: string;
  conversationId?: string;
  onDelta: (delta: string, finished: boolean) => void;
  onError: (message: string) => void;
  onComplete?: () => void;
}

/** One-shot SSE for progressive reply suggestions; does not reconnect after completion. */
export function connectAiSuggestStream({
  path,
  threadId,
  conversationId,
  onDelta,
  onError,
  onComplete,
}: AiSuggestStreamOptions): () => void {
  let disconnect: (() => void) | null = null;
  let settled = false;

  const stop = () => {
    disconnect?.();
    disconnect = null;
  };

  const finish = () => {
    if (settled) return;
    settled = true;
    stop();
    onComplete?.();
  };

  disconnect = connectEventStream<AiStreamEvent>({
    path,
    reconnect: false,
    parse: parseAiStreamEvent,
    onEvent: (event) => {
      if (threadId && event.threadId && event.threadId !== threadId) return;
      if (conversationId && event.conversationId && event.conversationId !== conversationId) return;

      if (event.type === "ai.error") {
        onError(event.payload.message ?? "AI suggestion failed");
        finish();
        return;
      }

      if (event.type === "ai.delta") {
        const delta = event.payload.delta ?? "";
        const isFinished = event.payload.finished === true;
        if (delta) onDelta(delta, isFinished);
        if (isFinished) finish();
      }
    },
    onError: (err) => {
      if (!settled) {
        onError(err.message);
        finish();
      }
    },
  });

  return stop;
}
