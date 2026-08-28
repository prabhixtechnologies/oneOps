-- =============================================================================
-- V23  Chat automatic agent routing settings
-- =============================================================================

ALTER TABLE chat_settings
    ADD COLUMN IF NOT EXISTS auto_assign_enabled boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS max_concurrent_conversations integer NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS routing_cursor integer NOT NULL DEFAULT 0;

ALTER TABLE chat_settings
    ADD CONSTRAINT ck_chat_settings_max_concurrent
        CHECK (max_concurrent_conversations > 0 AND max_concurrent_conversations <= 50);
