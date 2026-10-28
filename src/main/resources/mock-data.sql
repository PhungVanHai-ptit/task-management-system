-- ==============================================================================
-- Mini Jira - Task Management System
-- Mock Data Script for MySQL Database
-- All user passwords are: password123
-- (BCrypt hash: $2a$10$7EqJtq98hPqEX7fNZaFWoO.8/k0a0pL8q7vPzG5tYq2i2n.lqfF2e)
-- ==============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 1. ROLES
INSERT INTO `roles` (`id`, `name`) VALUES
(1, 'ADMIN'),
(2, 'USER')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 2. PROJECT ROLES
INSERT INTO `project_roles` (`id`, `name`) VALUES
(1, 'PROJECT_MANAGER'),
(2, 'DEVELOPER'),
(3, 'TESTER'),
(4, 'REPORTER'),
(5, 'VIEWER')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 3. LABELS
INSERT INTO `labels` (`id`, `name`) VALUES
(1, 'Backend'),
(2, 'Frontend'),
(3, 'Bug'),
(4, 'Feature'),
(5, 'Security'),
(6, 'Database'),
(7, 'DevOps')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 4. USERS
INSERT INTO `users` (`id`, `email`, `password_hash`, `full_name`, `is_active`, `token_version`, `deleted_at`, `role_id`, `created_at`, `updated_at`) VALUES
(1, 'admin@example.com', '$2a$10$3euPcmQFCiblsZeEu5s7p.9OVfmsYV9VwOaQJq1gR6w6xXF0L7WwG', 'System Administrator', 1, 1, NULL, 1, NOW(), NOW()),
(2, 'pm@example.com', '$2a$10$3euPcmQFCiblsZeEu5s7p.9OVfmsYV9VwOaQJq1gR6w6xXF0L7WwG', 'Nguyen Van PM', 1, 1, NULL, 2, NOW(), NOW()),
(3, 'dev1@example.com', '$2a$10$3euPcmQFCiblsZeEu5s7p.9OVfmsYV9VwOaQJq1gR6w6xXF0L7WwG', 'Tran Van Dev', 1, 1, NULL, 2, NOW(), NOW()),
(4, 'dev2@example.com', '$2a$10$3euPcmQFCiblsZeEu5s7p.9OVfmsYV9VwOaQJq1gR6w6xXF0L7WwG', 'Le Thi Coder', 1, 1, NULL, 2, NOW(), NOW()),
(5, 'tester@example.com', '$2a$10$3euPcmQFCiblsZeEu5s7p.9OVfmsYV9VwOaQJq1gR6w6xXF0L7WwG', 'Pham Thi Tester', 1, 1, NULL, 2, NOW(), NOW()),
(6, 'locked@example.com', '$2a$10$3euPcmQFCiblsZeEu5s7p.9OVfmsYV9VwOaQJq1gR6w6xXF0L7WwG', 'Locked Account User', 0, 1, NULL, 2, NOW(), NOW())
ON DUPLICATE KEY UPDATE `password_hash` = VALUES(`password_hash`), `email` = VALUES(`email`);

-- 5. PROJECTS
INSERT INTO `projects` (`id`, `key`, `name`, `description`, `created_at`, `updated_at`) VALUES
(1, 'PROJ', 'E-Commerce Platform', 'Sàn thương mại điện tử đa kênh tích hợp thanh toán tự động và quản lý tồn kho thời gian thực.', NOW(), NOW()),
(2, 'BANK', 'Mobile Banking App 2.0', 'Ứng dụng ngân hàng số bảo mật cao, hỗ trợ chuyển tiền Napas247 và thanh toán hoá đơn.', NOW(), NOW()),
(3, 'CRM', 'Customer Relationship Management', 'Hệ thống CRM quản lý khách hàng, tự động hoá chiến dịch email marketing qua hàng chờ Message Queue.', NOW(), NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 6. PROJECT MEMBERS
INSERT INTO `project_members` (`project_id`, `user_id`, `project_role_id`) VALUES
(1, 1, 1),
(1, 2, 1),
(1, 3, 2),
(1, 4, 2),
(1, 5, 3),
(2, 1, 1),
(2, 3, 2),
(2, 5, 3),
(3, 2, 1),
(3, 4, 2)
ON DUPLICATE KEY UPDATE `project_role_id` = VALUES(`project_role_id`);

-- 7. ISSUES
INSERT INTO `issues` (`id`, `issue_key`, `project_id`, `reporter_id`, `assignee_id`, `title`, `description`, `status`, `priority`, `due_date`, `is_public`, `created_at`, `updated_at`) VALUES
(1, 'PROJ-1', 1, 2, 3, 'Tích hợp cổng thanh toán VNPay & MoMo', 'Xây dựng module thanh toán hỗ trợ tạo URL redirect và xử lý callback IPN webhook bảo mật.', 'IN_PROGRESS', 'HIGH', DATE_ADD(NOW(), INTERVAL 5 DAY), 1, NOW(), NOW()),
(2, 'PROJ-2', 1, 5, 3, 'Lỗi giỏ hàng không lưu lại khi tải lại trang', 'Khi người dùng thêm sản phẩm vào giỏ và refresh F5 thì sản phẩm bị mất do chưa đồng bộ State vào LocalStorage.', 'TODO', 'HIGH', DATE_ADD(NOW(), INTERVAL 2 DAY), 0, NOW(), NOW()),
(3, 'PROJ-3', 1, 2, 4, 'Tối ưu truy vấn SQL danh sách sản phẩm', 'Thêm composite index (category_id, created_at) để giảm thời gian phản hồi từ 1.2s xuống dưới 50ms.', 'DONE', 'MEDIUM', DATE_SUB(NOW(), INTERVAL 1 DAY), 0, NOW(), NOW()),
(4, 'PROJ-4', 1, 2, 4, 'Thiết kế giao diện Kanban Board kéo thả', 'Dùng HTML5 Drag and Drop API để chuyển đổi trạng thái công việc mượt mà và trực quan.', 'DONE', 'HIGH', DATE_SUB(NOW(), INTERVAL 3 DAY), 0, NOW(), NOW()),
(5, 'PROJ-5', 1, 1, 3, 'Bổ sung xác thực 2 lớp (2FA) qua Email / SMS', 'Tích hợp mã OTP 6 số gửi qua ActiveMQ JMS queue khi đăng nhập từ thiết bị lạ.', 'TODO', 'HIGH', DATE_ADD(NOW(), INTERVAL 7 DAY), 0, NOW(), NOW()),
(6, 'BANK-1', 2, 1, 3, 'Triển khai API sinh mã QR chuyển tiền VietQR', 'Sinh mã chuẩn Napas 247 tương thích tất cả các app ngân hàng tại Việt Nam.', 'IN_PROGRESS', 'HIGH', DATE_ADD(NOW(), INTERVAL 3 DAY), 0, NOW(), NOW()),
(7, 'BANK-2', 2, 1, 5, 'Kiểm thử tải hệ thống thanh toán giờ cao điểm', 'Dùng Apache JMeter test tải 10,000 requests/giây để kiểm tra hiệu năng chịu tải.', 'TODO', 'HIGH', DATE_ADD(NOW(), INTERVAL 10 DAY), 0, NOW(), NOW()),
(8, 'CRM-1', 3, 2, 4, 'Cấu hình ActiveMQ gửi email marketing hàng loạt', 'Sử dụng JMS Message Listener để xử lý gửi mail bất đồng bộ không làm block request của người dùng.', 'IN_PROGRESS', 'MEDIUM', DATE_ADD(NOW(), INTERVAL 4 DAY), 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`), `is_public` = VALUES(`is_public`);

-- 8. ISSUE_LABELS
INSERT INTO `issue_labels` (`issue_id`, `label_id`) VALUES
(1, 1), (1, 4),
(2, 2), (2, 3),
(3, 1), (3, 6),
(4, 2), (4, 4),
(5, 1), (5, 5),
(6, 1), (6, 4), (6, 5),
(7, 7),
(8, 1), (8, 7)
ON DUPLICATE KEY UPDATE `label_id` = VALUES(`label_id`);

-- 9. COMMENTS
INSERT INTO `comments` (`id`, `issue_id`, `author_id`, `content`, `created_at`, `updated_at`) VALUES
(1, 1, 3, 'Đã hoàn thành kết nối sandbox với MoMo. Đang viết tiếp unit test cho IPN Webhook của VNPay.', NOW(), NOW()),
(2, 1, 2, 'Rất tốt! Nhớ kiểm tra kỹ chữ ký số checksum SHA256 để tránh giả mạo request nhé.', NOW(), NOW()),
(3, 2, 5, 'Lỗi này tái hiện 100% trên Safari và Chrome khi mở tab ẩn danh.', NOW(), NOW()),
(4, 2, 3, 'Mình đã tìm ra nguyên nhân, sẽ fix và đẩy code lên branch staging vào chiều nay.', NOW(), NOW()),
(5, 3, 4, 'Đã thêm index và test qua Explain query. Thời gian query đã giảm từ 1.2s xuống 38ms.', NOW(), NOW())
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`);

SET FOREIGN_KEY_CHECKS = 1;
