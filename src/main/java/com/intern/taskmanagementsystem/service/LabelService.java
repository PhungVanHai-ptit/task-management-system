package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.LabelDTO;
import com.intern.taskmanagementsystem.entity.Label;
import com.intern.taskmanagementsystem.exception.DuplicateResourceException;
import com.intern.taskmanagementsystem.exception.ResourceNotFoundException;
import com.intern.taskmanagementsystem.mapper.LabelMapper;
import com.intern.taskmanagementsystem.repository.LabelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LabelService {

    private final LabelRepository labelRepository;
    private final LabelMapper labelMapper;

    public List<LabelDTO.Response> listLabels() {
        return labelMapper.toLabelResponseList(labelRepository.findAll());
    }

    @Transactional
    public LabelDTO.Response createLabel(LabelDTO.CreateRequest request) {
        if (labelRepository.findByName(request.getName()).isPresent()) {
            throw new DuplicateResourceException("Label already exists: " + request.getName());
        }
        Label label = labelMapper.toEntity(request);
        return labelMapper.toLabelResponse(labelRepository.save(label));
    }

    @Transactional
    public LabelDTO.Response updateLabel(Long labelId, LabelDTO.CreateRequest request) {
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new ResourceNotFoundException("Label", labelId));
        labelRepository.findByName(request.getName()).ifPresent(existing -> {
            if (!existing.getId().equals(labelId)) {
                throw new DuplicateResourceException("Label name already exists: " + request.getName());
            }
        });
        label.setName(request.getName());
        return labelMapper.toLabelResponse(labelRepository.save(label));
    }

    @Transactional
    public void deleteLabel(Long labelId) {
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new ResourceNotFoundException("Label", labelId));
        if (label.getIssues() != null) {
            for (com.intern.taskmanagementsystem.entity.Issue issue : label.getIssues()) {
                issue.getLabels().remove(label);
            }
        }
        labelRepository.delete(label);
    }

    public LabelDTO.Response toLabelResponse(Label label) {
        return labelMapper.toLabelResponse(label);
    }
}
