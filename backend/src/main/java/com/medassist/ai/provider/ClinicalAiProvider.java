package com.medassist.ai.provider;

/**
 * Port to a large language model provider. Implementations return the provider's raw output; callers must never
 * pass it to clinicians before structured, citation and safety validation.
 *
 * <p>Adapters: OpenAI, Azure OpenAI and OpenAI-compatible servers (via Spring AI). Further providers (Anthropic,
 * native Ollama, other self-hosted models) can be added by implementing this interface.
 */
public interface ClinicalAiProvider {

    ClinicalReasoningResponse reason(ClinicalReasoningRequest request);

    /** Provider descriptor for status pages and audit (never contains credentials). */
    ProviderDescriptor descriptor();

    record ProviderDescriptor(String provider, String model, String endpoint, boolean enabled, String statusMessage) {
    }
}
