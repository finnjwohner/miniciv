package com.miniciv.room;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

import com.miniciv.engine.Game;
import com.miniciv.engine.NationView;

/**
 * The place Players gather before a Game starts.
 */
public class Room {

	/**
	 * What every Player in the Room may see: who has joined, who is ready and
	 * who is done with the Turn, and once it has started, the Game's public
	 * facts. The version rises with every change, so a browser can ignore a View
	 * older than one it already has.
	 */
	public record View(String code, int version, List<PlayerView> players, GameView game) {
	}

	public record PlayerView(String id, String nationName, boolean ready, boolean done) {
	}

	/**
	 * @param millisLeft how long is left in the current Turn
	 */
	public record GameView(int turn, List<String> nations, long millisLeft) {
	}

	/**
	 * What a joining Player's browser keeps: its public id and secret token.
	 */
	public record Joined(String playerId, String token) {
	}

	private static final int MIN_PLAYERS = 2;

	private static final int MAX_PLAYERS = 4;

	private static final Duration TURN_LENGTH = Duration.ofMinutes(2);

	private static final SecureRandom SEEDS = new SecureRandom();

	private final String code;

	private int version;

	private final List<Player> players = new ArrayList<>();

	private final Clock clock;

	private Game game;

	private Instant turnEndsAt;

	Room(String code, Clock clock) {
		this.code = code;
		this.clock = clock;
	}

	public String code() {
		return code;
	}

	public synchronized Joined join(String nationName) {
		String name = nationName == null ? "" : nationName.strip();
		if (game != null) {
			throw new JoinRefused(JoinRefused.Reason.GAME_STARTED, "The Game in this Room has already started.");
		}
		if (players.size() == MAX_PLAYERS) {
			throw new JoinRefused(JoinRefused.Reason.ROOM_FULL, "This Room already has " + MAX_PLAYERS + " Players.");
		}
		if (name.isEmpty()) {
			throw new JoinRefused(JoinRefused.Reason.NO_NATION_NAME, "Choose a name for your Nation.");
		}
		if (players.stream().anyMatch(p -> p.nationName().equalsIgnoreCase(name))) {
			throw new JoinRefused(JoinRefused.Reason.NATION_NAME_TAKEN,
					"A Nation called " + name + " is already in this Room.");
		}
		Player player = new Player(name);
		players.add(player);
		version++;
		return new Joined(player.id(), player.token());
	}

	/**
	 * Marks a Player ready or unready; the Game starts as soon as 2–4 Players
	 * are all ready. Has no effect once the Game has started.
	 */
	public synchronized void ready(String token, boolean ready) {
		if (game != null) {
			return;
		}
		player(token).ready(ready);
		version++;
		if (players.size() >= MIN_PLAYERS && players.stream().allMatch(Player::ready)) {
			game = new Game(players.stream().map(Player::nationName).toList(), new Random(SEEDS.nextLong()));
			turnEndsAt = clock.instant().plus(TURN_LENGTH);
		}
	}

	/**
	 * Marks a Player done with the current Turn; the Turn ends as soon as every
	 * Player is done, or when its time runs out. A done sent for a Turn that has
	 * already ended is ignored, so it cannot carry over into the next Turn.
	 */
	public synchronized void done(String token, int turn) {
		Player player = player(token);
		if (game == null || game.turn() != turn || player.done()) {
			return;
		}
		player.done(true);
		version++;
		if (players.stream().allMatch(Player::done)) {
			endTurn();
		}
	}

	/**
	 * Whether the current Turn's time has run out.
	 */
	synchronized boolean turnIsDue() {
		return game != null && !clock.instant().isBefore(turnEndsAt);
	}

	synchronized void endTurnIfDue() {
		if (turnIsDue()) {
			endTurn();
		}
	}

	private void endTurn() {
		game.endTurn();
		players.forEach(p -> p.done(false));
		turnEndsAt = clock.instant().plus(TURN_LENGTH);
		version++;
	}

	private Optional<Player> playerWhere(Predicate<Player> condition) {
		return players.stream().filter(condition).findFirst();
	}

	private Player player(String token) {
		return playerWhere(p -> p.token().equals(token))
			.orElseThrow(() -> new IllegalArgumentException("No Player in Room " + code + " has that token"));
	}

	synchronized Optional<String> playerIdFor(String token) {
		return playerWhere(p -> p.token().equals(token)).map(Player::id);
	}

	/**
	 * The Turn the Game is on, or 0 before it starts.
	 */
	synchronized int turn() {
		return game == null ? 0 : game.turn();
	}

	/**
	 * What a Player may see of their own Nation, or null before the Game starts.
	 */
	synchronized NationView nationViewOf(String playerId) {
		if (game == null) {
			return null;
		}
		Player player = playerWhere(p -> p.id().equals(playerId)).orElseThrow(() -> new IllegalArgumentException("No Player in Room " + code + " has that id"));
		return game.viewOf(player.nationName());
	}

	/**
	 * What each Player may see of their own Nation, by Player id.
	 */
	synchronized Map<String, NationView> nationViews() {
		Map<String, NationView> views = new LinkedHashMap<>();
		for (Player player : players) {
			views.put(player.id(), game.viewOf(player.nationName()));
		}
		return views;
	}

	public synchronized View view() {
		List<PlayerView> playerViews = players.stream()
			.map(p -> new PlayerView(p.id(), p.nationName(), p.ready(), p.done()))
			.toList();
		GameView gameView = game == null ? null
				: new GameView(game.turn(), game.nations(),
						Math.max(0, Duration.between(clock.instant(), turnEndsAt).toMillis()));
		return new View(code, version, playerViews, gameView);
	}

}
