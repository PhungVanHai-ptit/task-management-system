package com.intern.taskmanagementsystem.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intern.taskmanagementsystem.enums.SystemRole;
import com.intern.taskmanagementsystem.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.time.LocalDateTime;

/**
 * Spring Security configuration.
 * - Stateless (JWT, không dùng session)
 * - Permit /api/auth/** publicly
 * - @EnableMethodSecurity: cho phép @PreAuthorize ở Service/Controller level
 * Demo kiến thức: spring-security + authentication-authorization.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers("/*.html", "/css/**", "/js/**", "/lang/**", "/images/**", "/uploads/**", "/favicon.ico");
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // 1. Cho phép tất cả tài nguyên tĩnh và trang HTML giao diện
                        .requestMatchers(
                                "/",
                                "/*.html",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/lang/**",
                                "/uploads/**",
                                "/favicon.ico"
                        ).permitAll()
                        // 2. Public API endpoints
                        .requestMatchers(
                                "/api/auth/captcha",
                                "/api/auth/captcha-required",
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",          // không cần JWT
                                "/api/auth/logout",           // dùng refreshToken, không cần JWT
                                "/api/auth/verify-email",
                                "/api/auth/resend-verification",
                                "/api/auth/forgot-password",
                                "/api/auth/validate-reset-token",
                                "/api/auth/reset-password"
                        ).permitAll()
                        // 3. Quản lý User: các thao tác chỉnh sửa / xóa / khóa / phân quyền cần ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/users/**").hasRole(SystemRole.ADMIN.name())
                        .requestMatchers(HttpMethod.PUT, "/api/users/**").hasRole(SystemRole.ADMIN.name())
                        .requestMatchers(HttpMethod.PATCH, "/api/users/**").hasRole(SystemRole.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole(SystemRole.ADMIN.name())
                        // 4. Đọc danh sách User (để gán issue, thêm thành viên project): bất kỳ user đã đăng nhập
                        .requestMatchers(HttpMethod.GET, "/api/users/**").authenticated()
                        // 5. Reports cần authenticate
                        .requestMatchers("/api/reports/**").authenticated()
                        // 6. Cho phép public truy cập đọc chi tiết Issue và Comments (IssueService/CommentService sẽ kiểm tra nếu Issue là Private)
                        .requestMatchers(HttpMethod.GET, "/api/projects/*/issues/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/issues/*/comments").permitAll()
                        // 7. Tất cả endpoints API còn lại cần authenticate
                        .requestMatchers("/api/**").authenticated()
                        // 7. Mọi request khác (fallback tĩnh) đều cho phép
                        .anyRequest().permitAll()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Khi request không có/hết hạn JWT → trả 401 JSON (không phải 403 redirect)
                // Frontend interceptor sẽ bắt 401 và tự động gọi /refresh
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(apiAuthenticationEntryPoint())
                );

        return http.build();
    }

    /**
     * Trả về 401 JSON thay vì redirect login page mặc định.
     * Frontend interceptor (app.js) dựa vào 401 để tự động refresh token.
     */
    @Bean
    public AuthenticationEntryPoint apiAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            String errorCode = (String) request.getAttribute("authErrorCode");
            String errorMessage = (String) request.getAttribute("authErrorMessage");

            if (errorCode == null) {
                errorCode = "UNAUTHORIZED";
            }
            if (errorMessage == null) {
                errorMessage = "Phiên đăng nhập hết hạn hoặc không hợp lệ";
            }

            ErrorResponse errorResponse = ErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .status(HttpServletResponse.SC_UNAUTHORIZED)
                    .error("Unauthorized")
                    .code(errorCode)
                    .message(errorMessage)
                    .build();

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
        };
    }
}
