package com.intern.taskmanagementsystem.mapper;

import com.intern.taskmanagementsystem.dto.ProjectDTO;
import com.intern.taskmanagementsystem.entity.Project;
import com.intern.taskmanagementsystem.entity.ProjectMember;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface ProjectMapper {

    @Mapping(target = "projectRole", source = "projectRole.name")
    ProjectDTO.MemberResponse toProjectMemberResponse(ProjectMember member);

    @Mapping(target = "members", ignore = true)
    ProjectDTO.Response toProjectResponse(Project project);

    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "selfAssignPolicy", ignore = true)
    Project toEntity(ProjectDTO.CreateRequest request);
}
