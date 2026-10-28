package com.intern.taskmanagementsystem.controller;

import com.intern.taskmanagementsystem.dto.LabelDTO;
import com.intern.taskmanagementsystem.service.LabelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Label endpoints — /api/labels
 */
@RestController
@RequestMapping("/api/labels")
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;

    @GetMapping
    public ResponseEntity<List<LabelDTO.Response>> listLabels() {
        return ResponseEntity.ok(labelService.listLabels());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabelDTO.Response> createLabel(@Valid @RequestBody LabelDTO.CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(labelService.createLabel(request));
    }

    @PutMapping("/{labelId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LabelDTO.Response> updateLabel(
            @PathVariable Long labelId,
            @Valid @RequestBody LabelDTO.CreateRequest request
    ) {
        return ResponseEntity.ok(labelService.updateLabel(labelId, request));
    }

    @DeleteMapping("/{labelId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteLabel(@PathVariable Long labelId) {
        labelService.deleteLabel(labelId);
        return ResponseEntity.noContent().build();
    }
}
