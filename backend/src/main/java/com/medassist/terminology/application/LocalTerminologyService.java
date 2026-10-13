package com.medassist.terminology.application;

import com.medassist.terminology.catalog.CatalogEntry;
import com.medassist.terminology.catalog.LocalConceptCatalog;
import com.medassist.terminology.domain.ClinicalConcept;
import com.medassist.terminology.domain.CodingSystem;
import com.medassist.terminology.domain.ConceptDomain;
import com.medassist.terminology.domain.ConceptVerificationStatus;
import com.medassist.terminology.domain.TerminologyService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Terminology backed by the curated local catalog only. No SNOMED CT or ICD-10 codes are shipped (licensing
 * and accuracy); such concepts stay uncoded until a real terminology service is integrated.
 */
@Service
public class LocalTerminologyService implements TerminologyService {

    private final LocalConceptCatalog catalog;

    public LocalTerminologyService(LocalConceptCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public ClinicalConcept normalize(String text, ConceptDomain domain) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Concept text is required");
        }
        return catalog.find(trimmed, domain).map(LocalTerminologyService::toConcept)
                .orElseGet(() -> ClinicalConcept.unverified(trimmed));
    }

    @Override
    public List<ClinicalConcept> suggest(String query, ConceptDomain domain, int limit) {
        return catalog.search(query, domain, limit).stream().map(LocalTerminologyService::toConcept).toList();
    }

    @Override
    public ClinicalConcept verify(CodingSystem system, String code, String displayName) {
        if (code == null || code.isBlank() || system == null) {
            return ClinicalConcept.unverified(displayName);
        }
        return catalog.findByCode(code)
                .filter(entry -> entry.system() == system)
                .map(LocalTerminologyService::toConcept)
                .orElseGet(() -> new ClinicalConcept(system, code.trim(), displayName,
                        ConceptVerificationStatus.PROVIDED_UNVERIFIED));
    }

    @Override
    public String describeSources() {
        return "Local curated catalog (" + catalog.size() + " concepts; LOINC vital-sign codes from the HL7 FHIR R4 "
                + "Vital Signs profile; no SNOMED CT / ICD-10 codes). No external terminology service configured.";
    }

    private static ClinicalConcept toConcept(CatalogEntry entry) {
        if (entry.code() == null) {
            return ClinicalConcept.unverified(entry.displayName());
        }
        return new ClinicalConcept(entry.system(), entry.code(), entry.displayName(),
                ConceptVerificationStatus.CURATED_REFERENCE);
    }
}
