package com.medassist.demo;

import com.medassist.common.json.JsonCodec;
import com.medassist.knowledge.application.KnowledgeDocumentService;
import com.medassist.knowledge.document.ApprovalStatus;
import com.medassist.knowledge.document.DocumentType;
import com.medassist.knowledge.document.KnowledgeDocument;
import com.medassist.knowledge.document.KnowledgeDocumentRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

/**
 * Loads SYNTHETIC demonstration knowledge notes so retrieval and citations can be demonstrated. Each note states
 * that it is not clinical guidance; documents are marked synthetic and labelled as such wherever cited.
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "medassist.demo.seed-enabled", havingValue = "true")
public class SyntheticKnowledgeSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SyntheticKnowledgeSeeder.class);

    private record ManifestEntry(String file, String title, List<String> conditionTags) {
    }

    private final KnowledgeDocumentRepository repository;
    private final KnowledgeDocumentService documentService;
    private final JsonCodec json;

    public SyntheticKnowledgeSeeder(KnowledgeDocumentRepository repository, KnowledgeDocumentService documentService,
                                    JsonCodec json) {
        this.repository = repository;
        this.documentService = documentService;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (repository.existsBySyntheticTrue()) {
            log.info("Synthetic demonstration knowledge already present - skipping seed");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                SyntheticDataSeeder.SEEDER_USER, null, List.of()));
        try {
            List<ManifestEntry> manifest = json.read(read("demo/knowledge/manifest.json"),
                    new TypeReference<List<ManifestEntry>>() {
                    });
            for (ManifestEntry entry : manifest) {
                documentService.createManual(new KnowledgeDocument.Metadata(entry.title(), DocumentType.SYNTHETIC_DEMO,
                        "MedAssist synthetic demonstration content (not clinical guidance)", null,
                        "Not applicable (demonstration)", "demo-1.0", LocalDate.of(2026, 1, 1), null,
                        ApprovalStatus.APPROVED, "[]"), entry.conditionTags(), read("demo/knowledge/" + entry.file()), true);
            }
            log.info("Seeded {} synthetic demonstration knowledge notes", manifest.size());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static String read(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
