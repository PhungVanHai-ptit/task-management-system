package com.intern.taskmanagementsystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TaskManagementSystemApplicationTests {

    @org.springframework.beans.factory.annotation.Autowired
    private com.intern.taskmanagementsystem.service.ExcelService excelService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.intern.taskmanagementsystem.repository.ProjectRepository projectRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.intern.taskmanagementsystem.repository.UserRepository userRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private com.intern.taskmanagementsystem.repository.RoleRepository roleRepository;

    @Test
    void testImportTemplateFile() throws Exception {
        java.io.File file = new java.io.File("issue-import-template.xlsx");
        System.out.println("File exists: " + file.exists() + ", length: " + file.length());
        if (!file.exists()) return;

        var adminRole = roleRepository.findByName("ADMIN").orElseGet(() ->
                roleRepository.save(com.intern.taskmanagementsystem.entity.Role.builder().name("ADMIN").build())
        );
        var user = com.intern.taskmanagementsystem.entity.User.builder()
                .email("admin@example.com")
                .fullName("Admin")
                .passwordHash("password")
                .role(adminRole)
                .build();
        user = userRepository.save(user);

        var project = com.intern.taskmanagementsystem.entity.Project.builder()
                .key("TEST")
                .name("Test Project")
                .creator(user)
                .build();
        projectRepository.save(project);

        var admin = user;

        byte[] content = java.nio.file.Files.readAllBytes(file.toPath());
        org.springframework.mock.web.MockMultipartFile mockFile = new org.springframework.mock.web.MockMultipartFile(
                "file", "issue-import-template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content
        );

        try {
            var result = excelService.importIssues(project.getId(), mockFile, admin.getId());
            org.junit.jupiter.api.Assertions.assertNotNull(result);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    void contextLoads() {
    }

}
