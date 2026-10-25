package ru.mephi.hotelbooking.common.security;

public final class JwtClaims {

    public static final String USERNAME = "username";
    public static final String ROLE = "role";
    public static final String SCOPE = "scope";
    public static final String INTERNAL_SCOPE = "internal";

    private JwtClaims() {
    }
}
