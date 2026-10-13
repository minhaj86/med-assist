package com.medassist.knowledge.document;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentSourceRepository extends JpaRepository<KnowledgeDocumentSource, UUID> {
}
