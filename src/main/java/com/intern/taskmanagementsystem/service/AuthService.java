package com.intern.taskmanagementsystem.service;

import com.intern.taskmanagementsystem.dto.AuthDTO;
import com.intern.taskmanagementsystem.entity.PasswordResetToken;
import com.intern.taskmanagementsystem.entity.Role;
import com.intern.taskmanagementsystem.entity.User;
import com.intern.taskmanagementsystem.entity.VerificationToken;
import com.intern.taskmanagementsystem.enums.SystemRole;
import com.intern.taskmanagementsystem.exception.BadCredentialsException;
import com.intern.taskmanagementsystem.exception.CaptchaRequiredException;
import com.intern.taskmanagementsystem.exception.DuplicateResourceException;
import com.intern.taskmanagementsystem.exception.LoginFailedException;
import com.intern.taskmanagementsystem.exception.ResourceNotFoundException;
import com.intern.taskmanagementsystem.repository.PasswordResetTokenRepository;
import com.intern.taskmanagementsystem.repository.RoleRepository;
import com.intern.taskmanagementsystem.repository.UserRepository;
import com.intern.taskmanagementsystem.repository.VerificationTokenRepository;
import com.intern.taskmanagementsystem.security.CustomUserDetails;
import com.intern.taskmanagementsystem.security.JwtTokenProvider;
import com.intern.taskmanagementsystem.messaging.EmailVerificationMessage;
import com.intern.taskmanagementsystem.messaging.PasswordResetMessage;
import com.intern.taskmanagementsystem.messaging.NotificationProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuthService xử lý đăng ký, xác thực email, đăng nhập, refresh token,
 * logout, đổi mật khẩu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository              userRepository;
    private final RoleRepository              roleRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder             passwordEncoder;
    private final AuthenticationManager       authenticationManager;
    private final JwtTokenProvider            jwtTokenProvider;
    private final NotificationProducer        notificationProducer;
    private final RefreshTokenService         refreshTokenService;
    private final CaptchaService              captchaService;
    private final LoginAttemptService         loginAttemptService;
    private final TokenBlacklistService       tokenBlacklistService;

    @Value("${app.jwt.expiration-ms:900000}")
    private long jwtExpirationMs;

    private static final long VERIFICATION_TOKEN_HOURS = 24;

    // ----------------------------------------------------------------
    // Đăng ký
    // ----------------------------------------------------------------

    @Transactional
    public void register(AuthDTO.RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateResourceException("Email already registered: " + request.getEmail());
        }

        Role userRole = roleRepository.findByName(SystemRole.USER.name())
                .orElseThrow(() -> new ResourceNotFoundException("Default role USER not found in DB"));

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .isLocked(false)
                .emailVerified(false)
                .role(userRole)
                .build();

        userRepository.save(user);

        String token = createVerificationToken(user);
        notificationProducer.sendEmailVerification(EmailVerificationMessage.builder()
                .toEmail(user.getEmail())
                .fullName(user.getFullName())
                .token(token)
                .build());

        log.info("Registered new user: {}", request.getEmail());
    }

    // ----------------------------------------------------------------
    // Xác thực email
    // ----------------------------------------------------------------

    @Transactional
    public String verifyEmail(String token) {
        return verificationTokenRepository.findByToken(token)
                .map(vt -> {
                    if (vt.isExpired()) {
                        verificationTokenRepository.delete(vt);
                        log.warn("Verification token expired for user id={}", vt.getUser().getId());
                        return "expired";
                    }
                    User user = vt.getUser();
                    user.setEmailVerified(true);
                    userRepository.save(user);
                    verificationTokenRepository.delete(vt);
                    log.info("Email verified for user id={}", user.getId());
                    return "verified";
                })
                .orElse("invalid");
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("Email đã được xác thực rồi");
        }

        verificationTokenRepository.deleteByUser(user);
        String token = createVerificationToken(user);
        notificationProducer.sendEmailVerification(EmailVerificationMessage.builder()
                .toEmail(user.getEmail())
                .fullName(user.getFullName())
                .token(token)
                .build());

        log.info("Resent verification email to {}", email);
    }

    // ----------------------------------------------------------------
    // Đăng nhập — trả cả accessToken + refreshToken
    // ----------------------------------------------------------------

    /**
     * Đăng nhập:
     * Spring Security tự check isAccountNonLocked() và isEnabled().
     * Nếu pass → tạo access token (15 phút) + refresh token (7 ngày).
     */
    @Transactional
    public AuthDTO.AuthResponse login(AuthDTO.LoginRequest request, String clientIp, String deviceInfo) {
        String email = request.getEmail() != null ? request.getEmail().trim() : "";

        // 1. Kiểm tra captcha trước khi kiểm tra mật khẩu
        // Nếu IP sai >= 10 lần hoặc Email sai >= 5 lần, bắt buộc phải có mã Captcha hợp lệ
        if (loginAttemptService.isCaptchaRequired(clientIp, email)) {
            if (request.getCaptchaId() == null || request.getCaptchaId().isBlank() ||
                request.getCaptchaCode() == null || request.getCaptchaCode().isBlank()) {
                throw new CaptchaRequiredException("Hệ thống yêu cầu nhập mã xác thực (captcha) trước khi tiếp tục.");
            }
            if (!captchaService.validateAndConsume(request.getCaptchaId(), request.getCaptchaCode())) {
                throw new CaptchaRequiredException("Mã xác thực không chính xác hoặc đã hết hạn.");
            }
        }

        // 2. Kiểm tra tồn tại tài khoản
        User userEntity = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    int emailAttempts = loginAttemptService.recordFailedAttempt(clientIp, email);
                    boolean needCaptcha = loginAttemptService.isCaptchaRequired(clientIp, email);
                    int remaining = Math.max(0, LoginAttemptService.MAX_FAILED_ATTEMPTS_EMAIL - emailAttempts);
                    String msg = needCaptcha
                            ? "Đăng nhập thất bại quá nhiều lần. Vui lòng nhập mã xác thực (captcha) để tiếp tục."
                            : ("Email này chưa tồn tại trong hệ thống. Còn " + remaining + " lần thử.");
                    return new LoginFailedException(msg, needCaptcha, remaining);
                });

        // 3. Xác thực mật khẩu qua Spring Security
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );

            // Đăng nhập thành công → CHỈ reset số lần nhập sai của EMAIL, KHÔNG reset của IP
            loginAttemptService.resetEmailAttempts(email);

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", userDetails.getId()));

            String accessToken  = jwtTokenProvider.generateToken(userDetails);
            String refreshToken = refreshTokenService.createToken(user, deviceInfo);

            return AuthDTO.AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(jwtExpirationMs)
                    .userId(userDetails.getId())
                    .email(userDetails.getEmail())
                    .build();
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            int emailAttempts = loginAttemptService.recordFailedAttempt(clientIp, email);
            boolean needCaptcha = loginAttemptService.isCaptchaRequired(clientIp, email);
            int remaining = Math.max(0, LoginAttemptService.MAX_FAILED_ATTEMPTS_EMAIL - emailAttempts);
            String msg = needCaptcha
                    ? "Bạn đã nhập sai mật khẩu quá số lần quy định. Vui lòng nhập mã xác thực (captcha) để tiếp tục."
                    : ("Mật khẩu không chính xác. Còn " + remaining + " lần thử.");
            throw new LoginFailedException(msg, needCaptcha, remaining);
        }
    }

    // ----------------------------------------------------------------
    // Refresh token — cấp access token mới + xoay refresh token
    // ----------------------------------------------------------------

    /**
     * Dùng refresh token cũ để cấp access token mới + refresh token mới (rotate).
     * Kiểm tra: token hợp lệ + user không bị locked + email đã xác thực.
     */
    @Transactional
    public AuthDTO.RefreshResponse refresh(String rawRefreshToken) {
        // verifyAndRotate: validate + xóa cũ + tạo mới → trả raw token mới
        String newRawRefreshToken = refreshTokenService.verifyAndRotate(rawRefreshToken);

        // Lấy user từ token hash mới (token đã được tạo trong verifyAndRotate)
        // → Cần lấy user để generate JWT → tìm qua email từ refreshToken cũ
        // (Dùng cách: load user từ tokenHash mới — đã có trong DB sau rotate)
        String newHash = refreshTokenService.hashToken(newRawRefreshToken);
        User user = refreshTokenService.getUserByTokenHash(newHash);

        // Load CustomUserDetails để generate JWT
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newAccessToken = jwtTokenProvider.generateToken(userDetails);

        return AuthDTO.RefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs)
                .build();
    }

    // ----------------------------------------------------------------
    // Logout
    // ----------------------------------------------------------------

    /**
     * Đăng xuất 1 session — revoke refresh token hiện tại và đưa access token vào blacklist.
     * rawAccessToken là tuỳ chọn (có thể null hoặc đã hết hạn từ trước).
     */
    @Transactional
    public void logout(String rawRefreshToken, String rawAccessToken) {
        if (StringUtils.hasText(rawAccessToken)) {
            tokenBlacklistService.blacklistToken(rawAccessToken);
        }
        refreshTokenService.revokeToken(rawRefreshToken);
        log.info("User logged out (revoked 1 session, access token blacklisted if valid)");
    }

    /**
     * Đăng xuất tương thích ngược khi không truyền Access Token.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        logout(rawRefreshToken, null);
    }

    /**
     * Đăng xuất tất cả thiết bị — revoke mọi refresh token của user.
     */
    @Transactional
    public void logoutAll(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshTokenService.revokeAllByUser(user);
        log.info("User id={} logged out from all devices (tokenVersion={})", userId, user.getTokenVersion());
    }

    // ----------------------------------------------------------------
    // Đổi mật khẩu
    // ----------------------------------------------------------------

    @Transactional
    public AuthDTO.AuthResponse changePassword(Long userId, AuthDTO.ChangePasswordRequest request, String deviceInfo) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // Thu hồi toàn bộ phiên đăng nhập cũ trên mọi thiết bị
        refreshTokenService.revokeAllByUser(user);

        // Cấp cặp token mới cho phiên hiện tại để không làm gián đoạn người dùng
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newAccessToken  = jwtTokenProvider.generateToken(userDetails);
        String newRefreshToken = refreshTokenService.createToken(user, deviceInfo);

        log.info("Password changed and tokens regenerated for user id={}, newTokenVersion={}", userId, user.getTokenVersion());

        return AuthDTO.AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs)
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    // ----------------------------------------------------------------
    // Quên mật khẩu & Đặt lại mật khẩu
    // ----------------------------------------------------------------

    private static final long RESET_PASSWORD_TOKEN_MINUTES = 15;

    /**
     * Yêu cầu link đặt lại mật khẩu:
     * - Kiểm tra user tồn tại & đã xác thực email
     * - Xóa token cũ nếu có
     * - Tạo token mới (TTL 15 phút)
     * - Gửi message qua JMS để async gửi mail chứa link đổi mật khẩu
     */
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Email này chưa tồn tại trong hệ thống."));

        if (!user.isEmailVerified()) {
            throw new IllegalArgumentException("Tài khoản chưa được kích hoạt. Vui lòng kích hoạt tài khoản trước khi đặt lại mật khẩu.");
        }

        passwordResetTokenRepository.deleteByUser(user);

        String token = UUID.randomUUID().toString();
        PasswordResetToken prt = PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(RESET_PASSWORD_TOKEN_MINUTES))
                .build();
        passwordResetTokenRepository.save(prt);

        notificationProducer.sendPasswordReset(PasswordResetMessage.builder()
                .toEmail(user.getEmail())
                .fullName(user.getFullName())
                .token(token)
                .build());

        log.info("Password reset email queued for user {}", email);
    }

    /**
     * Kiểm tra token đặt lại mật khẩu có hợp lệ và còn hạn hay không.
     */
    @Transactional(readOnly = true)
    public boolean validateResetToken(String token) {
        return passwordResetTokenRepository.findByToken(token)
                .filter(t -> !t.isExpired())
                .isPresent();
    }

    /**
     * Đặt lại mật khẩu bằng token:
     * - Kiểm tra tính hợp lệ và hạn của token
     * - Cập nhật mật khẩu mã hóa mới
     * - Xóa token đã sử dụng
     * - Revoke toàn bộ refresh token / đăng xuất trên mọi thiết bị
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Link đặt lại mật khẩu không hợp lệ hoặc đã được sử dụng."));

        if (resetToken.isExpired()) {
            passwordResetTokenRepository.delete(resetToken);
            throw new IllegalArgumentException("Link đặt lại mật khẩu đã hết hạn. Vui lòng gửi lại yêu cầu mới.");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        // Xóa token sau khi đổi mật khẩu thành công
        passwordResetTokenRepository.delete(resetToken);

        // Thu hồi toàn bộ phiên đăng nhập cũ
        refreshTokenService.revokeAllByUser(user);

        log.info("Password successfully reset for user id={}, newTokenVersion={}", user.getId(), user.getTokenVersion());
    }



    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private String createVerificationToken(User user) {
        String tokenValue = UUID.randomUUID().toString();
        VerificationToken vt = VerificationToken.builder()
                .token(tokenValue)
                .user(user)
                .expiryDate(LocalDateTime.now().plusHours(VERIFICATION_TOKEN_HOURS))
                .build();
        verificationTokenRepository.save(vt);
        return tokenValue;
    }
}
