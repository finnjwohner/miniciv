package com.miniciv.room;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.miniciv.engine.Action;
import com.miniciv.engine.ActionResult;
import com.miniciv.engine.Ruleset;

/**
 * Every open Room on this server, held in memory. Every change to a Room is
 * broadcast to {@code /topic/rooms/{code}}, and whenever its Game moves to a
 * new Turn each Player is sent their own Nation's view on
 * {@code /user/queue/game}. A Player whose Action is accepted is sent their
 * changed view there too.
 */
@Service
public class Rooms {

	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private static final int CODE_LENGTH = 6;

	private final SecureRandom random = new SecureRandom();

	private final Map<String, Room> rooms = new ConcurrentHashMap<>();

	private final SimpMessagingTemplate messaging;

	private final Clock clock;

	private final Ruleset ruleset;

	Rooms(SimpMessagingTemplate messaging, Clock clock, Ruleset ruleset) {
		this.messaging = messaging;
		this.clock = clock;
		this.ruleset = ruleset;
	}

	public Room create() {
		while (true) {
			Room room = new Room(newCode(), clock, ruleset);
			if (rooms.putIfAbsent(room.code(), room) == null) {
				return room;
			}
		}
	}

	public Optional<Room> find(String code) {
		return Optional.ofNullable(rooms.get(code));
	}

	/**
	 * The public id of the Player holding a token, in whichever Room.
	 */
	public Optional<String> playerIdFor(String token) {
		return rooms.values().stream().flatMap(room -> room.playerIdFor(token).stream()).findFirst();
	}

	public Room.Joined join(String code, String nationName) {
		Room room = find(code).orElseThrow(
				() -> new JoinRefused(JoinRefused.Reason.NO_SUCH_ROOM, "There is no Room with code " + code + "."));
		return change(room, r -> r.join(nationName));
	}

	public void ready(String code, String token, boolean ready) {
		change(find(code).orElseThrow(), room -> {
			room.ready(token, ready);
			return null;
		});
	}

	public void done(String code, String token, int turn) {
		change(find(code).orElseThrow(), room -> {
			room.done(token, turn);
			return null;
		});
	}

	public ActionResult act(String code, String token, Action action) {
		Room room = find(code).orElseThrow();
		synchronized (room) {
			ActionResult result = room.act(token, action);
			if (result.accepted()) {
				String playerId = room.playerIdFor(token).orElseThrow();
				messaging.convertAndSendToUser(playerId, "/queue/game", room.nationViewOf(playerId));
			}
			return result;
		}
	}

	/**
	 * Ends every Turn whose time has run out.
	 */
	@Scheduled(fixedRate = 1000)
	public void endDueTurns() {
		for (Room room : rooms.values()) {
			if (room.turnIsDue()) {
				change(room, r -> {
					r.endTurnIfDue();
					return null;
				});
			}
		}
	}

	/**
	 * Applies a change and broadcasts the result while still holding the Room,
	 * so that broadcasts go out in the order the changes happened.
	 */
	private <T> T change(Room room, Function<Room, T> change) {
		synchronized (room) {
			int turn = room.turn();
			T result = change.apply(room);
			messaging.convertAndSend("/topic/rooms/" + room.code(), room.view());
			if (room.turn() != turn) {
				room.nationViews().forEach((playerId, view) -> messaging.convertAndSendToUser(playerId, "/queue/game", view));
			}
			return result;
		}
	}

	private String newCode() {
		StringBuilder code = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			code.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
		}
		return code.toString();
	}

}
