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

/**
 * Players sending each other private Messages over a Game. Time only moves
 * when a test advances the clock, and the test then asks the server to do
 * what its scheduler would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MessagesTest {

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

	String code;

	/**
	 * Every connection a test opens, closed after it.
	 */
	final List<StompConnection> connections = new ArrayList<>();

	/**
	 * A Player's token and one of their connections, following the Messages
	 * and rejections sent to them.
	 */
	record Connected(StompConnection stomp, StompConnection.Messages messages, StompConnection.Messages rejections,
			String token) {
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
	 * Joins Rome, Carthage and Egypt to a new Room, each Player connected with
	 * their token.
	 */
	void joinRoom() throws Exception {
		code = (String) rest().post().uri("/api/rooms").retrieve().body(Map.class).get("code");
		List<Connected> players = new ArrayList<>();
		for (String nation : List.of("Rome", "Carthage", "Egypt")) {
			Map<?, ?> joined = rest().post()
				.uri("/api/rooms/{code}/players", code)
				.body(Map.of("nationName", nation))
				.retrieve()
				.body(Map.class);
			players.add(connect((String) joined.get("token")));
		}
		rome = players.get(0);
		carthage = players.get(1);
		egypt = players.get(2);
		updates = carthage.stomp().subscribe("/topic/rooms/" + code);
	}

	/**
	 * Starts a Game between Rome, Carthage and Egypt, and waits until it has
	 * started.
	 */
	void startGame() throws Exception {
		joinRoom();
		List<Connected> players = List.of(rome, carthage, egypt);
		for (int seat = 0; seat < players.size(); seat++) {
			TurnsTest.readyUp(players.get(seat).stomp(), code, players.get(seat).token(), seat);
		}
		awaitGame(game -> true);
	}

	Connected connect(String token) throws Exception {
		StompConnection stomp = new StompConnection(port, token);
		connections.add(stomp);
		return new Connected(stomp, stomp.subscribe("/user/queue/messages"), stomp.subscribe("/user/queue/rejections"),
				token);
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

	void send(Connected from, String to, String text) {
		from.stomp().send("/app/rooms/" + code + "/messages", Map.of("token", from.token(), "to", to, "text", text));
	}

	/**
	 * The Messages a Player has sent and received, as they get them on
	 * subscribing.
	 */
	List<?> history(Connected player) throws Exception {
		return (List<?>) player.stomp().subscribe("/app/rooms/" + code + "/messages").next().get("messages");
	}

	static Map<String, Object> message(int id, String from, String to, String text) {
		return Map.of("id", id, "from", from, "to", to, "text", text);
	}

	@Test
	void aMessageReachesItsRecipientAndItsSenderButNoOneElse() throws Exception {
		startGame();

		send(rome, "Carthage", "Shall we split Egypt?");

		Map<String, Object> sent = message(1, "Rome", "Carthage", "Shall we split Egypt?");
		assertThat(carthage.messages().next()).isEqualTo(sent);
		assertThat(rome.messages().next()).isEqualTo(sent);
		egypt.messages().expectNone();
	}

	@Test
	void aMessageIsStrippedAndMayBe280CharactersLong() throws Exception {
		startGame();
		String longest = "a".repeat(280);

		send(rome, "Carthage", "  " + longest + "  ");

		assertThat(carthage.messages().next()).isEqualTo(message(1, "Rome", "Carthage", longest));
	}

	@Test
	void aMessageOver280CharactersIsRefused() throws Exception {
		startGame();

		send(rome, "Carthage", "a".repeat(281));

		assertThat(rome.rejections().next())
			.isEqualTo(Map.of("reason", "A Message can be at most 280 characters."));
		carthage.messages().expectNone();
	}

	@Test
	void anEmptyMessageIsRefused() throws Exception {
		startGame();

		send(rome, "Carthage", "   ");

		assertThat(rome.rejections().next()).isEqualTo(Map.of("reason", "Write something to send."));
		carthage.messages().expectNone();
	}

	@Test
	void aMessageToYourselfOrToANationNotInTheGameIsRefused() throws Exception {
		startGame();

		send(rome, "Rome", "Hello me");
		send(rome, "Atlantis", "Hello?");

		assertThat(rome.rejections().next()).isEqualTo(Map.of("reason", "Send your Message to another Nation."));
		assertThat(rome.rejections().next()).isEqualTo(Map.of("reason", "There is no Nation Atlantis in this Game."));
		rome.messages().expectNone();
	}

	@Test
	void noMessageCanBeSentBeforeTheGameStarts() throws Exception {
		joinRoom();

		send(rome, "Carthage", "Ready?");

		assertThat(rome.rejections().next())
			.isEqualTo(Map.of("reason", "Messages can only be sent once the Game has started."));
		carthage.messages().expectNone();
	}

	@Test
	void aRemovedPlayerCanNeitherReceiveNorSendMessages() throws Exception {
		startGame();
		rome.stomp().close();
		awaitGame(game -> !((List<?>) game.get("waitingFor")).isEmpty());
		clock.advance(Duration.ofMinutes(5).plusSeconds(1));
		rooms.removeLongDisconnected();
		awaitGame(game -> !((List<?>) game.get("removed")).isEmpty());
		rome = connect(rome.token());

		send(carthage, "Rome", "Are you there?");
		send(rome, "Carthage", "I'm back!");

		assertThat(carthage.rejections().next())
			.isEqualTo(Map.of("reason", "Rome was removed from the Game, so it can no longer receive Messages."));
		assertThat(rome.rejections().next())
			.isEqualTo(Map.of("reason", "Your Nation was removed from the Game, so you can no longer send Messages."));
		rome.messages().expectNone();
		carthage.messages().expectNone();
	}

	@Test
	void aReconnectingPlayerGetsTheirMessageHistory() throws Exception {
		startGame();
		// Each from its own connection, so each waits for the last to arrive to keep their order.
		send(rome, "Carthage", "Shall we split Egypt?");
		rome.messages().next();
		send(carthage, "Rome", "Deal.");
		rome.messages().next();
		send(egypt, "Rome", "Carthage is plotting against you.");
		rome.messages().next();

		rome.stomp().close();
		awaitGame(game -> !((List<?>) game.get("waitingFor")).isEmpty());
		rome = connect(rome.token());

		assertThat(history(rome)).isEqualTo(List.of(message(1, "Rome", "Carthage", "Shall we split Egypt?"),
				message(2, "Carthage", "Rome", "Deal."),
				message(3, "Egypt", "Rome", "Carthage is plotting against you.")));
		assertThat(history(egypt)).isEqualTo(List.of(message(3, "Egypt", "Rome", "Carthage is plotting against you.")));
	}

	@Test
	void messagesWorkWhileTheGameIsPaused() throws Exception {
		startGame();
		rome.stomp().close();
		awaitGame(game -> !((List<?>) game.get("waitingFor")).isEmpty());

		send(carthage, "Egypt", "Rome dropped out.");
		assertThat(egypt.messages().next()).isEqualTo(message(1, "Carthage", "Egypt", "Rome dropped out."));
		send(egypt, "Rome", "Come back soon.");

		assertThat(egypt.messages().next()).isEqualTo(message(2, "Egypt", "Rome", "Come back soon."));
		rome = connect(rome.token());
		assertThat(history(rome)).isEqualTo(List.of(message(2, "Egypt", "Rome", "Come back soon.")));
	}

}
