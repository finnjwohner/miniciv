package com.miniciv;

import java.util.HashMap;
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

import com.miniciv.engine.BuildingType;
import com.miniciv.engine.Ruleset;
import com.miniciv.engine.Technology;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Tech Tree as Players experience it over STOMP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TechTreeTest {

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
			return new Ruleset(List.of(), List.of(new BuildingType("Walls", 30, 2, 0, 0, 0, 0, 10)),
					List.of(new Technology("Masonry", 3, List.of(), List.of("Walls")),
							new Technology("Mathematics", 20, List.of("Masonry"), List.of()),
							new Technology("Astronomy", 40, List.of("Mathematics"), List.of())));
		}

	}

	/**
	 * A hidden Technology as it is sent, with nothing to tell which one it is.
	 */
	static final Map<String, Object> HIDDEN = new HashMap<>(Map.of("research", 0, "prerequisites", List.of(),
			"unlocks", List.of(), "standing", "HIDDEN"));

	static {
		HIDDEN.put("name", null);
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
	 * Starts a Game between Rome and Carthage and ends its first Turn, so each
	 * has gained some Research.
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
			TurnsTest.readyUp(player.stomp(), code, player.token(), player == rome ? 0 : 1);
		}
		for (TurnsTest.Connected player : List.of(rome, carthage)) {
			player.privately().next();
		}
		for (TurnsTest.Connected player : List.of(rome, carthage)) {
			send(player, "/done", Map.of("token", player.token(), "turn", 1));
		}
	}

	@Test
	void aPlayerSeesTheTechTreeAndUnlockingATechnologyGivesAccessToWhatItUnlocks() throws Exception {
		startAndEndFirstTurn();
		Map<?, ?> view = rome.privately().next();
		assertThat(view.get("research")).isEqualTo(3);
		assertThat(view.get("availableBuildings")).isEqualTo(List.of());
		assertThat(view.get("techTree")).isEqualTo(List.of(
				Map.of("name", "Masonry", "research", 3, "prerequisites", List.of(), "unlocks", List.of("Walls"),
						"standing", "AVAILABLE"),
				Map.of("name", "Mathematics", "research", 20, "prerequisites", List.of("Masonry"), "unlocks", List.of(),
						"standing", "LOCKED"),
				HIDDEN));

		send(rome, "/technologies", Map.of("token", rome.token(), "technology", "Masonry"));

		Map<?, ?> unlocked = rome.privately().next();
		assertThat(unlocked.get("research")).isEqualTo(0);
		assertThat(unlocked.get("techTree")).isEqualTo(List.of(
				Map.of("name", "Masonry", "research", 3, "prerequisites", List.of(), "unlocks", List.of("Walls"),
						"standing", "UNLOCKED"),
				Map.of("name", "Mathematics", "research", 20, "prerequisites", List.of("Masonry"), "unlocks", List.of(),
						"standing", "AVAILABLE"),
				Map.of("name", "Astronomy", "research", 40, "prerequisites", List.of("Mathematics"), "unlocks",
						List.of(), "standing", "LOCKED")));
		assertThat(unlocked.get("availableBuildings")).isEqualTo(List.of(Map.of("name", "Walls", "gold", 30, "turns", 2,
				"populationPerTurn", 0, "goldPerTurn", 0, "goldPerPopulation", 0.0, "researchPerTurn", 0, "defense", 10)));
	}

	@Test
	void aHiddenTechnologysNameIsNeverSentToThePlayer() throws Exception {
		startAndEndFirstTurn();
		Map<?, ?> view = rome.privately().next();

		assertThat(view.toString()).doesNotContain("Astronomy");
	}

	@Test
	void aPlayerWhoseUnlockingIsRejectedIsToldWhyPrivately() throws Exception {
		startAndEndFirstTurn();
		rome.privately().next();
		StompConnection.Messages romeRejections = rome.stomp().subscribe("/user/queue/rejections");

		send(rome, "/technologies", Map.of("token", rome.token(), "technology", "Mathematics"));

		assertThat(romeRejections.next())
			.isEqualTo(Map.of("reason", "Mathematics requires Masonry to be unlocked first."));
	}

	@Test
	void aRejectedUnlockOfAHiddenTechnologyDoesNotRevealItsPrerequisites() throws Exception {
		startAndEndFirstTurn();
		rome.privately().next();
		StompConnection.Messages romeRejections = rome.stomp().subscribe("/user/queue/rejections");

		send(rome, "/technologies", Map.of("token", rome.token(), "technology", "Astronomy"));

		Map<?, ?> rejection = romeRejections.next();
		assertThat(rejection).isEqualTo(Map.of("reason", "There is no Technology called Astronomy."));
		assertThat(rejection.toString()).doesNotContain("Mathematics");
	}

}
