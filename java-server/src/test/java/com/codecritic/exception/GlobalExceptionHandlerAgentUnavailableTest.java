package com.codecritic.exception;

import com.codecritic.agent.PythonAgentClient.PythonAgentUnavailableException;
import com.codecritic.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerAgentUnavailableTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void pythonAgentOutageReturns503InsteadOfGeneric500() {
        ResponseEntity<ApiErrorResponse> response = handler.handleAgentUnavailable(
                new PythonAgentUnavailableException("The advisory analysis service is unavailable",
                        new ConnectException("Connection refused")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("AGENT_UNAVAILABLE");
    }

    @Test
    void responseExplainsHowToRecoverWithoutLeakingConnectionDetails() {
        ResponseEntity<ApiErrorResponse> response = handler.handleAgentUnavailable(
                new PythonAgentUnavailableException("The advisory analysis service is unavailable",
                        new ConnectException("Connection refused: localhost:8000")));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message())
                .contains("Python agent")
                .doesNotContain("localhost", "8000", "Connection refused");
    }
}
