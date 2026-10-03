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
 * Tribute as Players experience it over STOMP: offered and answered mid-Turn.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TributeTest {

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

	int turn = 1;

	/**
	 * The view Carthage got of the Turn that started last.
	 */
	Map<?, ?> carthageView;

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
	 * Has both Players say they are done with the Turn, and waits for the view
	 * each gets of the next.
	 */
	void endTurn() throws Exception {
		for (TurnsTest.Connected player : List.of(rome, carthage)) {
			send(player, "/done", Map.of("token", player.token(), "turn", turn));
		}
		rome.privately().next();
		carthageView = carthage.privately().next();
		turn++;
	}

	/**
	 * Starts a Game between Rome and Carthage and has a Warparty of Warrior ×2
	 * from Rome arrive at Carthage.
	 */
	void romeArrivesAtCarthage() throws Exception {
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
		send(rome, "/units", Map.of("token", rome.token(), "unit", "Warrior", "count", 2));
		rome.privately().next();
		endTurn();
		send(rome, "/warparties", Map.of("token", rome.token(), "target", "Carthage", "units", Map.of("Warrior", 2)));
		rome.privately().next();
		endTurn();
		endTurn();
		endTurn();
	}

	@Test
	void anAttackerSeesTributeOfferedMidTurnAndAcceptingItRecallsTheWarpartyAndTellsTheDefender() throws Exception {
		romeArrivesAtCarthage();

		send(carthage, "/tributes", Map.of("token", carthage.token(), "nation", "Rome", "warparty", 1, "gold", 20,
				"population", 3));

		Map<?, ?> offered = Map.of("from", "Carthage", "to", "Rome", "warparty", 1, "gold", 20, "population", 3,
				"answer", "NONE");
		assertThat(carthage.privately().next().get("tributes")).isEqualTo(List.of(offered));
		Map<?, ?> romeOffered = rome.privately().next();
		assertThat(romeOffered.get("tributes")).isEqualTo(List.of(offered));
		int romeGold = (Integer) romeOffered.get("gold");
		int romePopulation = (Integer) romeOffered.get("population");
		int carthageGold = (Integer) carthageView.get("gold");
		int carthagePopulation = (Integer) carthageView.get("population");

		send(rome, "/tributes/answers", Map.of("token", rome.token(), "warparty", 1, "accept", true));

		Map<?, ?> romeAccepted = rome.privately().next();
		assertThat(romeAccepted.get("gold")).isEqualTo(romeGold + 20);
		assertThat(romeAccepted.get("population")).isEqualTo(romePopulation + 3);
		assertThat(romeAccepted.get("warparties")).isEqualTo(List.of(
				Map.of("id", 1, "target", "Carthage", "units", Map.of("Warrior", 2), "strength", 6.0, "homeward", true, "turnsLeft", 3,
						"turnsTravelled", 3, "plunder", Map.of("gold", 0, "research", 0))));
		Map<?, ?> carthageAccepted = carthage.privately().next();
		assertThat(carthageAccepted.get("gold")).isEqualTo(carthageGold - 20);
		assertThat(carthageAccepted.get("population")).isEqualTo(carthagePopulation - 3);
		assertThat(carthageAccepted.get("arrivals")).isEqualTo(List.of());
		assertThat(carthageAccepted.get("tributes")).isEqualTo(List.of(Map.of("from", "Carthage", "to", "Rome",
				"warparty", 1, "gold", 20, "population", 3, "answer", "ACCEPTED")));
	}

	@Test
	void aDefenderSeesTheWarpartyThatArrivedAndIsToldPrivatelyWhyAnOfferIsRejected() throws Exception {
		romeArrivesAtCarthage();
		assertThat(carthageView.get("arrivals")).isEqualTo(List.of(Map.of("nation", "Rome", "warparty", 1)));
		StompConnection.Messages carthageRejections = carthage.stomp().subscribe("/user/queue/rejections");

		send(carthage, "/tributes", Map.of("token", carthage.token(), "nation", "Rome", "warparty", 1, "gold", 0,
				"population", 0));

		assertThat(carthageRejections.next()).isEqualTo(Map.of("reason", "Offer some Gold or Population as Tribute."));
	}

}
