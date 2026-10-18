package com.ecommerce.platform.modules.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public class AuthenticatedUser implements UserDetails {

    private final Long id;
    private final String email;
    private final String displayName;
    private final List<String> roles;
    private final BigDecimal userDiscountPercentage;
    private final LocalDate userDiscountStartDate;
    private final LocalDate userDiscountEndDate;
    private final boolean enabled;
    private final boolean accountNonLocked;

    public AuthenticatedUser(Long id,
                             String email,
                             String displayName,
                             List<String> roles,
                             BigDecimal userDiscountPercentage,
                             LocalDate userDiscountStartDate,
                             LocalDate userDiscountEndDate,
                             boolean enabled,
                             boolean accountNonLocked) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.roles = List.copyOf(roles);
        this.userDiscountPercentage = userDiscountPercentage;
        this.userDiscountStartDate = userDiscountStartDate;
        this.userDiscountEndDate = userDiscountEndDate;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getRoles() {
        return roles;
    }

    public BigDecimal getUserDiscountPercentage() {
        return userDiscountPercentage;
    }

    public LocalDate getUserDiscountStartDate() {
        return userDiscountStartDate;
    }

    public LocalDate getUserDiscountEndDate() {
        return userDiscountEndDate;
    }

    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(roleName::equals);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
