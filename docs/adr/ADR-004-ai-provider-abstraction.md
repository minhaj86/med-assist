# ADR-004: AI provider abstraction

**Status:** Accepted

## Context
LLM vendors, models, pricing, data-residency terms and quality change quickly. Hospitals may require a local model
(no data leaving the network) or a specific cloud tenant (Azure OpenAI). The core system must stay usable when AI is
unavailable.

## Decision
Define `ClinicalAiProvider` (`reason(ClinicalReasoningRequest) → ClinicalReasoningResponse`) in the `ai` module with
adapters built on Spring AI's OpenAI client:
* `openai` — OpenAI API,
* `azure-openai` — Azure OpenAI deployments,
* `openai-compatible` — local or self-hosted servers (Ollama, vLLM, LM Studio; e.g. Qwen or Llama models),
* `none` — a disabled provider that reports `NOT_CONFIGURED`.

Selection and settings come from environment variables (`AI_PROVIDER`, `OPENAI_*`, `AZURE_OPENAI_*`, `LOCAL_LLM_*`).
The provider is wrapped with timeouts, safe retries, a bulkhead and a circuit breaker (Resilience4j). Misconfiguration
falls back to the disabled provider instead of failing startup. Additional providers (Anthropic, native Ollama, …)
are added as new adapters without touching the assessment pipeline.

## Consequences
* No vendor lock-in; local models are first-class.
* Model capabilities differ (e.g. strict JSON-schema output); `AI_RESPONSE_FORMAT` selects `json_object` or
  `json_schema`, and validation (ADR-005) is identical for every provider.
* Embedding providers are configured independently of the chat provider.
