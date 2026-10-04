package com.andres.walksecurity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.andres.walksecurity.api.user.AppUser;
import com.andres.walksecurity.api.user.AuthDtos.AuthResponse;
import com.andres.walksecurity.api.user.AuthDtos.DeviceRequest;
import com.andres.walksecurity.api.user.AuthService;
import com.andres.walksecurity.api.user.TokenService;
import com.andres.walksecurity.api.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** La app no tiene inicio de sesión: cada teléfono se identifica con su deviceId. */
class DeviceRegistrationTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final TokenService tokens = mock(TokenService.class);
    private final AuthService service = new AuthService(users, encoder, tokens);

    private static final String DEVICE = "3f2b8c1e-7d4a-4b9e-9a1c-2d5e6f708192";

    @Test
    void primerUsoCreaElUsuarioDelTelefono() {
        when(users.findByEmail(DEVICE + "@dispositivo.walksecurity")).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hash");
        when(users.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(tokens.issue(any())).thenReturn("jwt");

        AuthResponse response = service.registerDevice(new DeviceRequest(DEVICE, "Andrés", "+573001234567"));

        assertEquals("jwt", response.token());
        verify(users).save(any(AppUser.class));
    }

    @Test
    void usosSiguientesSoloActualizanElNombre() {
        AppUser existing = withId(new AppUser("Viejo", DEVICE + "@dispositivo.walksecurity", "", "hash"));
        when(users.findByEmail(DEVICE + "@dispositivo.walksecurity")).thenReturn(Optional.of(existing));
        when(tokens.issue(any())).thenReturn("jwt");

        service.registerDevice(new DeviceRequest(DEVICE, "Andrés", null));

        assertEquals("Andrés", existing.getName());
        verify(users, never()).save(any());
    }

    @Test
    void sinNombreUsaUnoGenerico() {
        when(users.findByEmail(any())).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("hash");
        when(users.save(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(tokens.issue(any())).thenReturn("jwt");

        AuthResponse response = service.registerDevice(new DeviceRequest(DEVICE, " ", null));

        assertNotNull(response);
        verify(users).save(org.mockito.ArgumentMatchers.argThat(u -> u.getName().equals("Usuario WalkSecurity")));
    }

    /** Simula el id que asigna PostgreSQL al guardar. */
    private static AppUser withId(AppUser user) {
        try {
            java.lang.reflect.Field field = AppUser.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, 1L);
            return user;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
