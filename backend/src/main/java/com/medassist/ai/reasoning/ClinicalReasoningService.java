package com.medassist.ai.reasoning;

import com.medassist.ai.validation.ReasoningOutputContract;
import com.medassist.ai.validation.ReasoningValidator;
import com.medassist.diagnosis.context.ClinicalFact;
import com.medassist.diagnosis.reasoning.AiReasoningStatus;
import com.medassist.diagnosis.reasoning.ClinicalReasoningPort;
import com.medassist.diagnosis.reasoning.ReasoningInput;
import com.medassist.diagnosis.reasoning.ReasoningOutcome;
import com.medassist.knowledge.retrieval.RetrievedClinicalEvidence;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * AI-assisted reasoning for assessments: renders the versioned prompt, calls the provider, enforces the structured
 * output contract, then applies the deterministic validation layer. Never throws for provider problems.
 */
@Service
public class ClinicalReasoningService implements ClinicalReasoningPort {

    public static final String PROMPT_ID = "clinical-reasoning";
    public static final String PURPOSE = "ASSESSMENT_REASONING";

    private final StructuredAiCaller caller;
    private final ReasoningValidator validator;

    public ClinicalReasoningService(StructuredAiCaller caller, ReasoningValidator validator) {
        this.caller = caller;
        this.validator = validator;
    }

    @Override
    public ReasoningOutcome reason(ReasoningInput input) {
        StructuredAiCaller.CallContext ctx = new StructuredAiCaller.CallContext(PURPOSE, input.context().encounterId(),
                input.context().patientId(), input.runId(), input.requestedBy(), input.evidence());
        StructuredAiCaller.CallResult<ReasoningOutputContract> result;
        try {
            result = caller.call(PROMPT_ID, ReasoningPromptVariables.forAssessment(input), ReasoningOutputContract.class, ctx);
        } catch (RuntimeException e) {
            return ReasoningOutcome.without(AiReasoningStatus.UNAVAILABLE,
                    "AI reasoning could not be performed. Deterministic results are shown.");
        }
        return switch (result.outcome()) {
            case NOT_CONFIGURED -> ReasoningOutcome.without(AiReasoningStatus.NOT_CONFIGURED,
                    "AI reasoning is not configured for this installation. Deterministic results are shown.");
            case PROVIDER_ERROR -> new ReasoningOutcome(AiReasoningStatus.UNAVAILABLE,
                    "The AI service is temporarily unavailable. Safety rules, scores and knowledge references are "
                            + "unaffected; you can re-run the assessment later.", result.executionId(), null, List.of());
            case VALIDATION_FAILED -> new ReasoningOutcome(AiReasoningStatus.OUTPUT_REJECTED,
                    "The AI response did not pass validation and is not shown. Deterministic results are unaffected; "
                            + "you can re-run the assessment.", result.executionId(), null, result.errors());
            case SUCCEEDED -> {
                Set<String> factKeys = input.context().facts().stream().map(ClinicalFact::key).collect(Collectors.toSet());
                Set<String> evidenceKeys = input.evidence().stream().map(RetrievedClinicalEvidence::evidenceKey)
                        .collect(Collectors.toSet());
                ReasoningValidator.Result validated = validator.validate(result.value(), factKeys, evidenceKeys);
                UUID executionId = caller.recordSuccess(result, ctx, validated.summary(), validated.issues());
                yield new ReasoningOutcome(AiReasoningStatus.COMPLETED, "AI-generated decision support (validated). "
                        + "Not a substitute for professional clinical judgment.", executionId, validated.reasoning(),
                        validated.issues());
            }
        };
    }
}
