package ru.mephi.hotelbooking.common.security;

public enum Role {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
