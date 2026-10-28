package com.intern.taskmanagementsystem.repository;

import com.intern.taskmanagementsystem.entity.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, Long> {

    boolean existsByJti(String jti);

    @Query("SELECT b FROM BlacklistedToken b WHERE b.expiresAt > :now")
    List<BlacklistedToken> findAllActive(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM BlacklistedToken b WHERE b.expiresAt < :now")
    int deleteAllExpiredBefore(@Param("now") LocalDateTime now);
}
