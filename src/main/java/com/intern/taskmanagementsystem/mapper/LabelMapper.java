package com.intern.taskmanagementsystem.mapper;

import com.intern.taskmanagementsystem.dto.LabelDTO;
import com.intern.taskmanagementsystem.entity.Label;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface LabelMapper {

    LabelDTO.Response toLabelResponse(Label label);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "issues", ignore = true)
    Label toEntity(LabelDTO.CreateRequest request);

    List<LabelDTO.Response> toLabelResponseList(List<Label> labels);

    Set<LabelDTO.Response> toLabelResponseSet(Set<Label> labels);
}
