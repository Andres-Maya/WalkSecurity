package com.andres.walksecurity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.andres.walksecurity.api.config.JwtProperties;
import com.andres.walksecurity.api.config.SecurityConfig;
import com.andres.walksecurity.api.user.AppUser;
import com.andres.walksecurity.api.user.TokenService;
import java.lang.reflect.Field;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class TokenServiceTest {

    private final SecurityConfig config = new SecurityConfig();
    private final JwtProperties properties = new JwtProperties("0123456789abcdef0123456789abcdef-test", 7);

    @Test
    void issuedTokenIsAcceptedAndCarriesUserId() throws Exception {
        SecretKey key = invoke("jwtSecretKey", properties);
        TokenService service = new TokenService(invoke("jwtEncoder", key), properties);
        JwtDecoder decoder = invoke("jwtDecoder", key);

        AppUser user = new AppUser("Ana", "ana@example.com", "+573001234567", "hash");
        setId(user, 42L);

        Jwt jwt = decoder.decode(service.issue(user));
        assertEquals("42", jwt.getSubject());
        assertEquals("ana@example.com", jwt.getClaimAsString("email"));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        SecretKey key = invoke("jwtSecretKey", properties);
        JwtProperties other = new JwtProperties("otra-clave-diferente-de-al-menos-32-chars", 7);
        TokenService foreign = new TokenService(invoke("jwtEncoder", invoke("jwtSecretKey", other)), other);
        JwtDecoder decoder = invoke("jwtDecoder", key);

        AppUser user = new AppUser("Ana", "ana@example.com", "+573001234567", "hash");
        setId(user, 1L);

        assertThrows(JwtException.class, () -> decoder.decode(foreign.issue(user)));
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtProperties("corta", 7));
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(String method, Object arg) throws Exception {
        for (var m : SecurityConfig.class.getDeclaredMethods()) {
            if (m.getName().equals(method)) {
                m.setAccessible(true);
                return (T) m.invoke(config, arg);
            }
        }
        throw new IllegalArgumentException(method);
    }

    private static void setId(AppUser user, long id) throws Exception {
        Field field = AppUser.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
    }
}
