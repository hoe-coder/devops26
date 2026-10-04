package com.devops.devops2026;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationIntegrationTest {

	@Test
	void contextLoads() {
	}

	@Test
	void applicationShouldRespond() throws Exception {

		HttpClient client = HttpClient.newHttpClient();

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:8080/"))
				.GET()
				.build();

		HttpResponse<String> response =
				client.send(
						request,
						HttpResponse.BodyHandlers.ofString()
				);

		assertEquals(200, response.statusCode());
		assertEquals("Hello!", response.body());
	}
}
