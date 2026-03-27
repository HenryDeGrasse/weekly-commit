-- =============================================================================
-- Weekly Commit Module — V12: Add prompt_version and eval scores to ai_suggestion
-- Enables A/B testing of prompt templates and production faithfulness scoring.
-- =============================================================================

ALTER TABLE ai_suggestion
    ADD COLUMN prompt_version          TEXT,
    ADD COLUMN eval_faithfulness_score  REAL,
    ADD COLUMN eval_relevancy_score     REAL,
    ADD COLUMN eval_scored_at           TIMESTAMPTZ;

CREATE INDEX idx_ai_suggestion_prompt_version
    ON ai_suggestion (prompt_version, suggestion_type);

-- Composite index for A/B analysis queries joining feedback
CREATE INDEX idx_ai_suggestion_ab_analysis
    ON ai_suggestion (suggestion_type, prompt_version, created_at);

COMMENT ON COLUMN ai_suggestion.prompt_version IS 'Prompt template version identifier (e.g. "commit-draft-assist-v1") for A/B analysis';
COMMENT ON COLUMN ai_suggestion.eval_faithfulness_score IS 'LLM-as-judge faithfulness score [0.0, 1.0] — % of claims attributable to context';
COMMENT ON COLUMN ai_suggestion.eval_relevancy_score IS 'LLM-as-judge answer relevancy score [0.0, 1.0]';
COMMENT ON COLUMN ai_suggestion.eval_scored_at IS 'Timestamp when eval scores were computed';
