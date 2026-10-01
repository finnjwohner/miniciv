package com.miniciv;

import java.time.Duration;
import java.util.HashMap;
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

import com.miniciv.engine.BuildingType;
import com.miniciv.engine.Ruleset;
import com.miniciv.room.Rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Turns, and the actions Players take in them, as Players experience them over
 * STOMP. Time only moves when a test advances the clock, and the test then asks
 * the server to end any Turn that is due, as its scheduler would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TurnsTest {

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
			return new Ruleset(List.of(), List.of(new BuildingType("Farm", 30, 2, 1, 0)), List.of());
		}

	}

	static final String BASE_INCOME_REPORT = "Your Nation gained 10 Gold, 2 Population and 3 Research.";

	@LocalServerPort
	int port;

	@Autowired
	TestClock clock;

	@Autowired
	Rooms rooms;

	RestClient rest() {
		return RestClient.create("http://localhost:" + port);
	}

	/**
	 * A Player in a started Game, connected with their token and following
	 * their private destination.
	 */
	record Connected(StompConnection stomp, StompConnection.Messages privately, String playerId, String token)
			implements AutoCloseable {

		@Override
		public void close() {
			stomp.close();
		}

	}

	String code;

	Connected rome;

	Connected carthage;

	/**
	 * Creates a Room with Rome and Carthage, connects both and readies them so
	 * that the Game starts.
	 */
	void startGame() throws Exception {
		joinRomeAndCarthage();
		readyBoth();
	}

	void joinRomeAndCarthage() throws Exception {
		code = (String) rest().post().uri("/api/rooms").retrieve().body(Map.class).get("code");
		rome = connect(join("Rome"));
		carthage = connect(join("Carthage"));
	}

	void readyBoth() {
		rome.stomp().send("/app/rooms/" + code + "/ready", Map.of("token", rome.token(), "ready", true));
		carthage.stomp().send("/app/rooms/" + code + "/ready", Map.of("token", carthage.token(), "ready", true));
	}

	Map<?, ?> join(String nationName) {
		return rest().post().uri("/api/rooms/{code}/players", code).body(Map.of("nationName", nationName)).retrieve().body(Map.class);
	}

	Connected connect(Map<?, ?> joined) throws Exception {
		String token = (String) joined.get("token");
		StompConnection stomp = new StompConnection(port, token);
		return new Connected(stomp, stomp.subscribe("/user/queue/game"), (String) joined.get("playerId"), token);
	}

	void startBuilding(Connected player, String building) {
		player.stomp().send("/app/rooms/" + code + "/buildings", Map.of("token", player.token(), "building", building));
	}

	void done(Connected player, int turn) {
		player.stomp().send("/app/rooms/" + code + "/done", Map.of("token", player.token(), "turn", turn));
	}

	Map<?, ?> currentGame() throws Exception {
		try (StompConnection stomp = new StompConnection(port)) {
			return (Map<?, ?>) stomp.subscribe("/app/rooms/" + code).next().get("game");
		}
	}

	/**
	 * A Nation's view as its Player receives it.
	 */
	static Map<String, Object> view(int version, int turn, String nation, int gold, int population, int research,
			List<String> otherNations, List<Map<String, Object>> construction, Map<String, Object> turnReport) {
		Map<String, Object> view = new HashMap<>();
		view.put("version", version);
		view.put("turn", turn);
		view.put("nation", nation);
		view.put("gold", gold);
		view.put("population", population);
		view.put("research", research);
		view.put("otherNations", otherNations);
		view.put("availableBuildings", List.of(Map.of("name", "Farm", "gold", 30, "turns", 2, "populationPerTurn", 1,
				"goldPerPopulation", 0.0)));
		view.put("buildings", Map.of());
		view.put("construction", construction);
		view.put("turnReport", turnReport);
		return view;
	}

	@AfterEach
	void disconnect() {
		rome.close();
		carthage.close();
	}

	@Test
	void eachPlayerReceivesTheirOwnViewPrivatelyWhenTheGameStarts() throws Exception {
		startGame();

		assertThat(rome.privately().next()).isEqualTo(view(1, 1, "Rome", 50, 10, 0, List.of("Carthage"), List.of(), null));
		assertThat(carthage.privately().next()).isEqualTo(view(1, 1, "Carthage", 50, 10, 0, List.of("Rome"), List.of(), null));
	}

	@Test
	void theTurnEndsEarlyOnceEveryPlayerIsDoneAndEachPlayerReceivesOnlyTheirOwnTurnReport() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();

		done(rome, 1);
		done(carthage, 1);

		Map<String, Object> report = Map.of("turn", 1, "lines", List.of(BASE_INCOME_REPORT));
		assertThat(rome.privately().next()).isEqualTo(view(2, 2, "Rome", 60, 12, 3, List.of("Carthage"), List.of(), report));
		assertThat(carthage.privately().next()).isEqualTo(view(2, 2, "Carthage", 60, 12, 3, List.of("Rome"), List.of(), report));
		rome.privately().expectNone();
		carthage.privately().expectNone();
	}

	@Test
	void theTurnEndsAfterTwoMinutesEvenIfNotEveryPlayerIsDone() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();
		done(rome, 1);

		clock.advance(Duration.ofSeconds(119));
		rooms.endDueTurns();
		assertThat(currentGame().get("turn")).isEqualTo(1);

		clock.advance(Duration.ofSeconds(1));
		rooms.endDueTurns();
		assertThat(rome.privately().next().get("turn")).isEqualTo(2);
		assertThat(carthage.privately().next().get("turn")).isEqualTo(2);
	}

	@Test
	void everyoneInTheRoomSeesHowLongIsLeftInTheTurn() throws Exception {
		startGame();
		rome.privately().next();
		assertThat(currentGame().get("millisLeft")).isEqualTo(120_000);

		clock.advance(Duration.ofSeconds(30));
		assertThat(currentGame().get("millisLeft")).isEqualTo(90_000);

		done(rome, 1);
		done(carthage, 1);
		rome.privately().next();
		assertThat(currentGame()).isEqualTo(Map.of("turn", 2, "nations", List.of("Rome", "Carthage"), "millisLeft", 120_000));
	}

	@Test
	void aPlayerWhoReconnectsMidGameGetsTheirCurrentView() throws Exception {
		startGame();
		rome.privately().next();

		try (StompConnection reconnected = new StompConnection(port, rome.token())) {
			assertThat(reconnected.subscribe("/app/rooms/" + code + "/nation").next())
				.isEqualTo(view(1, 1, "Rome", 50, 10, 0, List.of("Carthage"), List.of(), null));
		}
	}

	@Test
	void everyoneInTheRoomSeesWhichPlayersAreDoneWithTheTurn() throws Exception {
		startGame();
		rome.privately().next();
		StompConnection.Messages updates = rome.stomp().subscribe("/topic/rooms/" + code);

		done(rome, 1);

		assertThat((List<?>) updates.next().get("players")).extracting("nationName", "done")
			.containsExactly(tuple("Rome", true), tuple("Carthage", false));
	}

	@Test
	void aPlayerCannotFollowAnotherPlayersPrivateDestination() throws Exception {
		joinRomeAndCarthage();
		StompConnection.Messages snooped = rome.stomp().subscribe("/user/" + carthage.playerId() + "/queue/game");
		readyBoth();

		assertThat(carthage.privately().next().get("nation")).isEqualTo("Carthage");
		assertThat(rome.privately().next().get("nation")).isEqualTo("Rome");
		snooped.expectNone();
	}

	@Test
	void aDoneSentForATurnThatHasAlreadyEndedIsIgnored() throws Exception {
		startGame();
		rome.privately().next();
		done(rome, 1);
		done(carthage, 1);
		assertThat(rome.privately().next().get("turn")).isEqualTo(2);

		done(rome, 1);

		assertThat((List<?>) rome.stomp().subscribe("/app/rooms/" + code).next().get("players")).extracting("done")
			.containsExactly(false, false);
	}

	@Test
	void aPlayerWhoStartsABuildingReceivesTheirUpdatedViewAtOnce() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();

		startBuilding(rome, "Farm");

		assertThat(rome.privately().next()).isEqualTo(view(2, 1, "Rome", 20, 10, 0, List.of("Carthage"),
				List.of(Map.of("building", "Farm", "turnsLeft", 2)), null));
		carthage.privately().expectNone();
	}

	@Test
	void aPlayerWhoseBuildingIsRejectedIsToldWhyPrivately() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();
		StompConnection.Messages romeRejections = rome.stomp().subscribe("/user/queue/rejections");
		StompConnection.Messages carthageRejections = carthage.stomp().subscribe("/user/queue/rejections");
		startBuilding(rome, "Farm");
		rome.privately().next();

		startBuilding(rome, "Farm");

		assertThat(romeRejections.next()).isEqualTo(Map.of("reason", "Farm costs 30 Gold, but you have only 20."));
		rome.privately().expectNone();
		carthageRejections.expectNone();
	}

}
