package com.andres.walksecurity.api.user;

import com.andres.walksecurity.api.common.ApiException;
import com.andres.walksecurity.api.user.AuthDtos.AuthResponse;
import com.andres.walksecurity.api.user.AuthDtos.DeviceRequest;
import com.andres.walksecurity.api.user.AuthDtos.LoginRequest;
import com.andres.walksecurity.api.user.AuthDtos.RegisterRequest;
import com.andres.walksecurity.api.user.AuthDtos.UserResponse;
import java.util.Locale;
import java.util.UUID;
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

    /**
     * La app no tiene inicio de sesión: cada teléfono se identifica con su deviceId. Si ya existe
     * se actualiza su perfil; si no, se crea un usuario para él. Devuelve un JWT como el login.
     */
    @Transactional
    public AuthResponse registerDevice(DeviceRequest request) {
        String email = request.deviceId().toLowerCase(Locale.ROOT) + DEVICE_EMAIL_DOMAIN;
        String name = request.name() == null || request.name().isBlank() ? "Usuario WalkSecurity" : request.name().trim();
        String phone = request.phone() == null ? "" : request.phone();
        AppUser user = users.findByEmail(email)
            .map(existing -> {
                existing.updateProfile(name, phone);
                return existing;
            })
            .orElseGet(() -> users.save(new AppUser(
                name, email, phone, passwordEncoder.encode(UUID.randomUUID().toString()))));
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

    private static final String DEVICE_EMAIL_DOMAIN = "@dispositivo.walksecurity";

    private AuthResponse toResponse(AppUser user) {
        return new AuthResponse(tokenService.issue(user), UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
