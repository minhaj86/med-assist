package com.medassist.medication.api;

import com.medassist.common.api.CommonDtoMapper;
import com.medassist.medication.domain.Medication;
import org.mapstruct.Mapper;

@Mapper(uses = CommonDtoMapper.class)
public interface MedicationMapper {

    MedicationDtos.MedicationResponse toResponse(Medication medication);
}
