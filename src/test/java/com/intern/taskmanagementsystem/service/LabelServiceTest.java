package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.LabelDTO;
import com.intern.taskmanagementsystem.entity.Issue;
import com.intern.taskmanagementsystem.entity.Label;
import com.intern.taskmanagementsystem.exception.DuplicateResourceException;
import com.intern.taskmanagementsystem.exception.ResourceNotFoundException;
import com.intern.taskmanagementsystem.mapper.LabelMapper;
import com.intern.taskmanagementsystem.repository.LabelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabelServiceTest {

    @Mock
    private LabelRepository labelRepository;

    @Mock
    private LabelMapper labelMapper;

    @InjectMocks
    private LabelService labelService;

    private Label bugLabel;
    private Label featureLabel;

    @BeforeEach
    void setUp() {
        bugLabel = Label.builder().id(1L).name("bug").issues(new HashSet<>()).build();
        featureLabel = Label.builder().id(2L).name("feature").issues(new HashSet<>()).build();
    }

    @Test
    @DisplayName("listLabels: trả về danh sách label")
    void testListLabels() {
        when(labelRepository.findAll()).thenReturn(List.of(bugLabel, featureLabel));
        when(labelMapper.toLabelResponseList(any())).thenReturn(List.of(
                new LabelDTO.Response(1L, "bug"),
                new LabelDTO.Response(2L, "feature")
        ));

        List<LabelDTO.Response> result = labelService.listLabels();

        assertEquals(2, result.size());
        verify(labelRepository).findAll();
    }

    @Test
    @DisplayName("createLabel: tạo thành công khi tên chưa tồn tại")
    void testCreateLabel_Success() {
        LabelDTO.CreateRequest request = new LabelDTO.CreateRequest("hotfix");
        when(labelRepository.findByName("hotfix")).thenReturn(Optional.empty());
        Label newLabel = Label.builder().name("hotfix").build();
        when(labelMapper.toEntity(request)).thenReturn(newLabel);
        when(labelRepository.save(any(Label.class))).thenReturn(Label.builder().id(3L).name("hotfix").build());
        when(labelMapper.toLabelResponse(any())).thenReturn(new LabelDTO.Response(3L, "hotfix"));

        LabelDTO.Response response = labelService.createLabel(request);

        assertNotNull(response);
        assertEquals("hotfix", response.getName());
    }

    @Test
    @DisplayName("createLabel: ném DuplicateResourceException khi tên trùng")
    void testCreateLabel_Duplicate() {
        LabelDTO.CreateRequest request = new LabelDTO.CreateRequest("bug");
        when(labelRepository.findByName("bug")).thenReturn(Optional.of(bugLabel));

        assertThrows(DuplicateResourceException.class, () -> labelService.createLabel(request));
        verify(labelRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateLabel: cập nhật thành công với cùng tên của chính nó")
    void testUpdateLabel_SameNameSuccess() {
        LabelDTO.CreateRequest request = new LabelDTO.CreateRequest("bug");
        when(labelRepository.findById(1L)).thenReturn(Optional.of(bugLabel));
        when(labelRepository.findByName("bug")).thenReturn(Optional.of(bugLabel));
        when(labelRepository.save(any(Label.class))).thenReturn(bugLabel);
        when(labelMapper.toLabelResponse(any())).thenReturn(new LabelDTO.Response(1L, "bug"));

        LabelDTO.Response response = labelService.updateLabel(1L, request);

        assertEquals("bug", response.getName());
        verify(labelRepository).save(bugLabel);
    }

    @Test
    @DisplayName("updateLabel: ném DuplicateResourceException khi tên bị trùng với label khác")
    void testUpdateLabel_DuplicateOtherLabel() {
        LabelDTO.CreateRequest request = new LabelDTO.CreateRequest("feature");
        when(labelRepository.findById(1L)).thenReturn(Optional.of(bugLabel));
        when(labelRepository.findByName("feature")).thenReturn(Optional.of(featureLabel)); // ID 2 khác ID 1

        assertThrows(DuplicateResourceException.class, () -> labelService.updateLabel(1L, request));
        verify(labelRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteLabel: gỡ label khỏi các issue và xóa thành công")
    void testDeleteLabel_Success() {
        Issue issue = Issue.builder().labels(new HashSet<>(Set.of(bugLabel))).build();
        issue.setId(10L);
        bugLabel.getIssues().add(issue);

        when(labelRepository.findById(1L)).thenReturn(Optional.of(bugLabel));

        labelService.deleteLabel(1L);

        assertFalse(issue.getLabels().contains(bugLabel));
        verify(labelRepository).delete(bugLabel);
    }

    @Test
    @DisplayName("deleteLabel: ném ResourceNotFoundException khi id không tồn tại")
    void testDeleteLabel_NotFound() {
        when(labelRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> labelService.deleteLabel(999L));
        verify(labelRepository, never()).delete(any());
    }
}
