package com.miniciv;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The abuse limit on the STOMP frames a session may send, set low. Time only
 * moves when a test advances the clock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "miniciv.limits.frames-per-second=5")
class FrameLimitsTest {

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		TestClock testClock() {
			return new TestClock();
		}

	}

	@LocalServerPort
	int port;

	@Autowired
	TestClock clock;

	/**
	 * Every connection a test opens, closed after it.
	 */
	final List<StompConnection> connections = new ArrayList<>();

	@AfterEach
	void disconnect() {
		connections.forEach(StompConnection::close);
	}

	RestClient rest() {
		return RestClient.create("http://localhost:" + port);
	}

	@Test
	void framesBeyondASessionsRateAreDroppedUntilItRefills() throws Exception {
		String code = (String) rest().post().uri("/api/rooms").retrieve().body(Map.class).get("code");
		String token = (String) rest().post()
			.uri("/api/rooms/{code}/players", code)
			.body(Map.of("nationName", "Rome"))
			.retrieve()
			.body(Map.class)
			.get("token");
		StompConnection stomp = new StompConnection(port, token);
		connections.add(stomp);
		// Connecting and subscribing take two of the session's five frames.
		StompConnection.Messages rejections = stomp.subscribe("/user/queue/rejections");
		Map<String, String> message = Map.of("token", token, "to", "Carthage", "text", "Hello");
		Map<String, String> notStarted = Map.of("reason", "Messages can only be sent once the Game has started.");

		for (int i = 0; i < 10; i++) {
			stomp.send("/app/rooms/" + code + "/messages", message);
		}

		for (int i = 0; i < 3; i++) {
			assertThat(rejections.next()).isEqualTo(notStarted);
		}
		rejections.expectNone();
		clock.advance(Duration.ofMillis(200));
		stomp.send("/app/rooms/" + code + "/messages", message);
		assertThat(rejections.next()).isEqualTo(notStarted);
	}

}
