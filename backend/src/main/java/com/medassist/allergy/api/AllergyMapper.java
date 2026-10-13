package com.medassist.allergy.api;

import com.medassist.allergy.domain.Allergy;
import com.medassist.common.api.CommonDtoMapper;
import org.mapstruct.Mapper;

@Mapper(uses = CommonDtoMapper.class)
public interface AllergyMapper {

    AllergyDtos.AllergyResponse toResponse(Allergy allergy);
}
