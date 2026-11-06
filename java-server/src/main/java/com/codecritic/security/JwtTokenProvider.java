package com.codecritic.security;

import com.codecritic.config.DeploymentProfile;
import com.codecritic.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT token provider — generates and validates signed JWTs.
 *
 * Uses HMAC-SHA256 signing with a configured secret. Issuer, subject and
 * custom claims (role) are embedded in the token.
 */
@Component
public class JwtTokenProvider {

    /** Demo default shipped only under the {@code dev}/{@code test} profiles (application-dev.yml). */
    private static final String DEMO_DEFAULT_SECRET = "codecritic-demo-secret-key-change-me-in-production-0123456789";

    private final JwtProperties properties;
    private final SecretKey key;

    /**
     * Creates the provider and derives the HMAC signing key from the configured secret.
     *
     * @param properties JWT settings supplying the secret, issuer, and expiration
     * @param environment environment used to decide whether the demo secret is allowed
     * @throws IllegalStateException if the secret is shorter than 32 bytes or the demo secret is used outside dev/test
     */
    public JwtTokenProvider(JwtProperties properties, Environment environment) {
        this.properties = properties;
        String secret = properties.getSecret();
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("codecritic.jwt.secret must be at least 32 bytes for HS256");
        }
        if (!DeploymentProfile.allowsDemoDefaults(environment) && DEMO_DEFAULT_SECRET.equals(secret)) {
            // Default-deny: any profile that is not dev/test, and a deployment with no profile
            // at all, must not sign tokens with the committed demo secret. Requiring an explicit
            // dev/test profile means the safe behavior no longer depends on an operator
            // remembering to set a "prod" profile.
            throw new IllegalStateException(
                    "JWT_SECRET must be set to a non-default value outside the dev/test profiles. "
                            + "Refusing to start with the demo signing secret.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * Generates a signed token for the given subject and custom claims.
     *
     * @param username subject (username) embedded in the token
     * @param claims additional claims to embed; must not be {@code null}
     * @return the compact signed JWT
     */
    public String generateToken(String username, Map<String, Object> claims) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getExpirationMs());
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(username)
                .claims(claims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Generates a signed token for the given subject without custom claims.
     *
     * @param username subject (username) embedded in the token
     * @return the compact signed JWT
     */
    public String generateToken(String username) {
        return generateToken(username, Map.of());
    }

    /**
     * Checks whether a token is valid for this provider.
     *
     * @param token JWT to validate
     * @return {@code true} if the token parses, is unexpired, and has the expected issuer
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies the token signature and issuer, then returns its claims.
     *
     * @param token JWT whose claims should be read
     * @return the verified token payload
     * @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or not signed by this provider
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the subject (username) from a token.
     *
     * @param token JWT from which to read the subject
     * @return the token subject, or {@code null} if none was set
     * @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or not signed by this provider
     */
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }
}
