package com.miniciv.room;

import java.util.ArrayList;
import java.util.List;

import com.miniciv.engine.Game;

/**
 * The place Players gather before a Game starts.
 */
public class Room {

	/**
	 * What every Player in the Room may see: who has joined, who is ready, and
	 * once it has started, the Game's public facts. The version rises with every
	 * change, so a browser can ignore a View older than one it already has.
	 */
	public record View(String code, int version, List<PlayerView> players, GameView game) {
	}

	public record PlayerView(String id, String nationName, boolean ready) {
	}

	public record GameView(int turn, List<String> nations) {
	}

	/**
	 * What a joining Player's browser keeps: its public id and secret token.
	 */
	public record Joined(String playerId, String token) {
	}

	private static final int MIN_PLAYERS = 2;

	private static final int MAX_PLAYERS = 4;

	private final String code;

	private int version;

	private final List<Player> players = new ArrayList<>();

	private Game game;

	Room(String code) {
		this.code = code;
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
		players.stream()
			.filter(p -> p.token().equals(token))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No Player in Room " + code + " has that token"))
			.ready(ready);
		version++;
		if (players.size() >= MIN_PLAYERS && players.stream().allMatch(Player::ready)) {
			game = new Game(players.stream().map(Player::nationName).toList());
		}
	}

	public synchronized View view() {
		return new View(code, version, players.stream().map(p -> new PlayerView(p.id(), p.nationName(), p.ready())).toList(),
				game == null ? null : new GameView(game.turn(), game.nations()));
	}

}
