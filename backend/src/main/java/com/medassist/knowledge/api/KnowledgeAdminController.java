package com.medassist.knowledge.api;

import com.medassist.common.json.JsonCodec;
import com.medassist.common.web.PageResponse;
import com.medassist.knowledge.application.KnowledgeDocumentService;
import com.medassist.knowledge.application.KnowledgeQueryService;
import com.medassist.knowledge.document.DocumentType;
import com.medassist.knowledge.document.KnowledgeDocument;
import com.medassist.knowledge.embedding.EmbeddingService;
import com.medassist.security.annotation.RequireKnowledgeAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/api/admin/knowledge")
@Tag(name = "Admin - Knowledge base")
@RequireKnowledgeAccess
public class KnowledgeAdminController {

    private final KnowledgeDocumentService documentService;
    private final KnowledgeQueryService queryService;
    private final EmbeddingService embeddingService;
    private final JsonCodec json;

    public KnowledgeAdminController(KnowledgeDocumentService documentService, KnowledgeQueryService queryService,
                                    EmbeddingService embeddingService, JsonCodec json) {
        this.documentService = documentService;
        this.queryService = queryService;
        this.embeddingService = embeddingService;
        this.json = json;
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a PDF, TXT or Markdown document (ingested asynchronously)")
    public KnowledgeDtos.DocumentSummary upload(@Valid @RequestPart("metadata") KnowledgeDtos.DocumentMetadataRequest metadata,
                                                @RequestPart("file") MultipartFile file) throws IOException {
        return toSummary(documentService.upload(command(metadata, file)));
    }

    @PostMapping("/documents/manual")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enter knowledge manually as text")
    public KnowledgeDtos.DocumentSummary createManual(@Valid @RequestBody KnowledgeDtos.ManualDocumentRequest request) {
        return toSummary(documentService.createManual(toMetadata(request.metadata()), request.metadata().conditionTags(),
                request.text(), request.metadata().documentType() == DocumentType.SYNTHETIC_DEMO));
    }

    @PostMapping(value = "/documents/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Upload a new version of a document (supersedes the previous version once indexed)")
    public KnowledgeDtos.DocumentSummary uploadVersion(@PathVariable UUID documentId,
                                                       @Valid @RequestPart("metadata") KnowledgeDtos.DocumentMetadataRequest metadata,
                                                       @RequestPart("file") MultipartFile file) throws IOException {
        return toSummary(documentService.uploadNewVersion(documentId, command(metadata, file)));
    }

    @GetMapping("/documents")
    @Operation(summary = "List knowledge documents")
    public List<KnowledgeDtos.DocumentSummary> list() {
        return queryService.list().stream().map(this::toSummary).toList();
    }

    @GetMapping("/documents/{documentId}")
    @Operation(summary = "Document metadata, extracted text, versions and embedding status")
    public KnowledgeDtos.DocumentDetail get(@PathVariable UUID documentId) {
        KnowledgeDocument d = documentService.require(documentId);
        long embedded = queryService.embeddingCount(documentId);
        return new KnowledgeDtos.DocumentDetail(toSummary(d), d.getSourceUrl(), d.getJurisdiction(), d.getChecksum(),
                d.getContentType(), d.getOriginalFilename(), d.getSizeBytes(), d.getPageCount(), tags(d),
                d.getSupersedesId(), embedded, embeddingService.modelId(),
                embeddingService.modelId().equals(d.getEmbeddingModel()) && embedded == d.getChunkCount(),
                d.getExtractedText(), queryService.versions(documentId).stream().map(this::toSummary).toList());
    }

    @GetMapping("/documents/{documentId}/chunks")
    @Operation(summary = "Chunks of a document with their metadata")
    public PageResponse<KnowledgeDtos.ChunkResponse> chunks(@PathVariable UUID documentId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        documentService.require(documentId);
        return PageResponse.from(queryService.chunks(documentId, page, size), c -> new KnowledgeDtos.ChunkResponse(
                c.getId(), c.getChunkIndex(), c.getSection(), c.getPageNumber(), c.getContent(), c.getCharCount(),
                c.getTokenEstimate(), json.readTree(c.getMetadata())));
    }

    @PatchMapping("/documents/{documentId}")
    @Operation(summary = "Enable/disable, change approval status or condition tags")
    public KnowledgeDtos.DocumentSummary update(@PathVariable UUID documentId,
                                                @Valid @RequestBody KnowledgeDtos.UpdateDocumentRequest r) {
        return toSummary(documentService.update(documentId, r.enabled(), r.approvalStatus(), r.conditionTags()));
    }

    @PostMapping("/documents/{documentId}/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Re-extract, re-chunk and re-embed a document with the current configuration")
    public KnowledgeDtos.DocumentSummary reindex(@PathVariable UUID documentId) {
        return toSummary(documentService.reindex(documentId));
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a document, its chunks and embeddings")
    public void delete(@PathVariable UUID documentId) {
        documentService.delete(documentId);
    }

    @PostMapping("/search")
    @Operation(summary = "Knowledge-base debugging search (retrieval similarity is not clinical confidence)")
    public List<KnowledgeDtos.SearchHitResponse> search(@Valid @RequestBody KnowledgeDtos.SearchRequest r) {
        return queryService.search(r.query(), r.topK() == null ? 10 : r.topK(), r.includeInactiveOrDefault()).stream()
                .map(h -> new KnowledgeDtos.SearchHitResponse(h.chunkId(), h.documentId(), h.documentTitle(),
                        h.documentVersion(), h.sourceOrganization(), h.section(), h.page(), h.similarity(),
                        h.content(), json.readTree(h.metadataJson()), h.usableForClinicalRetrieval()))
                .toList();
    }

    @GetMapping("/status")
    @Operation(summary = "Embedding provider/model, chunking configuration and index statistics")
    public KnowledgeQueryService.KnowledgeStatus status() {
        return queryService.status();
    }

    private KnowledgeDocumentService.UploadCommand command(KnowledgeDtos.DocumentMetadataRequest metadata,
                                                           MultipartFile file) throws IOException {
        return new KnowledgeDocumentService.UploadCommand(toMetadata(metadata), metadata.conditionTags(),
                file.getOriginalFilename(), file.getContentType(), file.getBytes(),
                metadata.documentType() == DocumentType.SYNTHETIC_DEMO);
    }

    private static KnowledgeDocument.Metadata toMetadata(KnowledgeDtos.DocumentMetadataRequest m) {
        return new KnowledgeDocument.Metadata(m.title(), m.documentType(), m.sourceOrganization(), m.sourceUrl(),
                m.jurisdiction(), m.version(), m.publicationDate(), m.reviewDate(), m.approvalStatus(), "[]");
    }

    private List<String> tags(KnowledgeDocument d) {
        return json.read(d.getConditionTags(), new TypeReference<List<String>>() {
        });
    }

    private KnowledgeDtos.DocumentSummary toSummary(KnowledgeDocument d) {
        return new KnowledgeDtos.DocumentSummary(d.getId(), d.getSeriesId(), d.getTitle(), d.getDocumentType(),
                d.getSourceOrganization(), d.getDocumentVersion(), d.getPublicationDate(), d.getReviewDate(),
                d.getApprovalStatus(), d.isEnabled(), d.getIngestionStatus(), d.getIngestionError(), d.getChunkCount(),
                d.getLastIndexedAt(), d.getEmbeddingModel(), d.isSynthetic(), d.getSupersededById(), d.getUploadedAt(),
                d.getUploadedBy(), d.isUsableForRetrieval());
    }
}
