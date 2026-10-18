package com.ecommerce.platform.modules.users.service;

import com.ecommerce.platform.config.CacheNames;
import com.ecommerce.platform.modules.auth.security.AuthenticatedUser;
import com.ecommerce.platform.modules.users.dto.UserAdminDto;
import com.ecommerce.platform.modules.users.dto.request.ToggleEmployeeRoleRequest;
import com.ecommerce.platform.modules.users.dto.request.UpdateUserDiscountRequest;
import com.ecommerce.platform.modules.users.model.Role;
import com.ecommerce.platform.modules.users.model.User;
import com.ecommerce.platform.modules.users.repository.RoleRepository;
import com.ecommerce.platform.modules.users.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public Page<UserAdminDto> getUsers(int page, int size) {
        return userRepository.findAllWithRoles(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id")))
                .map(this::toAdminDto);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.USER_ROLES, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.USER_CART, key = "#id")
    })
    public UserAdminDto flagUser(Long id, AuthenticatedUser actor) {
        var target = getExistingUser(id);
        enforceManagerTargetRestrictions(actor, target);
        target.setFlagged(true);
        return toAdminDto(userRepository.save(target));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.USER_ROLES, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.USER_CART, key = "#id")
    })
    public UserAdminDto unflagUser(Long id) {
        var target = getExistingUser(id);
        target.setFlagged(false);
        return toAdminDto(userRepository.save(target));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.USER_ROLES, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.USER_CART, key = "#id")
    })
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        userRepository.deleteById(id);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.USER_ROLES, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.USER_CART, key = "#id")
    })
    public UserAdminDto updateUserDiscount(Long id, UpdateUserDiscountRequest request, AuthenticatedUser actor) {
        var target = getExistingUser(id);
        enforceManagerTargetRestrictions(actor, target);

        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate cannot be before startDate");
        }

        target.setUserDiscountPercentage(request.percentage());
        if (request.percentage().signum() == 0) {
            target.setUserDiscountStartDate(null);
            target.setUserDiscountEndDate(null);
        } else {
            target.setUserDiscountStartDate(request.startDate());
            target.setUserDiscountEndDate(request.endDate());
        }

        return toAdminDto(userRepository.save(target));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.USER_ROLES, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.USER_CART, key = "#id")
    })
    public UserAdminDto setEmployeeRole(Long id, ToggleEmployeeRoleRequest request) {
        var target = getExistingUser(id);
        var employeeRole = roleRepository.findByName("ROLE_EMPLOYEE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Role not found"));

        if (Boolean.TRUE.equals(request.enabled())) {
            target.getRoles().add(employeeRole);
        } else {
            target.getRoles().removeIf(role -> "ROLE_EMPLOYEE".equals(role.getName()));
        }

        return toAdminDto(userRepository.save(target));
    }

    private void enforceManagerTargetRestrictions(AuthenticatedUser actor, User target) {
        if (actor == null || !actor.hasRole("ROLE_MANAGER")) {
            return;
        }

        Long actorId = Objects.requireNonNull(actor.getId(), "Actor id must be present");
        Long targetId = Objects.requireNonNull(target.getId(), "Target id must be present");

        if (actorId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Managers cannot modify their own account");
        }

        if (target.hasRole("ROLE_ADMIN") || target.hasRole("ROLE_MANAGER")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Managers cannot modify privileged accounts");
        }
    }

    private User getExistingUser(Long id) {
        return userRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public UserAdminDto toAdminDto(User user) {
        return new UserAdminDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRoles().stream().map(Role::getName).toList(),
                user.isFlagged(),
                user.getUserDiscountPercentage(),
                user.getUserDiscountStartDate(),
                user.getUserDiscountEndDate()
        );
    }
}
