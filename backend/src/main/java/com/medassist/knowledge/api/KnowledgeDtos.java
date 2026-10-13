package com.medassist.knowledge.api;

import com.medassist.knowledge.document.ApprovalStatus;
import com.medassist.knowledge.document.DocumentType;
import com.medassist.knowledge.document.IngestionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    public record DocumentMetadataRequest(@NotBlank @Size(max = 300) String title,
                                          @NotNull DocumentType documentType,
                                          @NotBlank @Size(max = 300) String sourceOrganization,
                                          @Size(max = 1000) @Pattern(regexp = "^(https?://.*)?$",
                                                  message = "must be an http(s) URL") String sourceUrl,
                                          @Size(max = 100) String jurisdiction,
                                          @NotBlank @Size(max = 50) String version,
                                          LocalDate publicationDate,
                                          LocalDate reviewDate,
                                          @NotNull ApprovalStatus approvalStatus,
                                          @Size(max = 30) List<@Size(max = 200) String> conditionTags) {
    }

    public record ManualDocumentRequest(@NotNull @jakarta.validation.Valid DocumentMetadataRequest metadata,
                                        @NotBlank @Size(max = 500_000) String text) {
    }

    public record UpdateDocumentRequest(Boolean enabled, ApprovalStatus approvalStatus,
                                        @Size(max = 30) List<@Size(max = 200) String> conditionTags) {
    }

    public record SearchRequest(@NotBlank @Size(max = 2000) String query, @Min(1) @Max(50) Integer topK,
                                Boolean includeInactive) {

        public boolean includeInactiveOrDefault() {
            return Boolean.TRUE.equals(includeInactive);
        }
    }

    public record DocumentSummary(UUID id, UUID seriesId, String title, DocumentType documentType,
                                  String sourceOrganization, String version, LocalDate publicationDate,
                                  LocalDate reviewDate, ApprovalStatus approvalStatus, boolean enabled,
                                  IngestionStatus ingestionStatus, String ingestionError, int chunkCount,
                                  Instant lastIndexedAt, String embeddingModel, boolean synthetic,
                                  UUID supersededById, Instant uploadedAt, String uploadedBy,
                                  boolean usableForClinicalRetrieval) {
    }

    public record DocumentDetail(DocumentSummary summary, String sourceUrl, String jurisdiction, String checksum,
                                 String contentType, String originalFilename, long sizeBytes, Integer pageCount,
                                 List<String> conditionTags, UUID supersedesId, long embeddedChunks,
                                 String currentEmbeddingModel, boolean embeddingsCurrent, String extractedText,
                                 List<DocumentSummary> versions) {
    }

    public record ChunkResponse(UUID id, int chunkIndex, String section, Integer pageNumber, String content,
                                int charCount, int tokenEstimate, JsonNode metadata) {
    }

    public record SearchHitResponse(UUID chunkId, UUID documentId, String documentTitle, String documentVersion,
                                    String sourceOrganization, String section, Integer page,
                                    double retrievalSimilarity, String content, JsonNode metadata,
                                    boolean usableForClinicalRetrieval) {
    }
}
