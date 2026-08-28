-- =============================================================================
-- V40  Align ai_prompts.temperature with its mapped Java type
--
-- V33 declared the column numeric(4,3) while AiPrompt maps it as a primitive
-- double, so Hibernate schema validation refused to start the application.
-- Temperature is a sampling knob handed straight to a provider API that takes a
-- float, so double precision is the honest type; the CHECK still bounds it.
-- =============================================================================

ALTER TABLE ai_prompts
    ALTER COLUMN temperature TYPE double precision USING temperature::double precision,
    ALTER COLUMN temperature SET DEFAULT 0.3;
