package com.codecritic.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Credentials submitted when creating a new user account.
 *
 * <p>Instances are mutable data carriers produced by Lombok and validated before use.</p>
 */
@Data
@Builder
@AllArgsConstructor
public class RegisterRequest {

    /**
     * Creates an empty registration request populated by request binding or Lombok.
     */
    public RegisterRequest() {
    }

    @NotBlank(message = "username is required")
    @Size(min = 3, max = 64, message = "username must be between 3 and 64 characters")
    private String username;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 128, message = "password must be between 8 and 128 characters")
    private String password;
}