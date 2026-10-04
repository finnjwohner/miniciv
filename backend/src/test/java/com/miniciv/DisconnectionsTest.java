package com.miniciv;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.AfterEach;
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
import static org.assertj.core.api.Assertions.assertThatException;

/**
 * Players dropping their connection mid-Game and rejoining with their token.
 * Time only moves when a test advances the clock, and the test then asks the
 * server to do what its scheduler would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DisconnectionsTest {

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		TestClock testClock() {
			return new TestClock();
		}

	}

	static final String REMOVAL_REPORT = "Rome was removed from the Game, as its Player was disconnected for too long.";

	@LocalServerPort
	int port;

	@Autowired
	TestClock clock;

	@Autowired
	Rooms rooms;

	String code;

	/**
	 * Every connection a test opens, closed after it.
	 */
	final List<StompConnection> connections = new ArrayList<>();

	/**
	 * A Player's token and one of their connections, following their private
	 * destination.
	 */
	record Connected(StompConnection stomp, StompConnection.Messages privately, String token) {
	}

	Connected rome;

	Connected carthage;

	Connected egypt;

	/**
	 * Updates to the Room, as Carthage receives them.
	 */
	StompConnection.Messages updates;

	@AfterEach
	void disconnect() {
		connections.forEach(StompConnection::close);
	}

	RestClient rest() {
		return RestClient.create("http://localhost:" + port);
	}

	/**
	 * Starts a Game between the given Nations, each Player connected with their
	 * token, and waits until each has their view of the first Turn.
	 */
	void startGame(String... nations) throws Exception {
		code = (String) rest().post().uri("/api/rooms").retrieve().body(Map.class).get("code");
		List<Connected> players = new ArrayList<>();
		for (String nation : nations) {
			Map<?, ?> joined = rest().post()
				.uri("/api/rooms/{code}/players", code)
				.body(Map.of("nationName", nation))
				.retrieve()
				.body(Map.class);
			players.add(connect((String) joined.get("token")));
		}
		rome = players.get(0);
		carthage = players.get(1);
		egypt = players.size() > 2 ? players.get(2) : null;
		updates = carthage.stomp().subscribe("/topic/rooms/" + code);
		for (int seat = 0; seat < players.size(); seat++) {
			TurnsTest.readyUp(players.get(seat).stomp(), code, players.get(seat).token(), seat);
		}
		for (Connected player : players) {
			assertThat(player.privately().next().get("turn")).isEqualTo(1);
		}
	}

	Connected connect(String token) throws Exception {
		StompConnection stomp = new StompConnection(port, token);
		connections.add(stomp);
		return new Connected(stomp, stomp.subscribe("/user/queue/game"), token);
	}

	/**
	 * Closes a Player's connection and waits until everyone sees the Game
	 * waiting for them.
	 */
	void dropConnection(Connected player, String nation) throws Exception {
		player.stomp().close();
		awaitGame(game -> waitingFor(game).contains(nation));
	}

	/**
	 * Reconnects a Player with their token and waits until nobody sees the Game
	 * waiting for them.
	 */
	Connected reconnect(Connected player, String nation) throws Exception {
		Connected reconnected = connect(player.token());
		awaitGame(game -> !waitingFor(game).contains(nation));
		return reconnected;
	}

	/**
	 * Waits for an update to the Room whose Game satisfies the condition.
	 */
	Map<?, ?> awaitGame(Predicate<Map<?, ?>> condition) throws Exception {
		while (true) {
			Map<?, ?> game = (Map<?, ?>) updates.next().get("game");
			if (game != null && condition.test(game)) {
				return game;
			}
		}
	}

	static List<Object> waitingFor(Map<?, ?> game) {
		return ((List<?>) game.get("waitingFor")).stream().map(waiting -> (Object) ((Map<?, ?>) waiting).get("nation")).toList();
	}

	Map<?, ?> currentGame() throws Exception {
		try (StompConnection stomp = new StompConnection(port)) {
			return (Map<?, ?>) stomp.subscribe("/app/rooms/" + code).next().get("game");
		}
	}

	void done(Connected player, int turn) {
		player.stomp().send("/app/rooms/" + code + "/done", Map.of("token", player.token(), "turn", turn));
	}

	@Test
	void whileAPlayerIsDisconnectedEveryoneSeesTheGameWaitingForThemAndHowLongBeforeTheyAreRemoved() throws Exception {
		startGame("Rome", "Carthage");

		dropConnection(rome, "Rome");
		clock.advance(Duration.ofSeconds(30));

		Map<?, ?> game = currentGame();
		assertThat(game.get("waitingFor")).isEqualTo(List.of(Map.of("nation", "Rome", "millisLeft", 270_000)));
		assertThat(game.get("millisLeft")).isEqualTo(120_000);
	}

	@Test
	void theTurnTimerPausesWhileAPlayerIsDisconnectedAndResumesWhenTheyReconnect() throws Exception {
		startGame("Rome", "Carthage");
		clock.advance(Duration.ofSeconds(20));
		dropConnection(rome, "Rome");

		clock.advance(Duration.ofMinutes(3));
		rooms.endDueTurns();
		assertThat(currentGame().get("turn")).isEqualTo(1);

		rome = reconnect(rome, "Rome");
		assertThat(currentGame().get("millisLeft")).isEqualTo(100_000);
		clock.advance(Duration.ofSeconds(100));
		rooms.endDueTurns();
		assertThat(rome.privately().next().get("turn")).isEqualTo(2);
	}

	@Test
	void aPlayerWhoReconnectsWithTheirTokenGetsTheirOwnNationAndItsLaterViews() throws Exception {
		startGame("Rome", "Carthage");
		dropConnection(rome, "Rome");

		rome = reconnect(rome, "Rome");

		assertThat(rome.stomp().subscribe("/app/rooms/" + code + "/nation").next().get("nation")).isEqualTo("Rome");
		done(rome, 1);
		done(carthage, 1);
		Map<?, ?> view = rome.privately().next();
		assertThat(view.get("nation")).isEqualTo("Rome");
		assertThat(view.get("turn")).isEqualTo(2);
	}

	@Test
	void nobodyCanActWhileTheGameIsPaused() throws Exception {
		startGame("Rome", "Carthage");
		StompConnection.Messages rejections = carthage.stomp().subscribe("/user/queue/rejections");
		dropConnection(rome, "Rome");

		carthage.stomp()
			.send("/app/rooms/" + code + "/units", Map.of("token", carthage.token(), "unit", "Warrior", "count", 1));

		assertThat(rejections.next()).isEqualTo(Map.of("reason", "The Game is paused until Rome reconnects."));
		carthage.privately().expectNone();
	}

	@Test
	void aTurnEveryoneIsDoneWithEndsOnlyOnceTheGameResumes() throws Exception {
		startGame("Rome", "Carthage");
		done(rome, 1);
		dropConnection(rome, "Rome");

		done(carthage, 1);
		awaitGame(game -> true);
		carthage.privately().expectNone();
		assertThat(currentGame().get("turn")).isEqualTo(1);

		rome = reconnect(rome, "Rome");
		assertThat(carthage.privately().next().get("turn")).isEqualTo(2);
	}

	@Test
	void closingOneOfTwoConnectionsDoesNotPauseTheGame() throws Exception {
		startGame("Rome", "Carthage");
		Connected secondTab = connect(rome.token());

		rome.stomp().close();
		done(secondTab, 1);
		done(carthage, 1);

		assertThat(secondTab.privately().next().get("turn")).isEqualTo(2);
		assertThat(waitingFor(currentGame())).isEmpty();
	}

	@Test
	void aPlayerDisconnectedForMoreThanFiveMinutesInTotalHasTheirNationRemovedAndTheGameGoesOn() throws Exception {
		startGame("Rome", "Carthage", "Egypt");
		dropConnection(rome, "Rome");
		clock.advance(Duration.ofMinutes(3));
		rooms.removeLongDisconnected();
		rome = reconnect(rome, "Rome");
		dropConnection(rome, "Rome");
		clock.advance(Duration.ofMinutes(2));
		rooms.removeLongDisconnected();
		assertThat(currentGame().get("removed")).isEqualTo(List.of());

		clock.advance(Duration.ofSeconds(1));
		rooms.removeLongDisconnected();

		Map<?, ?> game = awaitGame(g -> !((List<?>) g.get("removed")).isEmpty());
		assertThat(game.get("removed")).isEqualTo(List.of("Rome"));
		assertThat(game.get("waitingFor")).isEqualTo(List.of());
		assertThat(game.get("winner")).isNull();
		Map<?, ?> carthageView = carthage.privately().next();
		assertThat(carthageView.get("map")).isEqualTo(List.of(TurnsTest.onMap("Rome", 0, "REMOVED"),
				TurnsTest.onMap("Carthage", 1, "IN_GAME"), TurnsTest.onMap("Egypt", 2, "IN_GAME")));
		assertThat(egypt.privately().next().get("map")).isEqualTo(carthageView.get("map"));
		done(carthage, 1);
		done(egypt, 1);
		Map<?, ?> report = (Map<?, ?>) carthage.privately().next().get("turnReport");
		Map<?, ?> alerts = (Map<?, ?>) ((List<?>) report.get("sections")).getFirst();
		assertThat(alerts.get("kind")).isEqualTo("DECISIONS_AND_ALERTS");
		assertThat((List<?>) alerts.get("lines")).first().isEqualTo(Map.of("parts", List.of(Map.of("text", REMOVAL_REPORT))));
	}

	@Test
	void whenARemovalLeavesOneNationItWinsTheGame() throws Exception {
		startGame("Rome", "Carthage");
		dropConnection(rome, "Rome");

		clock.advance(Duration.ofMinutes(5).plusSeconds(1));
		rooms.removeLongDisconnected();

		Map<?, ?> game = awaitGame(g -> g.get("winner") != null);
		assertThat(game.get("winner")).isEqualTo("Carthage");
		assertThat(game.get("conquered")).isEqualTo(List.of());
		assertThat(carthage.privately().next().get("winner")).isEqualTo("Carthage");
	}

	@Test
	void anInvalidTokenCannotConnectOrResumeTheGame() throws Exception {
		startGame("Rome", "Carthage");
		dropConnection(rome, "Rome");

		assertThatException().isThrownBy(() -> new StompConnection(port, "not-a-token"));

		assertThat(waitingFor(currentGame())).containsExactly("Rome");
	}

	@Test
	void aConnectionWithoutATokenSeesNoNationAndDoesNotResumeTheGame() throws Exception {
		startGame("Rome", "Carthage");
		dropConnection(rome, "Rome");

		try (StompConnection anonymous = new StompConnection(port)) {
			anonymous.subscribe("/app/rooms/" + code + "/nation").expectNone();
			assertThat(waitingFor(currentGame())).containsExactly("Rome");
		}
	}

}
