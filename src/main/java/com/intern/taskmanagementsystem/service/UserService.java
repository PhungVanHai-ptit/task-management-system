package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.UserDTO;
import com.intern.taskmanagementsystem.entity.Role;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.exception.ResourceNotFoundException;
import com.intern.taskmanagementsystem.exception.UnauthorizedActionException;
import com.intern.taskmanagementsystem.mapper.UserMapper;
import com.intern.taskmanagementsystem.repository.RoleRepository;
import com.intern.taskmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * UserService: quản lý user (chỉ ADMIN).
 * Demo kiến thức: @Transactional, soft delete, IoC, MapStruct.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final RefreshTokenService refreshTokenService;
    private final ImageStorageService imageStorageService;

    /**
     * Danh sách user, phân trang. Filter theo role, keyword, status nếu có.
     */
    public Page<UserDTO.Response> listUsers(String roleName, String keyword, String status, Pageable pageable) {
        return userRepository.findUsersWithFilters(roleName, keyword, status, pageable)
                .map(userMapper::toUserResponse);
    }

    /**
     * Xem chi tiết 1 user.
     */
    public UserDTO.Response getUser(Long userId) {
        User user = findUserById(userId);
        return userMapper.toUserResponse(user);
    }

    /**
     * Khoá tài khoản — set is_locked = true.
    /**
     * Khóa tài khoản — không cho Admin tự khóa chính mình.
     */
    @Transactional
    public void lockUser(Long userId, Long currentAdminId) {
        if (userId.equals(currentAdminId)) {
            throw new UnauthorizedActionException("Admin không thể tự khóa tài khoản của chính mình");
        }
        User user = findUserById(userId);
        user.setLocked(true);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        // Force logout: xóa tất cả refresh token và vô hiệu hóa access token cũ
        refreshTokenService.revokeAllByUser(user);
        log.info("Locked user id={} by admin id={}, new tokenVersion={}", userId, currentAdminId, user.getTokenVersion());
    }

    /**
     * Mở khoá tài khoản — set is_locked = false.
     */
    @Transactional
    public void unlockUser(Long userId) {
        User user = findUserById(userId);
        user.setLocked(false);
        userRepository.save(user);
        log.info("Unlocked user id={}", userId);
    }

    /**
     * Gán role hệ thống cho user — không cho Admin tự gỡ quyền ADMIN của chính mình.
     */
    @Transactional
    public UserDTO.Response assignRoles(Long userId, UserDTO.AssignRolesRequest request, Long currentAdminId) {
        if (userId.equals(currentAdminId) && !request.getRoleName().equalsIgnoreCase("ADMIN")) {
            throw new UnauthorizedActionException("Admin không thể tự gỡ quyền ADMIN của chính mình");
        }
        User user = findUserById(userId);

        Role role = roleRepository.findByName(request.getRoleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleName()));

        user.setRole(role);
        userRepository.save(user);
        return userMapper.toUserResponse(user);
    }

    /**
     * Soft delete & Anonymize (theo chuẩn Jira & GDPR):
     * 1. Chặn Admin tự xóa chính mình.
     * 2. Khóa tài khoản (isLocked = true).
     * 3. Ẩn danh hóa thông tin cá nhân (PII):
     *    - fullName -> "Former User"
     *    - email -> "deleted-{userId}@anonymized.local" (giải phóng email thật 100%)
     *    - passwordHash -> "" (xóa hash mật khẩu để không thể đăng nhập)
     * 4. Giữ nguyên User ID trong DB để bảo toàn lịch sử Issues, Comments, Logs.
     */
    @Transactional
    public void deleteUser(Long userId, Long currentAdminId) {
        if (userId.equals(currentAdminId)) {
            throw new UnauthorizedActionException("Admin không thể tự xóa tài khoản của chính mình");
        }
        User user = findUserById(userId);
        // Force logout trước khi anonymize
        refreshTokenService.revokeAllByUser(user);
        // Xóa file avatar trên disk nếu có
        if (user.getAvatarUrl() != null) {
            imageStorageService.deleteDiskFile(user.getAvatarUrl());
        }
        user.setLocked(true);
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setDeletedAt(LocalDateTime.now());
        user.setFullName("Former User");
        user.setEmail("deleted-" + userId + "@anonymized.local");
        user.setPasswordHash("");
        user.setAvatarUrl(null);
        userRepository.save(user);
        log.info("Anonymized and soft-deleted user id={} by admin id={}, new tokenVersion={}", userId, currentAdminId, user.getTokenVersion());
    }

    /**
     * Cập nhật thông tin cá nhân (đổi tên hiển thị).
     */
    @Transactional
    public UserDTO.Response updateProfile(Long userId, UserDTO.UpdateProfileRequest request) {
        User user = findUserById(userId);
        user.setFullName(request.getFullName().trim());
        userRepository.save(user);
        log.info("Profile updated for user id={}, new fullName={}", userId, user.getFullName());
        return userMapper.toUserResponse(user);
    }

    /**
     * Cập nhật cài đặt thông báo (bật/tắt tắt thông báo công việc qua email).
     */
    @Transactional
    public UserDTO.Response updateNotificationSettings(Long userId, UserDTO.UpdateNotificationSettingsRequest request) {
        User user = findUserById(userId);
        user.setMuteTaskEmails(request.getMuteTaskEmails());
        userRepository.save(user);
        log.info("Updated notification settings for user id={}, muteTaskEmails={}", userId, user.isMuteTaskEmails());
        return userMapper.toUserResponse(user);
    }

    /**
     * Upload avatar cho user:
     * - Xóa file avatar cũ trên disk nếu tồn tại
     * - Lưu file avatar mới lên disk (avatars)
     * - Cập nhật avatar_url trong bảng users
     */
    @Transactional
    public String uploadAvatar(Long userId, org.springframework.web.multipart.MultipartFile file) {
        User user = findUserById(userId);

        if (user.getAvatarUrl() != null) {
            imageStorageService.deleteDiskFile(user.getAvatarUrl());
        }

        String newUrl = imageStorageService.storeDiskFile(file, "avatars");
        user.setAvatarUrl(newUrl);
        userRepository.save(user);
        log.info("Uploaded avatar for user id={}, url={}", userId, newUrl);
        return newUrl;
    }

    /**
     * Xóa avatar của user:
     * - Xóa file avatar trên disk nếu tồn tại
     * - Reset avatar_url về null
     */
    @Transactional
    public void deleteAvatar(Long userId) {
        User user = findUserById(userId);

        if (user.getAvatarUrl() != null) {
            imageStorageService.deleteDiskFile(user.getAvatarUrl());
            user.setAvatarUrl(null);
            userRepository.save(user);
            log.info("Deleted avatar for user id={}", userId);
        }
    }

    // ----------------------------------------------------------------
    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    public UserDTO.Response toUserResponse(User user) {
        return userMapper.toUserResponse(user);
    }
}
