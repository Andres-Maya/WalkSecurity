package com.andres.walksecurity.api.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
        @NotBlank(message = "El nombre es obligatorio.") @Size(max = 120) String name,
        @NotBlank(message = "El correo es obligatorio.") @Email(message = "Correo inválido.") @Size(max = 160) String email,
        @NotBlank(message = "El teléfono es obligatorio.") @Pattern(regexp = "^\\+?\\d{7,15}$", message = "Teléfono inválido.") String phone,
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.") String password
    ) {}

    public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio.") String email,
        @NotBlank(message = "La contraseña es obligatoria.") String password
    ) {}

    /**
     * Identificación automática del teléfono (sin inicio de sesión). El deviceId es un UUID aleatorio
     * que la app genera una vez y guarda en el teléfono: funciona como su credencial.
     */
    public record DeviceRequest(
        @NotBlank(message = "El identificador del dispositivo es obligatorio.")
        @Pattern(regexp = "^[A-Za-z0-9-]{16,64}$", message = "Identificador de dispositivo inválido.") String deviceId,
        @Size(max = 120) String name,
        @Pattern(regexp = "^$|^\\+?\\d{7,15}$", message = "Teléfono inválido.") String phone
    ) {}

    public record UserResponse(long id, String name, String email, String phone) {
        static UserResponse from(AppUser user) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone());
        }
    }

    public record AuthResponse(String token, UserResponse user) {}
}
