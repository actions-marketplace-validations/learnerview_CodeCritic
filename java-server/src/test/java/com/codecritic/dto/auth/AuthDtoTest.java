package com.codecritic.dto.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

class AuthDtoTest {

    @Test
    void defaultConstructorsCreateEmptyPayloads() {
        assertNull(new LoginRequest().getUsername());
        assertNull(new LoginResponse().getToken());
        assertNull(new RegisterRequest().getUsername());
    }
}
