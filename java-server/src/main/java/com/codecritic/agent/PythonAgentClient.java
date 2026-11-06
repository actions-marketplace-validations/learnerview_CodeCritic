package com.codecritic.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The only Java boundary to the advisory Python agent.
 *
 * <p>The browser never receives the agent URL or its credentials. The Java
 * controller authenticates the user, applies the request limits, and then
 * calls this client with a private service token.</p>
 */
@Component
public final class PythonAgentClient {

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String serviceToken;

    /**
     * Creates the client bound to the configured Python agent base URL.
     *
     * <p>A blank URL falls back to {@code http://localhost:8000}, and any trailing
     * slash is removed. The service token is sent on every request in the
     * {@code X-CodeCritic-Agent-Token} header.</p>
     *
     * @param builder builder used to construct the underlying HTTP client
     * @param objectMapper mapper used to parse agent responses as JSON
     * @param url base URL of the Python agent
     * @param serviceToken private token used to authenticate against the Python agent
     */
    public PythonAgentClient(RestClient.Builder builder,
                             ObjectMapper objectMapper,
                             @Value("${codecritic.python-agent.url:http://localhost:8000}") String url,
                             @Value("${codecritic.python-agent.token:}") String serviceToken) {
        this.client = builder.baseUrl(stripTrailingSlash(url))
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.serviceToken = serviceToken;
    }

    /**
     * Submits source code to the agent's {@code /review} endpoint.
     *
     * @param request request payload serialized as JSON
     * @return the agent response as a JSON tree
     * @throws PythonAgentRequestException if the agent rejects the request with a 4xx response
     * @throws PythonAgentUnavailableException if the agent cannot be reached or fails with a 5xx response
     */
    public JsonNode review(Object request) {
        return post("/review", request);
    }

    /**
     * Submits a test-generation request to the agent's {@code /generate-tests} endpoint.
     *
     * @param request request payload serialized as JSON
     * @return the agent response as a JSON tree
     * @throws PythonAgentRequestException if the agent rejects the request with a 4xx response
     * @throws PythonAgentUnavailableException if the agent cannot be reached or fails with a 5xx response
     */
    public JsonNode generateTests(Object request) {
        return post("/generate-tests", request);
    }

    /**
     * Submits a repository analysis request to the agent's
     * {@code /analyze-repository} endpoint.
     *
     * <p>The payload defaults {@code maxFiles} to 15 when unspecified and omits a
     * blank GitHub token.</p>
     *
     * @param request repository analysis request
     * @return the agent response as a JSON tree
     * @throws PythonAgentRequestException if the agent rejects the request with a 4xx response
     * @throws PythonAgentUnavailableException if the agent cannot be reached or fails with a 5xx response
     */
    public JsonNode analyzeRepository(com.codecritic.dto.RepositoryAnalysisRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("repoUrl", request.repoUrl());
        payload.put("branch", request.branch());
        payload.put("maxFiles", request.maxFiles() == null ? 15 : request.maxFiles());
        if (request.githubToken() != null && !request.githubToken().isBlank()) {
            payload.put("githubToken", request.githubToken());
        }
        return post("/analyze-repository", payload);
    }

    /**
     * Submits a debugging request to the agent's {@code /debug} endpoint.
     *
     * <p>Defaults the language to {@code java} when it is missing or blank.</p>
     *
     * @param request debugging request containing code, error log, and language
     * @return the agent response as a JSON tree
     * @throws PythonAgentRequestException if the agent rejects the request with a 4xx response
     * @throws PythonAgentUnavailableException if the agent cannot be reached or fails with a 5xx response
     */
    public JsonNode debug(com.codecritic.dto.DebugAnalysisRequest request) {
        return post("/debug", Map.of(
                "code", request.code(),
                "errorLog", request.errorLog(),
                "language", request.language() == null || request.language().isBlank()
                        ? "java" : request.language()));
    }

    private JsonNode post(String path, Object request) {
        try {
            String body = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-CodeCritic-Agent-Token", serviceToken)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new PythonAgentRequestException(
                        "The advisory analysis service rejected the request", exception);
            }
            throw new PythonAgentUnavailableException("The advisory analysis service is unavailable", exception);
        } catch (RestClientException | java.io.IOException exception) {
            throw new PythonAgentUnavailableException("The advisory analysis service is unavailable", exception);
        }
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8000";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * Signals that the advisory Python agent could not be reached or returned a
     * server-side failure.
     */
    public static final class PythonAgentUnavailableException extends RuntimeException {
        /**
         * Creates the exception.
         *
         * @param message human-readable failure description
         * @param cause underlying transport or HTTP failure
         */
        public PythonAgentUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The agent rejected the caller's input; this is not an outage. */
    public static final class PythonAgentRequestException extends RuntimeException {
        /**
         * Creates the exception.
         *
         * @param message human-readable failure description
         * @param cause underlying HTTP rejection
         */
        public PythonAgentRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
