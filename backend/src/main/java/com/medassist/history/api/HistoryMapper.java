package com.medassist.history.api;

import com.medassist.common.api.CommonDtoMapper;
import com.medassist.history.domain.MedicalHistoryEntry;
import org.mapstruct.Mapper;

@Mapper(uses = CommonDtoMapper.class)
public interface HistoryMapper {

    HistoryDtos.HistoryResponse toResponse(MedicalHistoryEntry entry);
}
