package com.perfumestore.security;

import java.nio.charset.StandardCharsets;
import com.perfumestore.exception.ApiException;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password) {

        if (password == null || password.isBlank()) {
            throw ApiException.badRequest("Password is required");
        }

        if (password.length() < 8) {
            throw ApiException.badRequest("Password must contain at least 8 characters");
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Password must not exceed 72 UTF-8 bytes");
        }
    }
}