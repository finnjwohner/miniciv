package com.miniciv.room;

import java.security.Principal;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.miniciv.engine.Action;
import com.miniciv.engine.ActionResult;
import com.miniciv.engine.NationView;
import com.miniciv.engine.Ruleset;

/**
 * Every open Room on this server, held in memory. Every change to a Room is
 * broadcast to {@code /topic/rooms/{code}}, and whenever a change reaches a
 * Player's Nation, such as the Game moving to a new Turn, the Player is sent
 * their Nation's view on {@code /user/queue/game}. A Player whose Action is
 * accepted is sent their changed view there too. Players' connections opening
 * and closing are followed, so the Game can wait for a Player who drops out.
 */
@Service
public class Rooms {

	private final SecureRandom random = new SecureRandom();

	private final Map<String, Room> rooms = new ConcurrentHashMap<>();

	private final SimpMessagingTemplate messaging;

	private final Clock clock;

	private final Ruleset ruleset;

	private final Supplier<RandomGenerator> luck;

	Rooms(SimpMessagingTemplate messaging, Clock clock, Ruleset ruleset, Supplier<RandomGenerator> luck) {
		this.messaging = messaging;
		this.clock = clock;
		this.ruleset = ruleset;
		this.luck = luck;
	}

	public Room create() {
		while (true) {
			Room room = new Room(newCode(), clock, ruleset, luck);
			if (rooms.putIfAbsent(RoomCodes.key(room.code()), room) == null) {
				return room;
			}
		}
	}

	public Optional<Room> find(String code) {
		return Optional.ofNullable(rooms.get(RoomCodes.key(code)));
	}

	/**
	 * The public id of the Player holding a token, in whichever Room.
	 */
	public Optional<String> playerIdFor(String token) {
		return rooms.values().stream().flatMap(room -> room.playerIdFor(token).stream()).findFirst();
	}

	/**
	 * Adds a Player to a Room, refused as for an unknown code if the Room is
	 * removed before the Player is added.
	 */
	public Room.Joined join(String code, String nationName) {
		Room room = find(code).orElseThrow(() -> noSuchRoom(code));
		return change(room, r -> {
			if (rooms.get(RoomCodes.key(code)) != r) {
				throw noSuchRoom(code);
			}
			return r.join(nationName);
		});
	}

	private static JoinRefused noSuchRoom(String code) {
		return new JoinRefused(JoinRefused.Reason.NO_SUCH_ROOM, "There is no Room with code " + code + ".");
	}

	public ActionResult ready(String code, String token, boolean ready) {
		return change(find(code).orElseThrow(), room -> room.ready(token, ready));
	}

	public ActionResult identify(String code, String token, String ruler, String colour, String emblem) {
		return change(find(code).orElseThrow(), room -> room.identify(token, ruler, colour, emblem));
	}

	public void done(String code, String token, int turn) {
		change(find(code).orElseThrow(), room -> {
			room.done(token, turn);
			return null;
		});
	}

	public ActionResult playAgain(String code, String token) {
		return change(find(code).orElseThrow(), room -> room.playAgain(token));
	}

	public void leave(String code, String token) {
		change(find(code).orElseThrow(), room -> {
			room.leave(token);
			return null;
		});
	}

	/**
	 * Applies a Player's Action, sending every Player whose Nation's view it
	 * changed their new view: the acting Player's, and another's when the
	 * Action reaches across, such as Tribute.
	 */
	public ActionResult act(String code, String token, Action action) {
		return change(find(code).orElseThrow(), room -> room.act(token, action));
	}

	/**
	 * Sends a private Message to another Player, delivering it on
	 * {@code /user/queue/messages} to both its sender and its recipient, while
	 * still holding the Room, so that Messages arrive in the order sent. A
	 * Message changes neither the Room's view nor any Nation's, so it does not
	 * go through {@link #change}.
	 */
	public ActionResult message(String code, String token, String to, String text) {
		Room room = find(code).orElseThrow();
		synchronized (room) {
			Room.Sending sending = room.message(token, to, text);
			sending.playerIds()
				.forEach(playerId -> messaging.convertAndSendToUser(playerId, "/queue/messages", sending.message()));
			return sending.result();
		}
	}

	/**
	 * Records a connection identified as a Player opening. Listens for the
	 * connection being acknowledged rather than requested, as only then has
	 * {@link PlayerIdentification} surely identified it.
	 */
	@EventListener
	void connected(SessionConnectedEvent event) {
		Principal player = event.getUser();
		if (player == null) {
			return;
		}
		String connection = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
		roomOfPlayer(player.getName()).ifPresent(room -> change(room, r -> {
			r.connected(player.getName(), connection);
			return null;
		}));
	}

	/**
	 * Records a Player's connection closing.
	 */
	@EventListener
	void disconnected(SessionDisconnectEvent event) {
		Principal player = event.getUser();
		if (player == null) {
			return;
		}
		roomOfPlayer(player.getName()).ifPresent(room -> change(room, r -> {
			r.disconnected(player.getName(), event.getSessionId());
			return null;
		}));
	}

	private Optional<Room> roomOfPlayer(String playerId) {
		return rooms.values().stream().filter(room -> room.hasPlayerWithId(playerId)).findFirst();
	}

	/**
	 * Removes the Nation of every Player who has been disconnected for too
	 * long.
	 */
	@Scheduled(fixedRate = 1000)
	public void removeLongDisconnected() {
		for (Room room : rooms.values()) {
			if (room.removalIsDue()) {
				change(room, r -> {
					r.removeLongDisconnected();
					return null;
				});
			}
		}
	}

	/**
	 * Removes every Room none of whose Players has been connected for too
	 * long, so its code no longer resolves.
	 */
	@Scheduled(fixedRate = 1000)
	public void removeEmptyRooms() {
		for (Room room : rooms.values()) {
			// Holds the Room so that no Player joins it while it is removed.
			synchronized (room) {
				if (room.abandoned()) {
					rooms.remove(RoomCodes.key(room.code()), room);
				}
			}
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
	 * Applies a change and, if it changed the Room, broadcasts the result, and
	 * sends each Player whose Nation's view it changed their new view, while
	 * still holding the Room, so that they go out in the order the changes
	 * happened.
	 */
	private <T> T change(Room room, Function<Room, T> change) {
		synchronized (room) {
			int version = room.version();
			Map<String, NationView> before = room.nationViews();
			T result = change.apply(room);
			Room.View view = room.view();
			if (view.version() != version) {
				messaging.convertAndSend("/topic/rooms/" + room.code(), view);
			}
			room.nationViews().forEach((playerId, nation) -> {
				if (!nation.equals(before.get(playerId))) {
					messaging.convertAndSendToUser(playerId, "/queue/game", nation);
				}
			});
			return result;
		}
	}

	private String newCode() {
		return RoomCodes.random(random);
	}

}
