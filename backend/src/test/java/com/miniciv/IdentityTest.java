package com.miniciv;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each Player names their Nation's Ruler and picks a colour and emblem in the
 * Room, unique among its Players, until the Game starts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdentityTest {

	static final List<String> COLOURS = List.of("#e2574c", "#e8913a", "#f2c14e", "#7bc96f", "#4fb8b0", "#5b8def",
			"#a678de", "#e07bb5");

	static final List<String> EMBLEMS = List.of("crown", "moon", "sun", "heart", "star", "fire", "zap", "anchor",
			"tree", "fish", "feather", "key", "gem", "dog", "compass", "chess", "snake", "snowflake", "ship", "bell",
			"target");

	@LocalServerPort
	int port;

	String code;

	/**
	 * A connection following the Room's broadcasts.
	 */
	StompConnection watcher;

	StompConnection.Messages updates;

	final List<StompConnection> connections = new ArrayList<>();

	/**
	 * A Player in the Room, connected with their token and following the
	 * reasons their requests were rejected.
	 */
	record Joined(String id, String token, StompConnection stomp, StompConnection.Messages rejections) {
	}

	@BeforeEach
	void createRoom() throws Exception {
		code = (String) RestClient.create("http://localhost:" + port)
			.post()
			.uri("/api/rooms")
			.retrieve()
			.body(Map.class)
			.get("code");
		watcher = new StompConnection(port);
		connections.add(watcher);
		updates = watcher.subscribe("/topic/rooms/" + code);
		watcher.subscribe("/app/rooms/" + code).next();
	}

	@AfterEach
	void disconnect() {
		connections.forEach(StompConnection::close);
	}

	/**
	 * Joins the Room and waits for everyone to see the join.
	 */
	Joined join(String nationName) throws Exception {
		Map<?, ?> joined = RestClient.create("http://localhost:" + port)
			.post()
			.uri("/api/rooms/{code}/players", code)
			.body(Map.of("nationName", nationName))
			.retrieve()
			.body(Map.class);
		updates.next();
		String token = (String) joined.get("token");
		StompConnection stomp = new StompConnection(port, token);
		connections.add(stomp);
		return new Joined((String) joined.get("playerId"), token, stomp, stomp.subscribe("/user/queue/rejections"));
	}

	void identify(Joined player, String ruler, String colour, String emblem) {
		player.stomp()
			.send("/app/rooms/" + code + "/identity",
					Map.of("token", player.token(), "ruler", ruler, "colour", colour, "emblem", emblem));
	}

	void ready(Joined player, boolean ready) {
		player.stomp().send("/app/rooms/" + code + "/ready", Map.of("token", player.token(), "ready", ready));
	}

	/**
	 * The Room as it is now, as a new subscriber is sent it.
	 */
	Map<?, ?> room() throws Exception {
		return watcher.subscribe("/app/rooms/" + code).next();
	}

	static Map<String, Object> player(Joined player, String nationName, String ruler, String colour, String emblem,
			boolean ready) {
		return Map.of("id", player.id(), "nationName", nationName, "ruler", ruler, "colour", colour, "emblem",
				emblem, "ready", ready, "done", false);
	}

	@Test
	void theRoomOffersTheColoursAndEmblemsToPickFrom() throws Exception {
		Map<?, ?> room = room();

		assertThat(room.get("colours")).isEqualTo(COLOURS);
		assertThat(room.get("emblems")).isEqualTo(EMBLEMS);
	}

	@Test
	void aJoiningPlayerGetsTheFirstFreeColourAndEmblemAndNoRulerYet() throws Exception {
		Joined rome = join("Rome");
		identify(rome, "Aster", COLOURS.get(1), EMBLEMS.get(2));
		updates.next();

		Joined carthage = join("Carthage");
		Joined egypt = join("Egypt");

		assertThat(room().get("players")).isEqualTo(List.of(player(rome, "Rome", "Aster", COLOURS.get(1), EMBLEMS.get(2), false),
				player(carthage, "Carthage", "", COLOURS.get(0), EMBLEMS.get(0), false),
				player(egypt, "Egypt", "", COLOURS.get(2), EMBLEMS.get(1), false)));
	}

	@Test
	void aPlayerSetsTheirRulerColourAndEmblemAndEveryoneInTheRoomSeesIt() throws Exception {
		Joined rome = join("Rome");

		identify(rome, "  Queen Aster  ", COLOURS.get(5), EMBLEMS.get(11));

		assertThat(updates.next().get("players"))
			.isEqualTo(List.of(player(rome, "Rome", "Queen Aster", COLOURS.get(5), EMBLEMS.get(11), false)));
		rome.rejections().expectNone();
	}

	@Test
	void aColourOrEmblemAnotherPlayerHasIsRefusedButAPlayerMayKeepTheirOwn() throws Exception {
		Joined rome = join("Rome");
		Joined carthage = join("Carthage");

		identify(carthage, "Dido", COLOURS.get(0), EMBLEMS.get(1));
		assertThat(carthage.rejections().next())
			.isEqualTo(Map.of("reason", "Another Nation in this Room already has that colour."));
		identify(carthage, "Dido", COLOURS.get(1), EMBLEMS.get(0));
		assertThat(carthage.rejections().next())
			.isEqualTo(Map.of("reason", "Another Nation in this Room already has that emblem."));
		identify(rome, "Aster", COLOURS.get(0), EMBLEMS.get(0));

		assertThat(updates.next().get("players")).isEqualTo(
				List.of(player(rome, "Rome", "Aster", COLOURS.get(0), EMBLEMS.get(0), false),
						player(carthage, "Carthage", "", COLOURS.get(1), EMBLEMS.get(1), false)));
	}

	@Test
	void aColourOrEmblemNotOnOfferIsRefused() throws Exception {
		Joined rome = join("Rome");

		identify(rome, "Aster", "#000000", EMBLEMS.get(0));
		assertThat(rome.rejections().next()).isEqualTo(Map.of("reason", "There is no colour #000000 to pick."));
		identify(rome, "Aster", COLOURS.get(0), "dragon");
		assertThat(rome.rejections().next()).isEqualTo(Map.of("reason", "There is no emblem dragon to pick."));
		updates.expectNone();
	}

	@Test
	void aRulerNeedsANameOfAtMostTwentyCharacters() throws Exception {
		Joined rome = join("Rome");

		identify(rome, "   ", COLOURS.get(0), EMBLEMS.get(0));
		assertThat(rome.rejections().next()).isEqualTo(Map.of("reason", "Name your Ruler."));
		identify(rome, "Aster the Magnificent", COLOURS.get(0), EMBLEMS.get(0));
		assertThat(rome.rejections().next())
			.isEqualTo(Map.of("reason", "Your Ruler's name can be at most 20 characters."));
		updates.expectNone();

		identify(rome, " Aster the Magnifico ", COLOURS.get(0), EMBLEMS.get(0));
		assertThat(updates.next().get("players"))
			.isEqualTo(List.of(player(rome, "Rome", "Aster the Magnifico", COLOURS.get(0), EMBLEMS.get(0), false)));
	}

	@Test
	void aPlayerCannotReadyUpUntilTheyHaveNamedTheirRuler() throws Exception {
		Joined rome = join("Rome");

		ready(rome, true);

		assertThat(rome.rejections().next()).isEqualTo(Map.of("reason", "Name your Ruler before you ready up."));
		updates.expectNone();
		identify(rome, "Aster", COLOURS.get(0), EMBLEMS.get(0));
		updates.next();
		ready(rome, true);
		assertThat(updates.next().get("players"))
			.isEqualTo(List.of(player(rome, "Rome", "Aster", COLOURS.get(0), EMBLEMS.get(0), true)));
	}

	@Test
	void changingIdentityLeavesAPlayerReady() throws Exception {
		Joined rome = join("Rome");
		identify(rome, "Aster", COLOURS.get(0), EMBLEMS.get(0));
		ready(rome, true);
		updates.next();
		updates.next();

		identify(rome, "Aster II", COLOURS.get(3), EMBLEMS.get(3));

		assertThat(updates.next().get("players"))
			.isEqualTo(List.of(player(rome, "Rome", "Aster II", COLOURS.get(3), EMBLEMS.get(3), true)));
	}

	@Test
	void identityIsLockedOnceTheGameHasStarted() throws Exception {
		Joined rome = join("Rome");
		Joined carthage = join("Carthage");
		identify(rome, "Aster", COLOURS.get(0), EMBLEMS.get(0));
		identify(carthage, "Dido", COLOURS.get(1), EMBLEMS.get(1));
		ready(rome, true);
		ready(carthage, true);
		for (int i = 0; i < 4; i++) {
			updates.next();
		}
		assertThat(room().get("game")).isNotNull();

		identify(rome, "Aster II", COLOURS.get(3), EMBLEMS.get(3));

		assertThat(rome.rejections().next()).isEqualTo(
				Map.of("reason", "The Game has started, so your Ruler, colour and emblem can no longer change."));
		assertThat(room().get("players")).isEqualTo(
				List.of(player(rome, "Rome", "Aster", COLOURS.get(0), EMBLEMS.get(0), true),
						player(carthage, "Carthage", "Dido", COLOURS.get(1), EMBLEMS.get(1), true)));
	}

}
