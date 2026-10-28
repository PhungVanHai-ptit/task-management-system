package com.intern.taskmanagementsystem.repository;

import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository cho bảng verification_tokens.
 */
@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByToken(String token);

    Optional<VerificationToken> findByUser(User user);

    /**
     * Xóa token cũ trước khi tạo token mới (dùng trong resendVerification).
     */
    void deleteByUser(User user);
}
