package com.intern.taskmanagementsystem.repository;

import com.intern.taskmanagementsystem.entity.PasswordResetToken;
import com.intern.taskmanagementsystem.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository cho bảng password_reset_tokens.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findByUser(User user);

    /**
     * Xóa token cũ của user trước khi tạo token mới.
     */
    void deleteByUser(User user);
}
