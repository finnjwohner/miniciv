package com.miniciv.room;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Every open Room on this server, held in memory. Every change to a Room is
 * broadcast to {@code /topic/rooms/{code}}.
 */
@Service
public class Rooms {

	private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private static final int CODE_LENGTH = 6;

	private final SecureRandom random = new SecureRandom();

	private final Map<String, Room> rooms = new ConcurrentHashMap<>();

	private final SimpMessagingTemplate messaging;

	Rooms(SimpMessagingTemplate messaging) {
		this.messaging = messaging;
	}

	public Room create() {
		while (true) {
			Room room = new Room(newCode());
			if (rooms.putIfAbsent(room.code(), room) == null) {
				return room;
			}
		}
	}

	public Optional<Room> find(String code) {
		return Optional.ofNullable(rooms.get(code));
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

	/**
	 * Applies a change and broadcasts the result while still holding the Room,
	 * so that broadcasts go out in the order the changes happened.
	 */
	private <T> T change(Room room, Function<Room, T> change) {
		synchronized (room) {
			T result = change.apply(room);
			messaging.convertAndSend("/topic/rooms/" + room.code(), room.view());
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
