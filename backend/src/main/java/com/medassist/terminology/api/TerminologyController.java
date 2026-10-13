package com.medassist.terminology.api;

import com.medassist.terminology.domain.ClinicalConcept;
import com.medassist.terminology.domain.ConceptDomain;
import com.medassist.terminology.domain.TerminologyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/terminology")
@Tag(name = "Terminology")
public class TerminologyController {

    private final TerminologyService terminologyService;

    public TerminologyController(TerminologyService terminologyService) {
        this.terminologyService = terminologyService;
    }

    @GetMapping("/concepts")
    @Operation(summary = "Concept suggestions for structured data entry (codes only when verifiable)")
    public List<ClinicalConcept> suggest(@RequestParam ConceptDomain domain,
                                         @RequestParam(defaultValue = "") String query,
                                         @RequestParam(defaultValue = "15") int limit) {
        return terminologyService.suggest(query, domain, Math.min(Math.max(limit, 1), 50));
    }
}
