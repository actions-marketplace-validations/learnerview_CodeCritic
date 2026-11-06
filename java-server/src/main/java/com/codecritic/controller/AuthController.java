package com.codecritic.controller;

import com.codecritic.dto.auth.LoginRequest;
import com.codecritic.dto.auth.LoginResponse;
import com.codecritic.dto.auth.RegisterRequest;
import com.codecritic.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller handling user authentication and registration.
 *
 * <p>Successful login and registration both return a JWT that the client supplies as a
 * bearer token on subsequent requests.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    /**
     * Creates the controller with the authentication service.
     *
     * @param authService service performing credential checks and user registration
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates a user and issues a JWT.
     *
     * @param request username and password to verify
     * @return HTTP 200 with the issued token and expiry information
     * @throws org.springframework.security.authentication.BadCredentialsException if the
     *         username or password is invalid
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());
        return ResponseEntity.ok(authService.authenticate(request));
    }

    /**
     * Registers a new user and issues a JWT.
     *
     * @param request username and password for the new account
     * @return HTTP 200 with the issued token and expiry information
     * @throws org.springframework.web.server.ResponseStatusException if the username is
     *         already taken
     */
    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration attempt for user: {}", request.getUsername());
        return ResponseEntity.ok(authService.register(request));
    }
}
