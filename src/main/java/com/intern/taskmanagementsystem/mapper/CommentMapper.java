package com.intern.taskmanagementsystem.mapper;

import com.intern.taskmanagementsystem.dto.CommentDTO;
import com.intern.taskmanagementsystem.entity.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface CommentMapper {

    @Mapping(target = "images", ignore = true)
    CommentDTO.Response toCommentResponse(Comment comment);
}
