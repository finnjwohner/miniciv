package com.miniciv;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

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
 * Encounters as Players experience them over STOMP, with luck that has every
 * two Warparties abroad meet.
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
			return new Ruleset(List.of(new UnitType("Warrior", 10, 1, 1, 1, 3, List.of()), TurnsTest.PATHFINDER), List.of(),
					List.of());
		}

		@Bean
		@Primary
		Supplier<RandomGenerator> testLuck() {
			return () -> () -> 0L;
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

	void startGame() throws Exception {
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
	}

	void endTurn(int turn) throws Exception {
		for (TurnsTest.Connected player : List.of(rome, carthage)) {
			send(player, "/done", Map.of("token", player.token(), "turn", turn));
		}
		for (TurnsTest.Connected player : List.of(rome, carthage)) {
			assertThat(player.privately().next().get("turn")).isEqualTo(turn + 1);
		}
	}

	/**
	 * Has Rome and Carthage each train a Warrior and a Pathfinder on Turn 1,
	 * set the Pathfinder to Locate the other on Turn 2, which the luck has
	 * succeed, and send the Warrior at the other on Turn 3, so their
	 * Warparties meet as Turn 3 ends.
	 */
	void romeMeetsCarthage() throws Exception {
		for (TurnsTest.Connected player : List.of(rome, carthage)) {
			send(player, "/units", Map.of("token", player.token(), "unit", "Warrior", "count", 1));
			player.privately().next();
			send(player, "/units", Map.of("token", player.token(), "unit", "Pathfinder", "count", 1));
			player.privately().next();
		}
		endTurn(1);
		for (TurnsTest.Connected player : List.of(rome, carthage)) {
			send(player, "/tasks", Map.of("token", player.token(), "kind", "LOCATE", "target",
					player == rome ? "Carthage" : "Rome", "scouts", 1));
			player.privately().next();
		}
		endTurn(2);
		for (TurnsTest.Connected player : List.of(rome, carthage)) {
			send(player, "/warparties", Map.of("token", player.token(), "target", player == rome ? "Carthage" : "Rome",
					"units", Map.of("Warrior", 1)));
			player.privately().next();
		}
		endTurn(3);
	}

	static List<Map<String, Object>> encounter(String romeChoice) {
		return List.of(Map.of("first", Map.of("nation", "Rome", "warparty", 1, "units", Map.of("Warrior", 1), "choice", romeChoice),
				"second", Map.of("nation", "Carthage", "warparty", 1, "units", Map.of("Warrior", 1), "choice", "NONE")));
	}

	@Test
	void whenAPlayerChangesTheirChoiceTheOtherPlayerSeesItAtOnce() throws Exception {
		startGame();
		romeMeetsCarthage();
		send(rome, "/encounters/answers", Map.of("token", rome.token(), "warparty", 1, "fight", true));
		rome.privately().next();
		assertThat(carthage.privately().next().get("encounters")).isEqualTo(encounter("FIGHT"));

		send(rome, "/encounters/answers", Map.of("token", rome.token(), "warparty", 1, "fight", false));

		assertThat(rome.privately().next().get("encounters")).isEqualTo(encounter("PASS"));
		assertThat(carthage.privately().next().get("encounters")).isEqualTo(encounter("PASS"));
	}

	@Test
	void aPlayerIsToldPrivatelyWhyAChoiceAboutAnEncounterIsRejected() throws Exception {
		startGame();
		StompConnection.Messages romeRejections = rome.stomp().subscribe("/user/queue/rejections");

		send(rome, "/encounters/answers", Map.of("token", rome.token(), "warparty", 1, "fight", true));

		assertThat(romeRejections.next())
			.isEqualTo(Map.of("reason", "No Encounter awaits your choice for that Warparty."));
	}

}
