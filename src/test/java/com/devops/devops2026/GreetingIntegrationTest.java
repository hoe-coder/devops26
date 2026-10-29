package com.devops.devops2026;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GreetingIntegrationTest {

	@Test
	void shouldReturnGreeting() {
		GreetingService service = new GreetingService();

		String result = service.greet("Alice");

		assertEquals("Hello Alice!", result);
	}
}
