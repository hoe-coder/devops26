package com.devops.devops2026;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SpringBootTest
class Devops2026ApplicationTests {

	@Test
	void contextLoads() {
	}

	@Test
	void applicationShouldRespond() throws Exception
	{
		HttpClient client = HttpClient.newHttpClient();

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:8080/api/test"))
				.GET()
				.build();

		HttpResponse<String> response =
				client.send(request, HttpResponse.BodyHandlers.ofString());

		assertEquals(200, response.statusCode());
	}
}
