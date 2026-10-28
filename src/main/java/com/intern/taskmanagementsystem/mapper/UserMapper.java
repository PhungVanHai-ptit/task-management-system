package com.intern.taskmanagementsystem.mapper;

import com.intern.taskmanagementsystem.dto.UserDTO;
import com.intern.taskmanagementsystem.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "role", source = "role.name")
    UserDTO.Response toUserResponse(User user);

    UserDTO.SummaryResponse toUserSummaryResponse(User user);

    List<UserDTO.Response> toUserResponseList(List<User> users);
}
