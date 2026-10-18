package com.ecommerce.platform.modules.auth.service;

import com.ecommerce.platform.modules.auth.dto.AuthResponse;
import com.ecommerce.platform.modules.auth.dto.SignupRequest;
import com.ecommerce.platform.modules.auth.dto.UserDto;
import com.ecommerce.platform.modules.auth.security.JwtService;
import com.ecommerce.platform.modules.users.api.UserAccountApi;
import com.ecommerce.platform.modules.users.api.UserIdentity;
import com.ecommerce.platform.modules.users.api.UserRegistrationRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final JwtService jwtService;
    private final UserAccountApi userAccountApi;

    public AuthService(JwtService jwtService, UserAccountApi userAccountApi) {
        this.jwtService = jwtService;
        this.userAccountApi = userAccountApi;
    }

    public AuthResponse signup(SignupRequest req) {
        UserIdentity user = userAccountApi.registerUser(toRegistrationRequest(req));
        String token = jwtService.generateToken(user);
        return new AuthResponse(token, mapToDto(user));
    }

    public AuthResponse login(String email, String password) {
        UserIdentity user = userAccountApi.loadByEmailForLogin(email);

        enforceAccountActive(user);

        if (!userAccountApi.passwordMatches(password, user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, mapToDto(user));
    }

    private void enforceAccountActive(UserIdentity user) {
        if (!user.enabled() || !user.accountNonLocked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is disabled or locked");
        }
    }

    private UserDto mapToDto(UserIdentity user) {
        return new UserDto(
                user.id(),
                user.email(),
                user.displayName(),
                user.roles(),
                user.userDiscountPercentage(),
                user.userDiscountStartDate(),
                user.userDiscountEndDate()
        );
    }

    private UserRegistrationRequest toRegistrationRequest(SignupRequest request) {
        return new UserRegistrationRequest(request.email(), request.password(), request.username());
    }
}
