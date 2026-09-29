package com.andres.walksecurity.api.common;

import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    private CurrentUser() {}

    /** El "sub" del JWT es el id del usuario. */
    public static long id(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
