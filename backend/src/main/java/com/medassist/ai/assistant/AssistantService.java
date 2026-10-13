package com.medassist.ai.assistant;

import com.medassist.ai.guardrail.SafetyGuardrail;
import com.medassist.ai.reasoning.ReasoningPromptVariables;
import com.medassist.ai.reasoning.StructuredAiCaller;
import com.medassist.ai.validation.AssistantOutputContract;
import com.medassist.assessment.application.AssessmentQueryService;
import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.error.NotFoundException;
import com.medassist.common.error.ServiceUnavailableException;
import com.medassist.common.json.JsonCodec;
import com.medassist.diagnosis.context.ClinicalContext;
import com.medassist.diagnosis.context.ClinicalContextAssembler;
import com.medassist.diagnosis.context.ClinicalFact;
import com.medassist.diagnosis.differential.SupportStatus;
import com.medassist.encounter.application.EncounterService;
import com.medassist.encounter.domain.Encounter;
import com.medassist.knowledge.citation.CitationValidator;
import com.medassist.knowledge.retrieval.ClinicalKnowledgeRetriever;
import com.medassist.knowledge.retrieval.KnowledgeQuery;
import com.medassist.knowledge.retrieval.RetrievedClinicalEvidence;
import com.medassist.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encounter-scoped clinical assistant. Receives the current encounter context automatically, cites evidence, and may
 * only PROPOSE record updates - it never modifies patient data.
 */
@Service
public class AssistantService {

    public static final String PROMPT_ID = "clinical-assistant";
    public static final String PURPOSE = "ASSISTANT_QUESTION";
    static final int HISTORY_MESSAGES = 6;

    private final EncounterService encounterService;
    private final ClinicalContextAssembler assembler;
    private final ClinicalKnowledgeRetriever retriever;
    private final AssessmentQueryService assessmentQueryService;
    private final StructuredAiCaller caller;
    private final CitationValidator citationValidator;
    private final SafetyGuardrail guardrail;
    private final AssistantConversationRepository conversations;
    private final AssistantMessageRepository messages;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final JsonCodec json;
    private final Clock clock;

    public AssistantService(EncounterService encounterService, ClinicalContextAssembler assembler,
                            ClinicalKnowledgeRetriever retriever, AssessmentQueryService assessmentQueryService,
                            StructuredAiCaller caller, CitationValidator citationValidator, SafetyGuardrail guardrail,
                            AssistantConversationRepository conversations,
                            AssistantMessageRepository messages, AuditService auditService,
                            CurrentUserProvider currentUser, JsonCodec json, Clock clock) {
        this.encounterService = encounterService;
        this.assembler = assembler;
        this.retriever = retriever;
        this.assessmentQueryService = assessmentQueryService;
        this.caller = caller;
        this.citationValidator = citationValidator;
        this.guardrail = guardrail;
        this.conversations = conversations;
        this.messages = messages;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public AssistantAnswer ask(UUID encounterId, String question, UUID conversationId) {
        Encounter encounter = encounterService.require(encounterId);
        String clinicianId = currentUser.currentUserId();
        AssistantConversation conversation = conversationId == null
                ? conversations.save(new AssistantConversation(encounterId, encounter.getPatientId(), clinicianId, Instant.now(clock)))
                : conversations.findById(conversationId)
                .filter(c -> c.getEncounterId().equals(encounterId))
                .orElseThrow(() -> NotFoundException.of("Assistant conversation", conversationId));
        List<AssistantMessage> history = messages.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        messages.save(new AssistantMessage(conversation.getId(), AssistantMessage.Role.CLINICIAN, question.strip(), null,
                null, clinicianId, Instant.now(clock)));
        auditService.record(AuditEntry.of(AuditEventType.ASSISTANT_QUESTION_ASKED)
                .entity("AssistantConversation", conversation.getId())
                .patient(encounter.getPatientId())
                .encounter(encounterId)
                .with("questionLength", question.length()));

        ClinicalContext context = assembler.assemble(encounterId);
        List<RetrievedClinicalEvidence> evidence = retrieve(context, question);
        Map<String, String> variables = new LinkedHashMap<>(ReasoningPromptVariables.contextVariables(context));
        variables.put("assessmentSummary", ReasoningPromptVariables.sanitize(
                assessmentQueryService.latestSummaryForAssistant(encounterId).orElse("(no assessment has been run yet)")));
        variables.put("knowledge", ReasoningPromptVariables.knowledge(evidence));
        variables.put("conversation", conversationText(history));
        variables.put("question", ReasoningPromptVariables.sanitize(question.strip()));

        StructuredAiCaller.CallContext ctx = new StructuredAiCaller.CallContext(PURPOSE, encounterId,
                encounter.getPatientId(), null, clinicianId, evidence);
        StructuredAiCaller.CallResult<AssistantOutputContract> result =
                caller.call(PROMPT_ID, variables, AssistantOutputContract.class, ctx);
        switch (result.outcome()) {
            case NOT_CONFIGURED, PROVIDER_ERROR -> throw new ServiceUnavailableException(
                    "The AI assistant is temporarily unavailable. All other MedAssist functions continue to work.");
            case VALIDATION_FAILED -> {
                return new AssistantAnswer(conversation.getId(), null, AssistantAnswer.Status.OUTPUT_REJECTED,
                        "The assistant could not produce a validated answer. Please rephrase the question or try again.",
                        null, List.of(), true, List.of(), List.of(), List.of(), result.executionId(), Instant.now(clock));
            }
            default -> {
                // validated below
            }
        }
        Set<String> factKeys = context.facts().stream().map(ClinicalFact::key).collect(Collectors.toSet());
        Set<String> evidenceKeys = evidence.stream().map(RetrievedClinicalEvidence::evidenceKey).collect(Collectors.toSet());
        Validated validated = validate(result.value(), factKeys, evidenceKeys);
        UUID executionId = caller.recordSuccess(result, ctx, Map.of("statements", validated.statements().size(),
                "withheld", validated.withheld(), "proposedUpdates", result.value().proposedUpdates().size()),
                validated.issues());
        UUID messageId = UUID.randomUUID();
        List<AssistantAnswer.ProposedUpdate> proposals = new ArrayList<>();
        int index = 0;
        for (AssistantOutputContract.ProposedUpdate p : result.value().proposedUpdates()) {
            index++;
            if (guardrail.inspect(p.description() + " " + p.rationale()).isEmpty()) {
                proposals.add(new AssistantAnswer.ProposedUpdate(p.category(), p.description().strip(),
                        p.rationale().strip(), "assistant-execution:" + executionId + "#" + index));
            }
        }
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("statements", validated.statements());
        structured.put("insufficientInformation", result.value().insufficientInformation());
        structured.put("proposedUpdates", proposals);
        structured.put("followUpQuestions", validated.followUps());
        structured.put("evidenceKeys", evidence.stream().map(RetrievedClinicalEvidence::evidenceKey).toList());
        AssistantMessage saved = messages.save(new AssistantMessage(conversation.getId(), AssistantMessage.Role.ASSISTANT,
                validated.answer(), json.write(structured), executionId, "ai-assistant", Instant.now(clock)));
        return new AssistantAnswer(conversation.getId(), saved.getId(), AssistantAnswer.Status.ANSWERED,
                "AI-generated clinical decision support. Not a substitute for professional clinical judgment.",
                validated.answer(), validated.statements(), result.value().insufficientInformation(), proposals,
                validated.followUps(), evidence.stream().map(AssistantService::toEvidence).toList(), executionId,
                saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<AssistantMessage> latestConversation(UUID encounterId) {
        encounterService.require(encounterId);
        return conversations.findFirstByEncounterIdOrderByStartedAtDesc(encounterId)
                .map(c -> messages.findByConversationIdOrderByCreatedAtAsc(c.getId()))
                .orElse(List.of());
    }

    private record Validated(String answer, List<AssistantAnswer.Statement> statements, List<String> followUps,
                             int withheld, List<String> issues) {
    }

    private Validated validate(AssistantOutputContract output, Set<String> factKeys, Set<String> evidenceKeys) {
        int withheld = 0;
        List<String> issues = new ArrayList<>();
        String answer = output.answer().strip();
        List<SafetyGuardrail.Finding> answerFindings = guardrail.inspect(answer);
        if (!answerFindings.isEmpty()) {
            withheld++;
            issues.add("Answer text withheld by safety validator");
            answer = SafetyGuardrail.withheldPlaceholder(answerFindings);
        }
        List<AssistantAnswer.Statement> statements = new ArrayList<>();
        for (AssistantOutputContract.Statement s : output.statements()) {
            List<SafetyGuardrail.Finding> findings = guardrail.inspect(s.statement());
            if (!findings.isEmpty()) {
                withheld++;
                statements.add(new AssistantAnswer.Statement(SafetyGuardrail.withheldPlaceholder(findings), List.of(),
                        List.of(), SupportStatus.WITHHELD));
                continue;
            }
            CitationValidator.CitationCheck check = citationValidator.check(s.evidenceIds(), factKeys, evidenceKeys);
            if (check.hasInvalid()) {
                issues.add("Invalid citation(s) removed: " + check.invalid().size());
            }
            statements.add(new AssistantAnswer.Statement(s.statement().strip(), check.validFactKeys(),
                    check.validEvidenceKeys(), check.hasAnyValid() ? SupportStatus.SUPPORTED : SupportStatus.UNSUPPORTED));
        }
        List<String> followUps = output.followUpQuestions().stream()
                .filter(q -> guardrail.inspect(q).isEmpty()).map(String::strip).toList();
        return new Validated(answer, List.copyOf(statements), followUps, withheld, List.copyOf(issues));
    }

    private List<RetrievedClinicalEvidence> retrieve(ClinicalContext context, String question) {
        try {
            List<String> symptoms = context.presentSymptoms().stream().map(ClinicalContext.SymptomFact::displayName).toList();
            return retriever.retrieve(new KnowledgeQuery(symptoms, List.of(), List.of(), question, 4));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private String conversationText(List<AssistantMessage> history) {
        if (history.isEmpty()) {
            return "(no previous messages)";
        }
        List<AssistantMessage> recent = history.subList(Math.max(0, history.size() - HISTORY_MESSAGES), history.size());
        return recent.stream().map(m -> (m.getRole() == AssistantMessage.Role.CLINICIAN ? "Clinician: " : "Assistant: ")
                + ReasoningPromptVariables.sanitize(m.getContent())).collect(Collectors.joining("\n"));
    }

    private static AssistantAnswer.Evidence toEvidence(RetrievedClinicalEvidence e) {
        return new AssistantAnswer.Evidence(e.evidenceKey(), e.documentId(), e.documentTitle(), e.documentVersion(),
                e.sourceOrganization(), e.section(), e.page(), e.publicationDate(),
                BigDecimal.valueOf(e.similarity()).setScale(4, RoundingMode.HALF_UP), e.text(), e.syntheticSource());
    }
}
