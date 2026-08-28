import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MessageBubble } from "@/features/chat/MessageBubble";
import type { ChatMessage } from "@/lib/schemas/chat";

const baseMessage = {
  id: "msg-1",
  body: "Hello there",
  occurredAt: "2025-01-01T12:00:00Z",
  fileId: null,
} satisfies Partial<ChatMessage>;

describe("MessageBubble", () => {
  it("renders internal notes with private styling, never as visitor-visible replies", () => {
    render(
      <MessageBubble
        message={{ ...baseMessage, senderType: "NOTE" } as ChatMessage}
        visitorLabel="Visitor"
        agentLabel="Agent"
      />,
    );

    expect(screen.getByLabelText(/internal note — not visible to visitor/i)).toBeInTheDocument();
    expect(screen.getByText(/PRIVATE — Internal note/i)).toBeInTheDocument();
    expect(screen.queryByText("Visitor")).not.toBeInTheDocument();
    expect(screen.queryByText("Agent reply")).not.toBeInTheDocument();
  });

  it("renders agent replies as visitor-visible with agent badge", () => {
    render(
      <MessageBubble
        message={{ ...baseMessage, senderType: "AGENT" } as ChatMessage}
        visitorLabel="Visitor"
        agentLabel="Agent"
      />,
    );

    expect(screen.getByText("Agent reply")).toBeInTheDocument();
    expect(screen.queryByText(/PRIVATE — Internal note/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/internal note/i)).not.toBeInTheDocument();
  });

  it("renders visitor messages distinctly from internal notes", () => {
    render(
      <MessageBubble
        message={{ ...baseMessage, senderType: "VISITOR" } as ChatMessage}
        visitorLabel="Jane"
        agentLabel="Agent"
      />,
    );

    expect(screen.getByText("Visitor")).toBeInTheDocument();
    expect(screen.queryByText(/PRIVATE — Internal note/i)).not.toBeInTheDocument();
  });
});
