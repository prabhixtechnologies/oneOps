-- AI first-responder messages are attributed distinctly from SYSTEM automation.
ALTER TABLE chat_messages DROP CONSTRAINT IF EXISTS ck_chat_messages_sender;
ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_sender
    CHECK (sender_type IN ('VISITOR', 'AGENT', 'SYSTEM', 'NOTE', 'AI'));
