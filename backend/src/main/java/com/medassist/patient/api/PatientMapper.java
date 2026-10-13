package com.medassist.patient.api;

import com.medassist.patient.domain.Patient;
import java.time.LocalDate;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface PatientMapper {

    @Mapping(target = "ageYears", expression = "java(patient.ageOn(today))")
    PatientDtos.PatientResponse toResponse(Patient patient, @Context LocalDate today);

    @Mapping(target = "ageYears", expression = "java(patient.ageOn(today))")
    PatientDtos.PatientSummary toSummary(Patient patient, @Context LocalDate today);
}
