package com.miniciv;

import java.time.Duration;
import java.util.ArrayList;
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

import com.miniciv.room.Rooms;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rooms whose Players have all left being removed. Time only moves when a test
 * advances the clock, and the test then asks the server to do what its
 * scheduler would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EmptyRoomsTest {

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

	@AfterEach
	void disconnect() {
		connections.forEach(StompConnection::close);
	}

	RestClient rest() {
		return RestClient.create("http://localhost:" + port);
	}

	void createRoom() {
		code = (String) rest().post().uri("/api/rooms").retrieve().body(Map.class).get("code");
	}

	String join(String nationName) {
		return (String) rest().post()
			.uri("/api/rooms/{code}/players", code)
			.body(Map.of("nationName", nationName))
			.retrieve()
			.body(Map.class)
			.get("token");
	}

	StompConnection connect(String token) throws Exception {
		StompConnection stomp = new StompConnection(port, token);
		connections.add(stomp);
		return stomp;
	}

	/**
	 * The HTTP status and message a join to a Room is refused with.
	 */
	Map<String, Object> refusedJoin(String code) {
		return rest().post()
			.uri("/api/rooms/{code}/players", code)
			.body(Map.of("nationName", "Rome"))
			.exchange((request, response) -> Map.of("status", response.getStatusCode().value(), "detail",
					response.bodyTo(Map.class).get("detail")));
	}

	/**
	 * Waits until the server has seen whether any of the Room's Players is
	 * connected.
	 */
	void awaitEmpty(boolean empty) throws InterruptedException {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (rooms.find(code).orElseThrow().empty() != empty) {
			if (System.nanoTime() > deadline) {
				throw new AssertionError("The Room never became " + (empty ? "empty" : "occupied"));
			}
			Thread.sleep(10);
		}
	}

	@Test
	void aRoomWhoseOnlyPlayerDisconnectsIsGoneAfterSixtySecondsAndItsCodeIsUnknown() throws Exception {
		createRoom();
		StompConnection stomp = connect(join("Rome"));
		awaitEmpty(false);
		stomp.close();
		awaitEmpty(true);

		clock.advance(Duration.ofSeconds(59));
		rooms.removeEmptyRooms();
		assertThat(rooms.find(code)).isPresent();

		clock.advance(Duration.ofSeconds(1));
		rooms.removeEmptyRooms();

		assertThat(rooms.find(code)).isEmpty();
		assertThat(refusedJoin(code))
			.isEqualTo(Map.of("status", 404, "detail", "There is no Room with code " + code + "."));
	}

	@Test
	void aPlayerReconnectingAfterThirtySecondsKeepsTheRoom() throws Exception {
		createRoom();
		String token = join("Rome");
		StompConnection stomp = connect(token);
		awaitEmpty(false);
		stomp.close();
		awaitEmpty(true);

		clock.advance(Duration.ofSeconds(30));
		connect(token);
		awaitEmpty(false);
		clock.advance(Duration.ofMinutes(5));
		rooms.removeEmptyRooms();

		assertThat(rooms.find(code)).isPresent();
	}

	@Test
	void aGameInProgressWhosePlayersAreAllDisconnectedForSixtySecondsIsRemoved() throws Exception {
		createRoom();
		String rome = join("Rome");
		String carthage = join("Carthage");
		StompConnection romeStomp = connect(rome);
		StompConnection carthageStomp = connect(carthage);
		StompConnection.Messages romeGame = romeStomp.subscribe("/user/queue/game");
		TurnsTest.readyUp(romeStomp, code, rome, 0);
		TurnsTest.readyUp(carthageStomp, code, carthage, 1);
		assertThat(romeGame.next().get("turn")).isEqualTo(1);

		romeStomp.close();
		carthageStomp.close();
		awaitEmpty(true);
		clock.advance(Duration.ofSeconds(60));
		rooms.removeEmptyRooms();

		assertThat(rooms.find(code)).isEmpty();
		assertThat(refusedJoin(code))
			.isEqualTo(Map.of("status", 404, "detail", "There is no Room with code " + code + "."));
	}

	@Test
	void aPlayerJoiningAnEmptyRoomHasSixtySecondsToConnect() throws Exception {
		createRoom();
		clock.advance(Duration.ofSeconds(55));
		join("Rome");

		clock.advance(Duration.ofSeconds(59));
		rooms.removeEmptyRooms();
		assertThat(rooms.find(code)).isPresent();

		clock.advance(Duration.ofSeconds(1));
		rooms.removeEmptyRooms();
		assertThat(rooms.find(code)).isEmpty();
	}

}
