package com.medassist.ai.prompt;

import com.medassist.common.json.JsonCodec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;

/**
 * Registers the reviewed prompt catalog ({@code prompts/catalog.json}) in the prompt registry at startup. A changed
 * template under an existing version fails startup: prompt versions are immutable, changes need a new version.
 */
@Component
public class PromptCatalogLoader implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PromptCatalogLoader.class);

    record CatalogEntry(String promptId, String version, PromptStatus status, String description, String intendedModel,
                        String outputSchemaId, String systemTemplate, String userTemplate, String repairTemplate) {
    }

    private final PromptTemplateRepository repository;
    private final TransactionTemplate tx;
    private final JsonCodec json;
    private final Clock clock;

    public PromptCatalogLoader(PromptTemplateRepository repository, TransactionTemplate tx, JsonCodec json, Clock clock) {
        this.repository = repository;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public void afterPropertiesSet() {
        List<CatalogEntry> catalog = json.read(read("prompts/catalog.json"), new TypeReference<List<CatalogEntry>>() {
        });
        tx.executeWithoutResult(status -> catalog.forEach(this::register));
    }

    private void register(CatalogEntry entry) {
        String system = read("prompts/" + entry.systemTemplate());
        String user = read("prompts/" + entry.userTemplate());
        String repair = read("prompts/" + entry.repairTemplate());
        String checksum = checksum(system, user + "\u0000" + repair, entry.outputSchemaId());
        repository.findByPromptIdAndVersion(entry.promptId(), entry.version()).ifPresentOrElse(existing -> {
            if (!existing.getChecksum().equals(checksum)) {
                throw new IllegalStateException("Prompt " + entry.promptId() + " v" + entry.version()
                        + " changed without a version change. Prompt versions are immutable - create a new version.");
            }
            if (existing.getStatus() != entry.status()) {
                existing.changeStatus(entry.status());
            }
        }, () -> {
            if (entry.status() == PromptStatus.ACTIVE) {
                repository.findByPromptIdAndStatus(entry.promptId(), PromptStatus.ACTIVE)
                        .ifPresent(previous -> {
                            previous.changeStatus(PromptStatus.RETIRED);
                            repository.flush();
                        });
            }
            repository.save(new PromptTemplate(entry.promptId(), entry.version(), entry.description(), system, user,
                    repair, entry.intendedModel(), entry.outputSchemaId(), entry.status(), checksum, Instant.now(clock),
                    "prompt-catalog"));
            log.info("Registered prompt {} v{} ({})", entry.promptId(), entry.version(), entry.status());
        });
    }

    static String checksum(String system, String user, String schemaId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(system.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(user.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(schemaId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Prompt resource " + path + " not found", e);
        }
    }
}
