package com.medassist.common.api;

import com.medassist.common.domain.RecordProvenance;
import com.medassist.terminology.domain.ClinicalConcept;
import org.mapstruct.Mapper;

@Mapper
public interface CommonDtoMapper {

    CommonDtos.ProvenanceDto toDto(RecordProvenance provenance);

    CommonDtos.ConceptDto toDto(ClinicalConcept concept);
}
