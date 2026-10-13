package com.medassist.fhir.api;

import com.medassist.fhir.application.FhirExportService;
import com.medassist.fhir.application.FhirImportService;
import com.medassist.security.annotation.RequireClinician;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fhir")
@Tag(name = "FHIR R4")
@RequireClinician
public class FhirController {

    public static final String FHIR_JSON = "application/fhir+json";

    private final FhirImportService importService;
    private final FhirExportService exportService;

    public FhirController(FhirImportService importService, FhirExportService exportService) {
        this.importService = importService;
        this.exportService = exportService;
    }

    @PostMapping(value = "/import", consumes = {FHIR_JSON, "application/json"})
    @Operation(summary = "Import a FHIR R4 Bundle (one Patient with Encounter, Observations, Conditions, "
            + "MedicationRequests, AllergyIntolerances); all-or-nothing")
    public FhirImportService.ImportResult importBundle(@RequestBody String body) {
        return importService.importResource(body);
    }

    @GetMapping(value = "/patient/{patientId}", produces = FHIR_JSON)
    @Operation(summary = "Export a patient as a FHIR R4 Patient resource")
    public String patient(@PathVariable UUID patientId) {
        return exportService.patient(patientId);
    }

    @GetMapping(value = "/encounter/{encounterId}", produces = FHIR_JSON)
    @Operation(summary = "Export an encounter as a FHIR R4 collection Bundle")
    public String encounter(@PathVariable UUID encounterId) {
        return exportService.encounterBundle(encounterId);
    }
}
