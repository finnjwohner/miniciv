package com.miniciv;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The app as the Droplet runs it, behind Caddy on localhost. Each IP may create
 * one Room a minute, so a second Room is only created if the app keys on the
 * client IP Caddy forwards rather than on localhost.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "miniciv.limits.room-creations-per-minute=1")
@ActiveProfiles("prod")
class ProdProfileTest {

	@LocalServerPort
	int port;

	int createRoom(String ip) {
		return RestClient.create("http://127.0.0.1:" + port)
			.post()
			.uri("/api/rooms")
			.header("X-Forwarded-For", ip)
			.exchange((request, response) -> response.getStatusCode().value());
	}

	@Test
	void takesTheClientIpFromXForwardedForSentFromLocalhost() {
		assertThat(createRoom("10.0.0.1")).isEqualTo(201);
		assertThat(createRoom("10.0.0.2")).isEqualTo(201);
		assertThat(createRoom("10.0.0.1")).isEqualTo(429);
	}

}
