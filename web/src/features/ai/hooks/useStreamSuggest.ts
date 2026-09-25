import { useCallback, useEffect, useRef, useState } from "react";
import { connectAiSuggestStream } from "@/lib/sse";

export interface UseStreamSuggestOptions {
  onError?: (message: string) => void;
}

export function useStreamSuggest(options: UseStreamSuggestOptions = {}) {
  const [streaming, setStreaming] = useState(false);
  const [text, setText] = useState("");
  const cleanupRef = useRef<(() => void) | null>(null);
  const onErrorRef = useRef(options.onError);
  onErrorRef.current = options.onError;

  const cancel = useCallback(() => {
    cleanupRef.current?.();
    cleanupRef.current = null;
    setStreaming(false);
  }, []);

  const startChatSuggest = useCallback(
    (conversationId: string) => {
      cancel();
      setText("");
      setStreaming(true);
      cleanupRef.current = connectAiSuggestStream({
        path: `/oneops/ai/chat/conversations/reply/suggest/stream?conversationId=${conversationId}`,
        conversationId,
        onDelta: (delta, finished) => {
          if (delta) setText((prev) => prev + delta);
          if (finished) setStreaming(false);
        },
        onError: (message) => {
          onErrorRef.current?.(message);
          setStreaming(false);
        },
        onComplete: () => {
          cleanupRef.current = null;
          setStreaming(false);
        },
      });
    },
    [cancel],
  );

  useEffect(() => () => cancel(), [cancel]);

  return {
    text,
    streaming,
    setText,
    cancel,
    startChatSuggest,
  };
}
