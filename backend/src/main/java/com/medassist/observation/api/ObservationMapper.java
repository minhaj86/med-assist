package com.medassist.observation.api;

import com.medassist.common.api.CommonDtoMapper;
import com.medassist.observation.domain.ClinicalObservation;
import com.medassist.observation.domain.LabObservation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = CommonDtoMapper.class)
public interface ObservationMapper {

    @Mapping(target = "displayValue", expression = "java(observation.displayValue())")
    ObservationDtos.ObservationResponse toResponse(ClinicalObservation observation);

    @Mapping(target = "testCode", source = "test.code")
    @Mapping(target = "codingSystem", source = "test.system")
    @Mapping(target = "testName", source = "test.displayName")
    @Mapping(target = "codeVerification", source = "test.verificationStatus")
    @Mapping(target = "displayValue", expression = "java(lab.displayValue())")
    ObservationDtos.LabResponse toResponse(LabObservation lab);
}
