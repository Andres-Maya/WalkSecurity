package com.andres.walksecurity.api.user;

import com.andres.walksecurity.api.common.ApiException;
import com.andres.walksecurity.api.user.AuthDtos.AuthResponse;
import com.andres.walksecurity.api.user.AuthDtos.LoginRequest;
import com.andres.walksecurity.api.user.AuthDtos.RegisterRequest;
import com.andres.walksecurity.api.user.AuthDtos.UserResponse;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("Ya existe una cuenta con ese correo.");
        }
        AppUser user = users.save(new AppUser(
            request.name().trim(), email, request.phone(), passwordEncoder.encode(request.password())));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmail(normalizeEmail(request.email()))
            .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
            // Mismo mensaje para correo o contraseña incorrectos: no revela qué cuentas existen
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos."));
        return toResponse(user);
    }

    private AuthResponse toResponse(AppUser user) {
        return new AuthResponse(tokenService.issue(user), UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
