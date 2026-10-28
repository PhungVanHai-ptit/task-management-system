package com.intern.taskmanagementsystem.security;

import com.intern.taskmanagementsystem.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Wrapper của User entity cho Spring Security.
 * - isAccountNonLocked() → !isLocked  → ném LockedException nếu tài khoản bị khóa
 * - isEnabled()          → emailVerified → ném DisabledException nếu chưa xác thực email
 * Demo kiến thức: interface (UserDetails), final fields.
 */
public class CustomUserDetails implements UserDetails {

    @Getter
    private final Long id;

    @Getter
    private final String email;

    private final String password;

    private final boolean locked;

    private final boolean emailVerified;

    @Getter
    private final Integer tokenVersion;

    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.id            = user.getId();
        this.email         = user.getEmail();
        this.password      = user.getPasswordHash();
        this.locked        = user.isLocked();
        this.emailVerified = user.isEmailVerified();
        this.tokenVersion  = user.getTokenVersion() != null ? user.getTokenVersion() : 1;
        this.authorities   = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** Trả false nếu tài khoản bị khóa → Spring ném LockedException */
    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** Trả false nếu email chưa xác thực → Spring ném DisabledException */
    @Override
    public boolean isEnabled() {
        return emailVerified;
    }
}
