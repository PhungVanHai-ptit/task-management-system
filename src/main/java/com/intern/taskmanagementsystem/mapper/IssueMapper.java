package com.intern.taskmanagementsystem.mapper;

import com.intern.taskmanagementsystem.dto.IssueDTO;
import com.intern.taskmanagementsystem.entity.Issue;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class, LabelMapper.class})
public interface IssueMapper {

    @Mapping(target = "projectKey", source = "project.key")
    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "projectName", source = "project.name")
    @Mapping(target = "images", ignore = true)
    IssueDTO.Response toIssueResponse(Issue issue);
}
