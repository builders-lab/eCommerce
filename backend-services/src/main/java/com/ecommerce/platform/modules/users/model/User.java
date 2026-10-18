package com.ecommerce.platform.modules.users.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(name = "username")
    private String displayName;

    @Column(name = "is_flagged")
    private boolean isFlagged = false;

    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "user_discount_percentage", precision = 5, scale = 2)
    private BigDecimal userDiscountPercentage = BigDecimal.ZERO;

    @Column(name = "user_discount_start_date")
    private LocalDate userDiscountStartDate;

    @Column(name = "user_discount_end_date")
    private LocalDate userDiscountEndDate;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .toList();
    }

    @Override
    public String getUsername() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !isFlagged;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasRole(String roleName) {
        return roles.stream().anyMatch(role -> roleName.equals(role.getName()));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setFlagged(boolean flagged) {
        isFlagged = flagged;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFlagged() {
        return isFlagged;
    }

    public BigDecimal getUserDiscountPercentage() {
        return userDiscountPercentage;
    }

    public void setUserDiscountPercentage(BigDecimal userDiscountPercentage) {
        this.userDiscountPercentage = userDiscountPercentage;
    }

    public LocalDate getUserDiscountStartDate() {
        return userDiscountStartDate;
    }

    public void setUserDiscountStartDate(LocalDate userDiscountStartDate) {
        this.userDiscountStartDate = userDiscountStartDate;
    }

    public LocalDate getUserDiscountEndDate() {
        return userDiscountEndDate;
    }

    public void setUserDiscountEndDate(LocalDate userDiscountEndDate) {
        this.userDiscountEndDate = userDiscountEndDate;
    }
}
