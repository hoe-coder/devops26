package com.devops.devops2026.rest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ControllerIntegrationTest {
    private void assertEndpoint(String path, String expected) throws Exception {
        String baseUrl = System.getProperty("app.baseUrl", "http://localhost:8080");
        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build().send(
                HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals(expected, response.body());
    }

    @Test
    void helloReturnsGreeting() throws Exception {
        assertEndpoint("/", "Hello!");
    }

    @Test
    void nameReturnsProvidedName() throws Exception {
        assertEndpoint("/Alice", "Alice");
    }
}
