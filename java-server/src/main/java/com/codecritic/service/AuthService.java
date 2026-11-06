package com.codecritic.service;

import com.codecritic.dto.auth.LoginRequest;
import com.codecritic.dto.auth.LoginResponse;
import com.codecritic.dto.auth.RegisterRequest;

/**
 * Contract for user authentication and registration.
 */
public interface AuthService {

    /**
     * Verifies the supplied credentials and issues a JWT for the matching user.
     *
     * @param request username and password to verify
     * @return the issued token together with its type, lifetime, and username
     * @throws org.springframework.security.authentication.BadCredentialsException if the
     *         username does not exist or the password does not match
     */
    LoginResponse authenticate(LoginRequest request);

    /**
     * Creates a new user account and issues a JWT for that user.
     *
     * @param request username and password for the new account
     * @return the issued token together with its type, lifetime, and username
     * @throws org.springframework.web.server.ResponseStatusException if the username is
     *         already taken, with an HTTP 409 status
     */
    LoginResponse register(RegisterRequest request);
}
