package com.miniciv;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import com.miniciv.room.Rooms;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The abuse limits on creating and joining Rooms, set low. Each request comes
 * from the client IP it names in X-Forwarded-For, as it does through Caddy.
 * Time only moves when a test advances the clock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "server.forward-headers-strategy=native", "miniciv.limits.rooms=4",
				"miniciv.limits.room-creations-per-minute=2", "miniciv.limits.joins-per-minute=3",
				"miniciv.limits.nation-name-length=8" })
class RequestLimitsTest {

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

	@Autowired
	Rooms rooms;

	record Response(int status, Map<?, ?> body) {
	}

	/**
	 * Starts each test with no Rooms and every client IP's requests forgotten.
	 */
	@BeforeEach
	void removeEveryRoom() {
		clock.advance(Duration.ofHours(1));
		rooms.removeEmptyRooms();
	}

	Response createRoom(String ip) {
		return rest().post()
			.uri("/api/rooms")
			.header("X-Forwarded-For", ip)
			.exchange((request, response) -> new Response(response.getStatusCode().value(),
					response.bodyTo(Map.class)));
	}

	Response join(String ip, String code, String nationName) {
		return rest().post()
			.uri("/api/rooms/{code}/players", code)
			.header("X-Forwarded-For", ip)
			.body(Map.of("nationName", nationName))
			.exchange((request, response) -> new Response(response.getStatusCode().value(),
					response.bodyTo(Map.class)));
	}

	RestClient rest() {
		return RestClient.create("http://localhost:" + port);
	}

	@Test
	void anIpMayCreateOnlySoManyRoomsAMinute() {
		assertThat(createRoom("10.0.0.1").status()).isEqualTo(201);
		assertThat(createRoom("10.0.0.1").status()).isEqualTo(201);

		Response refused = createRoom("10.0.0.1");
		assertThat(refused.status()).isEqualTo(429);
		assertThat(refused.body().get("detail"))
			.isEqualTo("You've created several Rooms just now. Wait a minute and try again.");
		assertThat(createRoom("10.0.0.2").status()).isEqualTo(201);

		clock.advance(Duration.ofSeconds(30));
		assertThat(createRoom("10.0.0.1").status()).isEqualTo(201);
	}

	@Test
	void onlySoManyRoomsMayBeLiveAtOnce() {
		createRoom("10.0.0.1");
		createRoom("10.0.0.2");
		createRoom("10.0.0.3");
		createRoom("10.0.0.4");

		Response refused = createRoom("10.0.0.5");
		assertThat(refused.status()).isEqualTo(503);
		assertThat(refused.body().get("detail")).isEqualTo("miniciv is full right now, try again shortly");

		clock.advance(Duration.ofSeconds(60));
		rooms.removeEmptyRooms();
		assertThat(createRoom("10.0.0.5").status()).isEqualTo(201);
	}

	@Test
	void anIpMayTryToJoinRoomsOnlySoManyTimesAMinuteWhetherOrNotTheRoomExists() {
		String code = (String) createRoom("10.0.0.1").body().get("code");
		assertThat(join("10.0.0.5", "no-such-room", "Rome").status()).isEqualTo(404);
		assertThat(join("10.0.0.5", "no-such-room", "Rome").status()).isEqualTo(404);
		assertThat(join("10.0.0.5", code, "Rome").status()).isEqualTo(201);

		Response refused = join("10.0.0.5", code, "Carthage");
		assertThat(refused.status()).isEqualTo(429);
		assertThat(refused.body().get("detail"))
			.isEqualTo("You've tried to join several Rooms just now. Wait a minute and try again.");
		assertThat(join("10.0.0.6", code, "Carthage").status()).isEqualTo(201);

		clock.advance(Duration.ofSeconds(20));
		assertThat(join("10.0.0.5", code, "Egypt").status()).isEqualTo(201);
	}

	@Test
	void aNationsNameMayBeOnlySoLong() {
		String code = (String) createRoom("10.0.0.1").body().get("code");

		assertThat(join("10.0.0.1", code, "  Carthage  ").status()).isEqualTo(201);
		Response refused = join("10.0.0.1", code, "Babylonia");
		assertThat(refused.status()).isEqualTo(400);
		assertThat(refused.body().get("detail")).isEqualTo("A Nation's name can be at most 8 characters.");
	}

}
