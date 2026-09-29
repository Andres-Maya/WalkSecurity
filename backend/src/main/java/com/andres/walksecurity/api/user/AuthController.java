package com.andres.walksecurity.api.user;

import com.andres.walksecurity.api.common.ApiException;
import com.andres.walksecurity.api.common.CurrentUser;
import com.andres.walksecurity.api.user.AuthDtos.AuthResponse;
import com.andres.walksecurity.api.user.AuthDtos.LoginRequest;
import com.andres.walksecurity.api.user.AuthDtos.RegisterRequest;
import com.andres.walksecurity.api.user.AuthDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;
    private final UserRepository users;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/api/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/api/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/api/users/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return users.findById(CurrentUser.id(jwt))
            .map(UserResponse::from)
            .orElseThrow(() -> ApiException.notFound("Usuario no encontrado."));
    }
}
