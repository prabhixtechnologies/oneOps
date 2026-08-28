package com.prabhix.platform.chat.domain;

public final class ChatEnums {

    private ChatEnums() {
    }

    public enum ConversationStatus {
        OPEN, PENDING, RESOLVED, CLOSED
    }

    public enum Priority {
        LOW, NORMAL, HIGH, URGENT
    }

    public enum SenderType {
        VISITOR, AGENT, SYSTEM, NOTE, AI
    }

    public enum Availability {
        ONLINE, AWAY, OFFLINE
    }
}
