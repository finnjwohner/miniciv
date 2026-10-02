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

import com.miniciv.engine.Event;
import com.miniciv.engine.Ruleset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dilemmas as Players experience them over STOMP, with one that happens to
 * every Nation as every Turn ends.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DilemmasTest {

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
			return new Ruleset(List.of(), List.of(), List.of(),
					List.of(new Event("Outbreak", Event.Subject.NATION, 1, null, null, "Sickness broke out in your Nation",
							List.of(new Event.Choice("Quarantine", new Event.Effects(-15, 0, 0, 0, 0), false,
									"You quarantined the sick"),
									new Event.Choice("Let it spread", new Event.Effects(0, -8, 0, 0, 0), true,
											"You let the sickness spread")))));
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

	/**
	 * Starts a Game between Rome and Carthage and ends its first Turn, so the
	 * Dilemma awaits both Players.
	 */
	void startAndEndFirstTurn() throws Exception {
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
		for (TurnsTest.Connected player : List.of(rome, carthage)) {
			send(player, "/done", Map.of("token", player.token(), "turn", 1));
		}
	}

	@Test
	void aPlayerSeesADilemmaAtTheStartOfTheTurnAndTheirAnswerAppliesAtOnce() throws Exception {
		startAndEndFirstTurn();
		Map<?, ?> view = rome.privately().next();
		assertThat(view.get("turn")).isEqualTo(2);
		assertThat(view.get("dilemmas")).isEqualTo(List.of(Map.of("name", "Outbreak", "text",
				"Sickness broke out in your Nation", "choices",
				List.of(Map.of("name", "Quarantine", "effects",
						Map.of("gold", -15, "population", 0, "research", 0, "damageDefense", 0, "travelTurns", 0), "worst",
						false, "text", "You quarantined the sick"),
						Map.of("name", "Let it spread", "effects",
								Map.of("gold", 0, "population", -8, "research", 0, "damageDefense", 0, "travelTurns", 0),
								"worst", true, "text", "You let the sickness spread")))));

		send(rome, "/dilemmas", Map.of("token", rome.token(), "dilemma", "Outbreak", "choice", "Quarantine"));

		Map<?, ?> answered = rome.privately().next();
		assertThat(answered.get("dilemmas")).isEqualTo(List.of());
		assertThat(answered.get("gold")).isEqualTo(45);
	}

	@Test
	void aPlayerWhoseAnswerIsRejectedIsToldWhyPrivately() throws Exception {
		startAndEndFirstTurn();
		rome.privately().next();
		StompConnection.Messages romeRejections = rome.stomp().subscribe("/user/queue/rejections");

		send(rome, "/dilemmas", Map.of("token", rome.token(), "dilemma", "Outbreak", "choice", "Pray"));

		assertThat(romeRejections.next()).isEqualTo(Map.of("reason", "Outbreak offers no choice called Pray."));
	}

}
