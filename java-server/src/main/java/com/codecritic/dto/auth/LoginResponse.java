package com.codecritic.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Successful authentication result containing the issued bearer token.
 *
 * <p>Instances are mutable data carriers produced by Lombok.</p>
 */
@Data
@Builder
@AllArgsConstructor
public class LoginResponse {

    /**
     * Creates an empty login response populated by Lombok or the auth service.
     */
    public LoginResponse() {
    }

    private String token;
    private String tokenType;
    private long expiresInMs;
    private String username;
}
