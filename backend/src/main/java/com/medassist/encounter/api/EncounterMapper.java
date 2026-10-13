package com.medassist.encounter.api;

import com.medassist.encounter.domain.Encounter;
import org.mapstruct.Mapper;

@Mapper
public interface EncounterMapper {

    EncounterDtos.EncounterResponse toResponse(Encounter encounter);
}
