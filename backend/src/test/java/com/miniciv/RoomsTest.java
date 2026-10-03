package com.miniciv;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomsTest {

	@LocalServerPort
	int port;

	RestClient rest() {
		return RestClient.create("http://localhost:" + port);
	}

	String createRoom() {
		return (String) rest().post().uri("/api/rooms").retrieve().body(Map.class).get("code");
	}

	Map<?, ?> join(String code, String nationName) {
		return rest().post()
			.uri("/api/rooms/{code}/players", code)
			.body(Map.of("nationName", nationName))
			.retrieve()
			.body(Map.class);
	}

	/**
	 * Attempts a join that should be refused, returning the HTTP status and the
	 * message shown to the Player.
	 */
	Refusal refusedJoin(String code, String nationName) {
		return rest().post()
			.uri("/api/rooms/{code}/players", code)
			.body(Map.of("nationName", nationName))
			.exchange((request, response) -> new Refusal(response.getStatusCode().value(),
					(String) response.bodyTo(Map.class).get("detail")));
	}

	record Refusal(int status, String message) {
	}

	/**
	 * A Player as everyone in the Room sees them, holding the colour and emblem
	 * a Player joining at the given seat, counting from 0, is given.
	 */
	static Map<String, Object> seen(Map<?, ?> joined, String nationName, String ruler, int seat, boolean ready) {
		return Map.of("id", joined.get("playerId"), "nationName", nationName, "ruler", ruler, "colour",
				IdentityTest.COLOURS.get(seat), "emblem", IdentityTest.EMBLEMS.get(seat), "ready", ready, "done", false);
	}

	/**
	 * Names a Player's Ruler, keeping the colour and emblem they were given at
	 * the given seat, and readies them up.
	 */
	void readyUp(StompConnection stomp, String code, Map<?, ?> joined, int seat) {
		TurnsTest.readyUp(stomp, code, (String) joined.get("token"), seat);
	}

	@Test
	void creatingARoomReturnsARoomCodeAndInviteLink() {
		Map<?, ?> room = rest().post().uri("/api/rooms").retrieve().body(Map.class);

		String code = (String) room.get("code");
		assertThat(code).matches("[A-Z0-9]{6}");
		assertThat(room.get("inviteLink")).isEqualTo("http://localhost:" + port + "/rooms/" + code);
	}

	@Test
	void joiningARoomIssuesAPlayerTokenAndAddsThePlayerToTheRoom() throws Exception {
		String code = createRoom();

		Map<?, ?> player = join(code, "Rome");

		assertThat((String) player.get("token")).isNotBlank();
		try (StompConnection stomp = new StompConnection(port)) {
			Map<?, ?> room = stomp.subscribe("/app/rooms/" + code).next();
			assertThat(room.get("players")).isEqualTo(List.of(seen(player, "Rome", "", 0, false)));
		}
	}

	@Test
	void playersInTheRoomSeeJoinsAndReadyStatesLive() throws Exception {
		String code = createRoom();
		Map<?, ?> rome = join(code, "Rome");
		try (StompConnection stomp = new StompConnection(port)) {
			StompConnection.Messages updates = stomp.subscribe("/topic/rooms/" + code);
			stomp.subscribe("/app/rooms/" + code).next();

			Map<?, ?> carthage = join(code, "Carthage");
			assertThat(updates.next().get("players"))
				.isEqualTo(List.of(seen(rome, "Rome", "", 0, false), seen(carthage, "Carthage", "", 1, false)));

			readyUp(stomp, code, carthage, 1);
			assertThat(updates.next().get("players"))
				.isEqualTo(List.of(seen(rome, "Rome", "", 0, false), seen(carthage, "Carthage", "Ruler 2", 1, false)));
			assertThat(updates.next().get("players"))
				.isEqualTo(List.of(seen(rome, "Rome", "", 0, false), seen(carthage, "Carthage", "Ruler 2", 1, true)));

			stomp.send("/app/rooms/" + code + "/ready", Map.of("token", carthage.get("token"), "ready", false));
			assertThat(updates.next().get("players"))
				.isEqualTo(List.of(seen(rome, "Rome", "", 0, false), seen(carthage, "Carthage", "Ruler 2", 1, false)));
		}
	}

	@Test
	void eachRoomUpdateHasAHigherVersionThanTheLast() throws Exception {
		String code = createRoom();
		Map<?, ?> rome = join(code, "Rome");
		try (StompConnection stomp = new StompConnection(port)) {
			StompConnection.Messages updates = stomp.subscribe("/topic/rooms/" + code);
			int current = (Integer) stomp.subscribe("/app/rooms/" + code).next().get("version");

			join(code, "Carthage");
			int afterJoin = (Integer) updates.next().get("version");
			readyUp(stomp, code, rome, 0);
			int afterIdentity = (Integer) updates.next().get("version");
			int afterReady = (Integer) updates.next().get("version");

			assertThat(afterJoin).isGreaterThan(current);
			assertThat(afterIdentity).isGreaterThan(afterJoin);
			assertThat(afterReady).isGreaterThan(afterIdentity);
		}
	}

	@Test
	void theGameStartsOnTurnOneOnceAllPlayersAreReady() throws Exception {
		String code = createRoom();
		Map<?, ?> rome = join(code, "Rome");
		Map<?, ?> carthage = join(code, "Carthage");
		try (StompConnection stomp = new StompConnection(port)) {
			StompConnection.Messages updates = stomp.subscribe("/topic/rooms/" + code);
			stomp.subscribe("/app/rooms/" + code).next();

			readyUp(stomp, code, rome, 0);
			updates.next();
			assertThat(updates.next().get("game")).isNull();

			readyUp(stomp, code, carthage, 1);
			updates.next();
			Map<?, ?> game = (Map<?, ?>) updates.next().get("game");
			assertThat(game.get("turn")).isEqualTo(1);
			assertThat(game.get("nations")).isEqualTo(List.of("Rome", "Carthage"));
		}
	}

	@Test
	void aSingleReadyPlayerDoesNotStartAGame() throws Exception {
		String code = createRoom();
		Map<?, ?> rome = join(code, "Rome");
		try (StompConnection stomp = new StompConnection(port)) {
			StompConnection.Messages updates = stomp.subscribe("/topic/rooms/" + code);
			stomp.subscribe("/app/rooms/" + code).next();

			readyUp(stomp, code, rome, 0);
			updates.next();

			Map<?, ?> room = updates.next();
			assertThat(room.get("players")).isEqualTo(List.of(seen(rome, "Rome", "Ruler 1", 0, true)));
			assertThat(room.get("game")).isNull();
		}
	}

	@Test
	void joiningIsRefusedWhenTheRoomCodeIsInvalid() {
		assertThat(refusedJoin("NOROOM", "Rome")).isEqualTo(new Refusal(404, "There is no Room with code NOROOM."));
	}

	@Test
	void joiningIsRefusedWhenTheRoomAlreadyHasFourPlayers() {
		String code = createRoom();
		join(code, "Rome");
		join(code, "Carthage");
		join(code, "Egypt");
		join(code, "Persia");

		assertThat(refusedJoin(code, "Greece")).isEqualTo(new Refusal(409, "This Room already has 4 Players."));
	}

	@Test
	void joiningIsRefusedOnceTheGameHasStarted() throws Exception {
		String code = createRoom();
		Map<?, ?> rome = join(code, "Rome");
		Map<?, ?> carthage = join(code, "Carthage");
		try (StompConnection stomp = new StompConnection(port)) {
			StompConnection.Messages updates = stomp.subscribe("/topic/rooms/" + code);
			stomp.subscribe("/app/rooms/" + code).next();
			readyUp(stomp, code, rome, 0);
			readyUp(stomp, code, carthage, 1);
			for (int i = 0; i < 3; i++) {
				updates.next();
			}
			assertThat(updates.next().get("game")).isNotNull();
		}

		assertThat(refusedJoin(code, "Egypt")).isEqualTo(new Refusal(409, "The Game in this Room has already started."));
	}

	@Test
	void joiningIsRefusedWithoutANationName() {
		assertThat(refusedJoin(createRoom(), "  ")).isEqualTo(new Refusal(400, "Choose a name for your Nation."));
	}

	@Test
	void joiningIsRefusedWhenTheNationNameIsTaken() {
		String code = createRoom();
		join(code, "Rome");

		assertThat(refusedJoin(code, "rome")).isEqualTo(new Refusal(409, "A Nation called rome is already in this Room."));
	}

}
