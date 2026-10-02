package com.miniciv;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import com.miniciv.engine.Ruleset;
import com.miniciv.engine.UnitType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Encounters as Players experience them over STOMP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EncountersTest {

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		TestClock testClock() {
			return new TestClock();
		}

		@Bean
		@Primary
		Ruleset testRuleset() {
			return new Ruleset(List.of(new UnitType("Warrior", 10, 1, 1, 1, 3, List.of())), List.of(), List.of());
		}

	}

	@LocalServerPort
	int port;

	String code;

	TurnsTest.Connected rome;

	TurnsTest.Connected carthage;

	@AfterEach
	void disconnect() {
		rome.close();
		carthage.close();
	}

	TurnsTest.Connected join(String nationName) throws Exception {
		Map<?, ?> joined = RestClient.create("http://localhost:" + port)
			.post()
			.uri("/api/rooms/{code}/players", code)
			.body(Map.of("nationName", nationName))
			.retrieve()
			.body(Map.class);
		String token = (String) joined.get("token");
		StompConnection stomp = new StompConnection(port, token);
		return new TurnsTest.Connected(stomp, stomp.subscribe("/user/queue/game"), (String) joined.get("playerId"),
				token);
	}

	void send(TurnsTest.Connected player, String destination, Map<String, Object> body) {
		player.stomp().send("/app/rooms/" + code + destination, body);
	}

	@Test
	void aPlayerIsToldPrivatelyWhyAChoiceAboutAnEncounterIsRejected() throws Exception {
		code = (String) RestClient.create("http://localhost:" + port)
			.post()
			.uri("/api/rooms")
			.retrieve()
			.body(Map.class)
			.get("code");
		rome = join("Rome");
		carthage = join("Carthage");
		for (TurnsTest.Connected player : List.of(rome, carthage)) {
			send(player, "/ready", Map.of("token", player.token(), "ready", true));
		}
		for (TurnsTest.Connected player : List.of(rome, carthage)) {
			player.privately().next();
		}
		StompConnection.Messages romeRejections = rome.stomp().subscribe("/user/queue/rejections");

		send(rome, "/encounters/answers", Map.of("token", rome.token(), "warparty", 1, "fight", true));

		assertThat(romeRejections.next())
			.isEqualTo(Map.of("reason", "No Encounter awaits your choice for that Warparty."));
	}

}
