package com.medassist.knowledge.application;

import com.medassist.audit.application.AuditEntry;
import com.medassist.audit.application.AuditService;
import com.medassist.audit.domain.AuditEventType;
import com.medassist.common.error.ConflictException;
import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.common.error.NotFoundException;
import com.medassist.common.json.JsonCodec;
import com.medassist.configuration.AsyncConfiguration;
import com.medassist.knowledge.document.ApprovalStatus;
import com.medassist.knowledge.document.DocumentFormat;
import com.medassist.knowledge.document.IngestionStatus;
import com.medassist.knowledge.document.KnowledgeDocument;
import com.medassist.knowledge.document.KnowledgeDocumentRepository;
import com.medassist.knowledge.document.KnowledgeDocumentSource;
import com.medassist.knowledge.document.KnowledgeDocumentSourceRepository;
import com.medassist.knowledge.ingestion.KnowledgeIngestionService;
import com.medassist.security.CurrentUserProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Knowledge base administration: upload, manual entry, versions, approval, enable/disable, re-index, delete. */
@Service
public class KnowledgeDocumentService {

    public record UploadCommand(KnowledgeDocument.Metadata metadata, List<String> conditionTags, String filename,
                                String declaredContentType, byte[] content, boolean synthetic) {
    }

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentSourceRepository sourceRepository;
    private final KnowledgeIngestionService ingestionService;
    private final TaskExecutor executor;
    private final AuditService auditService;
    private final CurrentUserProvider currentUser;
    private final JsonCodec json;
    private final Clock clock;

    public KnowledgeDocumentService(KnowledgeDocumentRepository documentRepository,
                                    KnowledgeDocumentSourceRepository sourceRepository,
                                    KnowledgeIngestionService ingestionService,
                                    @Qualifier(AsyncConfiguration.CLINICAL_TASK_EXECUTOR) TaskExecutor executor,
                                    AuditService auditService, CurrentUserProvider currentUser, JsonCodec json,
                                    Clock clock) {
        this.documentRepository = documentRepository;
        this.sourceRepository = sourceRepository;
        this.ingestionService = ingestionService;
        this.executor = executor;
        this.auditService = auditService;
        this.currentUser = currentUser;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public KnowledgeDocument upload(UploadCommand command) {
        return register(null, null, command);
    }

    /** Knowledge entered directly as text (stored as Markdown). */
    @Transactional
    public KnowledgeDocument createManual(KnowledgeDocument.Metadata metadata, List<String> conditionTags, String text,
                                          boolean synthetic) {
        if (text == null || text.isBlank()) {
            throw new DomainRuleViolationException("Knowledge text is required");
        }
        return register(null, null, new UploadCommand(metadata, conditionTags, "manual-entry.md",
                DocumentFormat.MARKDOWN.mediaType(), text.getBytes(StandardCharsets.UTF_8), synthetic));
    }

    /** Uploads a new version of an existing document series; the previous version is superseded once indexed. */
    @Transactional
    public KnowledgeDocument uploadNewVersion(UUID previousVersionId, UploadCommand command) {
        KnowledgeDocument previous = require(previousVersionId);
        if (documentRepository.existsBySeriesIdAndDocumentVersion(previous.getSeriesId(), command.metadata().version())) {
            throw new ConflictException("This document already has a version " + command.metadata().version());
        }
        return register(previous.getSeriesId(), previous.getId(), command);
    }

    @Transactional
    public KnowledgeDocument update(UUID documentId, Boolean enabled, ApprovalStatus approvalStatus,
                                    List<String> conditionTags) {
        KnowledgeDocument document = require(documentId);
        AuditEntry audit = AuditEntry.of(AuditEventType.KNOWLEDGE_DOCUMENT_UPDATED).entity("KnowledgeDocument", documentId);
        if (enabled != null && enabled != document.isEnabled()) {
            if (enabled && document.getSupersededById() != null) {
                throw new DomainRuleViolationException("A superseded version cannot be re-enabled; upload a new version");
            }
            document.setEnabled(enabled);
            audit.with("enabled", enabled);
        }
        if (approvalStatus != null && approvalStatus != document.getApprovalStatus()) {
            document.changeApproval(approvalStatus);
            audit.with("approvalStatus", approvalStatus);
        }
        if (conditionTags != null) {
            document.changeConditionTags(json.write(normalizeTags(conditionTags)));
            audit.with("conditionTagsChanged", true);
        }
        auditService.record(audit);
        return document;
    }

    @Transactional
    public KnowledgeDocument reindex(UUID documentId) {
        KnowledgeDocument document = require(documentId);
        if (document.getIngestionStatus() != IngestionStatus.INDEXED && document.getIngestionStatus() != IngestionStatus.FAILED) {
            throw new ConflictException("Document is currently being ingested");
        }
        document.ingestionStage(IngestionStatus.PENDING);
        auditService.record(AuditEntry.of(AuditEventType.KNOWLEDGE_DOCUMENT_REINDEX_REQUESTED)
                .entity("KnowledgeDocument", documentId));
        scheduleIngestion(documentId);
        return document;
    }

    /**
     * Deletes a document with its chunks and embeddings. Assessments that cited it keep their own immutable
     * evidence snapshots.
     */
    @Transactional
    public void delete(UUID documentId) {
        KnowledgeDocument document = require(documentId);
        documentRepository.delete(document);
        auditService.record(AuditEntry.of(AuditEventType.KNOWLEDGE_DOCUMENT_DELETED)
                .entity("KnowledgeDocument", documentId)
                .with("version", document.getDocumentVersion())
                .with("checksum", document.getChecksum()));
    }

    @Transactional(readOnly = true)
    public KnowledgeDocument require(UUID documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> NotFoundException.of("Knowledge document", documentId));
    }

    private KnowledgeDocument register(UUID seriesId, UUID supersedesId, UploadCommand command) {
        if (command.content() == null || command.content().length == 0) {
            throw new DomainRuleViolationException("The document is empty");
        }
        DocumentFormat format = DocumentFormat.detect(command.filename(), command.declaredContentType());
        String checksum = sha256(command.content());
        if (documentRepository.existsByChecksumAndEnabledTrue(checksum)) {
            throw new ConflictException("An identical document is already in the knowledge base");
        }
        KnowledgeDocument.Metadata m = command.metadata();
        KnowledgeDocument.Metadata metadata = new KnowledgeDocument.Metadata(m.title(), m.documentType(),
                m.sourceOrganization(), m.sourceUrl(), m.jurisdiction(), m.version(), m.publicationDate(),
                m.reviewDate(), m.approvalStatus(), json.write(normalizeTags(command.conditionTags())));
        KnowledgeDocument document = documentRepository.save(KnowledgeDocument.register(seriesId, metadata, checksum,
                format.mediaType(), safeFilename(command.filename()), command.content().length, command.synthetic(),
                supersedesId, currentUser.currentUserId(), Instant.now(clock)));
        sourceRepository.save(new KnowledgeDocumentSource(document.getId(), command.content()));
        auditService.record(AuditEntry.of(AuditEventType.KNOWLEDGE_DOCUMENT_UPLOADED)
                .entity("KnowledgeDocument", document.getId())
                .with("format", format)
                .with("version", document.getDocumentVersion())
                .with("approvalStatus", document.getApprovalStatus())
                .with("checksum", checksum)
                .with("supersedes", supersedesId)
                .with("synthetic", command.synthetic()));
        scheduleIngestion(document.getId());
        return document;
    }

    private void scheduleIngestion(UUID documentId) {
        String actor = currentUser.currentUserId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.execute(() -> ingestionService.ingest(documentId, actor));
            }
        });
    }

    private static List<String> normalizeTags(List<String> tags) {
        return tags == null ? List.of() : tags.stream().filter(t -> t != null && !t.isBlank()).map(String::trim)
                .distinct().limit(30).toList();
    }

    private static String safeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.length() > 300 ? name.substring(0, 300) : name;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
