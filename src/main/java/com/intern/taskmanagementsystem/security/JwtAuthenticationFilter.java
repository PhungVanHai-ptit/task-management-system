package com.intern.taskmanagementsystem.security;

import com.intern.taskmanagementsystem.service.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — chạy 1 lần mỗi request.
 * Đọc header Authorization: Bearer <token>, validate, set SecurityContext.
 * Nếu không hợp lệ hoặc lệch version, không trả 401 trực tiếp mà để request đi tiếp
 * để không chặn các endpoint public (permitAll). Các endpoint cần auth sẽ được
 * AuthenticationEntryPoint xử lý trả 401 JSON chuẩn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = extractTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            try {
                // 1. Kiểm tra token có nằm trong Blacklist (đã đăng xuất) không
                String jti = jwtTokenProvider.getJtiFromToken(token);
                if (tokenBlacklistService.isBlacklisted(jti)) {
                    request.setAttribute("authErrorCode", "TOKEN_BLACKLISTED");
                    request.setAttribute("authErrorMessage", "Phiên đăng nhập đã kết thúc do đăng xuất. Vui lòng đăng nhập lại.");
                    filterChain.doFilter(request, response);
                    return;
                }

                String email = jwtTokenProvider.getEmailFromToken(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                // Tài khoản bị khóa → không set SecurityContext, đánh dấu lỗi cho EntryPoint
                if (!userDetails.isAccountNonLocked()) {
                    request.setAttribute("authErrorCode", "ACCOUNT_LOCKED");
                    request.setAttribute("authErrorMessage", "Tài khoản của bạn đã bị khóa");
                    filterChain.doFilter(request, response);
                    return;
                }

                // Email chưa xác thực → không set SecurityContext, đánh dấu lỗi cho EntryPoint
                if (!userDetails.isEnabled()) {
                    request.setAttribute("authErrorCode", "ACCOUNT_DISABLED");
                    request.setAttribute("authErrorMessage", "Tài khoản chưa được kích hoạt");
                    filterChain.doFilter(request, response);
                    return;
                }

                // Kiểm tra tokenVersion có khớp không
                if (userDetails instanceof CustomUserDetails customUser) {
                    Integer tokenVersionInJwt = jwtTokenProvider.getTokenVersionFromToken(token);
                    if (tokenVersionInJwt == null || !tokenVersionInJwt.equals(customUser.getTokenVersion())) {
                        request.setAttribute("authErrorCode", "TOKEN_VERSION_MISMATCH");
                        request.setAttribute("authErrorMessage", "Phiên đăng nhập đã hết hiệu lực do mật khẩu hoặc phiên đã thay đổi. Vui lòng đăng nhập lại.");
                        filterChain.doFilter(request, response);
                        return;
                    }
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (UsernameNotFoundException ex) {
                // User không tồn tại hoặc đã bị xóa / ẩn danh hóa
                request.setAttribute("authErrorCode", "USER_NOT_FOUND");
                request.setAttribute("authErrorMessage", "Tài khoản không tồn tại hoặc đã bị xóa");
                filterChain.doFilter(request, response);
                return;
            } catch (Exception ex) {
                log.warn("Lỗi xác thực JWT: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Trích xuất JWT từ header "Authorization: Bearer <token>".
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
