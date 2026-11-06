package com.codecritic.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Credentials submitted when authenticating an existing user.
 *
 * <p>Instances are mutable data carriers produced by Lombok and validated before use.</p>
 */
@Data
@Builder
@AllArgsConstructor
public class LoginRequest {

    /**
     * Creates an empty login request populated by request binding or Lombok.
     */
    public LoginRequest() {
    }

    @NotBlank(message = "username is required")
    @Size(max = 64, message = "username must be 64 characters or fewer")
    private String username;

    @NotBlank(message = "password is required")
    @Size(max = 128, message = "password must be 128 characters or fewer")
    private String password;
}
