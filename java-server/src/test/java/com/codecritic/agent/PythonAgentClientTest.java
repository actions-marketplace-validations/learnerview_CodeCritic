package com.codecritic.agent;

import com.codecritic.agent.PythonAgentClient.PythonAgentRequestException;
import com.codecritic.agent.PythonAgentClient.PythonAgentUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonAgentClientTest {

    private MockRestServiceServer server;
    private PythonAgentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PythonAgentClient(builder, new ObjectMapper(), "http://agent.test/", "service-token");
    }

    @Test
    void reviewSendsTheServiceTokenAndParsesTheResponse() {
        server.expect(requestTo("http://agent.test/review"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-CodeCritic-Agent-Token", "service-token"))
                .andRespond(withSuccess("{\"review\":\"looks good\"}", MediaType.APPLICATION_JSON));

        JsonNode response = client.review(Map.of("code", "public class A { void m() {} }"));

        assertThat(response.get("review").asText()).isEqualTo("looks good");
        server.verify();
    }

    @Test
    void agent4xxIsReportedAsARejectedRequestNotAnOutage() {
        server.expect(requestTo("http://agent.test/review"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\":\"Only Java code is supported\"}"));

        assertThatThrownBy(() -> client.review(Map.of("code", "not java")))
                .isInstanceOf(PythonAgentRequestException.class);
    }

    @Test
    void agent5xxIsReportedAsAnOutage() {
        server.expect(requestTo("http://agent.test/review"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.review(Map.of("code", "public class A {}")))
                .isInstanceOf(PythonAgentUnavailableException.class);
    }
}
