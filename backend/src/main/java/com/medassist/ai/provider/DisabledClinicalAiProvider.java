package com.medassist.ai.provider;

/** Used when no provider is configured (or configuration is incomplete). Deterministic features are unaffected. */
public class DisabledClinicalAiProvider implements ClinicalAiProvider {

    private final ProviderDescriptor descriptor;

    public DisabledClinicalAiProvider(String provider, String reason) {
        this.descriptor = new ProviderDescriptor(provider, null, null, false, reason);
    }

    @Override
    public ClinicalReasoningResponse reason(ClinicalReasoningRequest request) {
        throw new AiProviderException(AiProviderException.Category.NOT_CONFIGURED, descriptor.statusMessage());
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }
}
