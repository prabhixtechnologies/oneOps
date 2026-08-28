-- =============================================================================
-- V33  AI prompt templates (org-scoped with platform defaults)
-- =============================================================================

CREATE TABLE ai_prompts (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint      NOT NULL DEFAULT 0,
    organization_id     uuid,
    task_key            varchar(80) NOT NULL,
    name                varchar(160) NOT NULL,
    description         varchar(500),
    template            text        NOT NULL,
    -- Optional override; null falls back to prabhix.ai.default-provider
    provider            varchar(32),
    model               varchar(80),
    temperature         numeric(4, 3) NOT NULL DEFAULT 0.300,
    prompt_version      integer     NOT NULL DEFAULT 1,
    enabled             boolean     NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid,
    updated_by          uuid,

    CONSTRAINT uq_ai_prompts_org_task UNIQUE (organization_id, task_key),
    CONSTRAINT ck_ai_prompts_temperature CHECK (temperature >= 0 AND temperature <= 2),
    CONSTRAINT fk_ai_prompts_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
);

CREATE INDEX ix_ai_prompts_task ON ai_prompts (task_key) WHERE organization_id IS NULL;
CREATE INDEX ix_ai_prompts_org ON ai_prompts (organization_id) WHERE organization_id IS NOT NULL;

-- Platform default prompts. Variables use Thymeleaf ${...} syntax.
INSERT INTO ai_prompts (organization_id, task_key, name, description, template, temperature) VALUES
(NULL, 'mail.reply_suggest',
 'Suggest mail reply',
 'Draft a reply for agent review based on thread history.',
 'You are a helpful support agent for [[${orgName}]]. Draft a professional reply to the customer.
Subject: [[${subject}]]
Thread history:
[[${threadHistory}]]
Write only the reply body. Do not send automatically; the agent will review.',
 0.400),

(NULL, 'mail.thread_summarize',
 'Summarize mail thread',
 'Concise summary of a long email thread.',
 'Summarize this support email thread in 3-5 bullet points. Include open questions and next steps.
Subject: [[${subject}]]
Messages:
[[${threadHistory}]]',
 0.200),

(NULL, 'mail.thread_triage',
 'Triage mail thread',
 'Suggest tags, priority, and customer intent.',
 'Analyze this inbound support thread and respond with JSON only:
{"suggestedTags":["tag-slug"],"priority":"LOW|NORMAL|HIGH|URGENT","intent":"brief intent","confidence":0.0-1.0}
Available tags: [[${availableTags}]]
Subject: [[${subject}]]
Latest message:
[[${latestMessage}]]',
 0.100),

(NULL, 'mail.canned_reply_adapt',
 'Adapt canned reply',
 'Adapt a canned reply to thread context and tone.',
 'Adapt this canned reply template to fit the conversation context. Keep the core message but adjust tone.
Canned reply:
[[${cannedBody}]]
Thread context:
[[${threadHistory}]]
Write only the adapted reply body.',
 0.500),

(NULL, 'chat.reply_suggest',
 'Suggest chat reply',
 'Suggest a reply for a live-chat agent.',
 'You are a support agent for [[${orgName}]]. Suggest a concise, friendly chat reply.
Conversation:
[[${conversationHistory}]]
Suggest only the message text for the agent to send.',
 0.500),

(NULL, 'chat.message_rewrite',
 'Rewrite chat message',
 'Improve, shorten, or translate a drafted chat message.',
 '[[${action}]] this chat message for a customer support context. Output only the revised message.
Original:
[[${draft}]]',
 0.400),

(NULL, 'chat.handoff_summary',
 'Chat handoff summary',
 'Internal summary when transferring between agents.',
 'Write a brief internal handoff note for the next agent. Include customer issue, sentiment, and pending actions.
Conversation:
[[${conversationHistory}]]',
 0.200),

(NULL, 'chat.sentiment',
 'Chat sentiment analysis',
 'Sentiment and urgency signal for a conversation.',
 'Analyze this chat and respond with JSON only:
{"sentiment":"positive|neutral|negative|frustrated","urgency":"low|medium|high|critical","summary":"one sentence"}
Conversation:
[[${conversationHistory}]]',
 0.100),

(NULL, 'chat.first_responder',
 'Chat AI first responder',
 'Optional AI greeting when no agent is online. Must disclose AI and offer human handoff.',
 'You are an AI assistant for [[${orgName}]]. Start with a clear disclosure that you are AI.
Help briefly, then offer to connect to a human agent.
Visitor message: [[${visitorMessage}]]
Keep under 3 sentences.',
 0.600),

(NULL, 'lead.score_enrich',
 'Lead scoring and enrichment',
 'Score and summarize a marketing-site lead.',
 'Analyze this sales lead and respond with JSON only:
{"score":1-100,"priority":"low|medium|high","summary":"2-3 sentences","suggestedActions":["action"]}
Lead:
Name: [[${name}]]
Company: [[${company}]]
Interest: [[${interest}]]
Message: [[${message}]]',
 0.200),

(NULL, 'commerce.product_description',
 'Product description draft',
 'Draft a product catalog description.',
 'Write a compelling product description for an e-commerce catalog.
Product name: [[${name}]]
Tagline: [[${tagline}]]
Type: [[${productType}]]
Attributes: [[${attributes}]]
Write 2-3 paragraphs. Include SEO-friendly language.',
 0.600),

(NULL, 'assist.generic',
 'Generic AI assist',
 'Open-ended assist for future features.',
 '[[${instruction}]]
Context:
[[${context}]]',
 0.400);
