package com.intern.taskmanagementsystem.mapper;

import com.intern.taskmanagementsystem.dto.IssueChangeLogDTO;
import com.intern.taskmanagementsystem.entity.IssueChangeLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface IssueChangeLogMapper {

    @Mapping(target = "issueId", source = "issue.id")
    @Mapping(target = "oldAssignee", ignore = true)
    @Mapping(target = "newAssignee", ignore = true)
    IssueChangeLogDTO toDTO(IssueChangeLog log);
}
