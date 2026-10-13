package com.medassist.symptom.api;

import com.medassist.common.api.CommonDtoMapper;
import com.medassist.symptom.domain.Symptom;
import org.mapstruct.Mapper;

@Mapper(uses = CommonDtoMapper.class)
public interface SymptomMapper {

    SymptomDtos.SymptomResponse toResponse(Symptom symptom);

    default Symptom.Details toDetails(SymptomDtos.SymptomRequest r) {
        return new Symptom.Details(r.presence(), r.onsetPattern(), r.onsetAt(), r.duration(), r.severity(),
                r.location(), r.character(), r.radiation(), r.aggravatingFactors(), r.relievingFactors(),
                r.associatedSymptoms(), r.notes());
    }
}
