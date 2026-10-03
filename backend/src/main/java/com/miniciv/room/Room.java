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
import java.util.function.Function;
import java.util.function.Predicate;

import com.miniciv.engine.Action;
import com.miniciv.engine.ActionResult;
import com.miniciv.engine.Game;
import com.miniciv.engine.NationView;
import com.miniciv.engine.Ruleset;

/**
 * The place Players gather before a Game starts.
 */
public class Room {

	/**
	 * What every Player in the Room may see: who has joined, who is ready and
	 * who is done with the Turn, and once it has started, the Game's public
	 * facts. The version rises with every change, so a browser can ignore a View
	 * older than one it already has.
	 *
	 * @param colours the colours a Player may pick for their Nation
	 * @param emblems the emblems a Player may pick for their Nation, as pixel
	 *        icon names
	 */
	public record View(String code, int version, List<String> colours, List<String> emblems,
			List<PlayerView> players, GameView game) {
	}

	/**
	 * @param ruler the name of the Nation's Ruler, empty until the Player names
	 *        them
	 */
	public record PlayerView(String id, String nationName, String ruler, String colour, String emblem, boolean ready,
			boolean done) {
	}

	/**
	 * @param millisLeft how long is left in the current Turn, 0 once the Game
	 *        is won; it does not run down while the Game is paused
	 * @param conquered the Nations conquered so far, in the order they were
	 *        conquered, whose Players are knocked out
	 * @param removed the Nations removed so far, in the order they were
	 *        removed, as their Players were disconnected for too long
	 * @param winner the Nation that won the Game, or null while it goes on
	 * @param waitingFor the Nations still in the Game whose Players are
	 *        disconnected, in the order they joined; the Game is paused while
	 *        there are any
	 */
	public record GameView(int turn, List<String> nations, long millisLeft, List<String> conquered,
			List<String> removed, String winner, List<Waiting> waitingFor) {
	}

	/**
	 * A Nation whose Player the Game is waiting for to reconnect.
	 *
	 * @param millisLeft how much longer the Player may stay disconnected
	 *        before their Nation is removed
	 */
	public record Waiting(String nation, long millisLeft) {
	}

	/**
	 * What a joining Player's browser keeps: its public id and secret token.
	 */
	public record Joined(String playerId, String token) {
	}

	/**
	 * The colours a Player may pick for their Nation, unique among the Players
	 * in a Room.
	 */
	static final List<String> COLOURS = List.of("#e2574c", "#e8913a", "#f2c14e", "#7bc96f", "#4fb8b0", "#5b8def",
			"#a678de", "#e07bb5");

	/**
	 * The emblems a Player may pick for their Nation, as pixel icon names,
	 * unique among the Players in a Room.
	 */
	static final List<String> EMBLEMS = List.of("crown", "moon", "sun", "heart", "star", "fire", "zap", "anchor",
			"tree", "fish", "feather", "key");

	private static final int MAX_RULER_LENGTH = 20;

	private static final int MIN_PLAYERS = 2;

	private static final int MAX_PLAYERS = 4;

	private static final Duration TURN_LENGTH = Duration.ofMinutes(2);

	/**
	 * How long in total a Player may be disconnected over a Game before their
	 * Nation is removed.
	 */
	private static final Duration DISCONNECTION_ALLOWANCE = Duration.ofMinutes(5);

	private static final SecureRandom SEEDS = new SecureRandom();

	private final String code;

	private int version;

	private final List<Player> players = new ArrayList<>();

	private final Clock clock;

	private final Ruleset ruleset;

	private Game game;

	private Instant turnEndsAt;

	/**
	 * When the Game was paused, or null while it is not.
	 */
	private Instant pausedSince;

	Room(String code, Clock clock, Ruleset ruleset) {
		this.code = code;
		this.clock = clock;
		this.ruleset = ruleset;
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
		Player player = new Player(name, firstFree(COLOURS, Player::colour), firstFree(EMBLEMS, Player::emblem));
		players.add(player);
		version++;
		return new Joined(player.id(), player.token());
	}

	/**
	 * The first of the choices no Player in the Room has.
	 */
	private String firstFree(List<String> choices, Function<Player, String> chosen) {
		return choices.stream()
			.filter(choice -> players.stream().noneMatch(p -> chosen.apply(p).equals(choice)))
			.findFirst()
			.orElseThrow();
	}

	/**
	 * Names a Player's Ruler and sets their Nation's colour and emblem, until
	 * the Game starts; the colour and emblem must be on offer and not another
	 * Player's. A ready Player stays ready.
	 */
	public synchronized ActionResult identify(String token, String ruler, String colour, String emblem) {
		Player player = player(token);
		String name = ruler == null ? "" : ruler.strip();
		if (game != null) {
			return ActionResult.rejected("The Game has started, so your Ruler, colour and emblem can no longer change.");
		}
		if (name.isEmpty()) {
			return ActionResult.rejected("Name your Ruler.");
		}
		if (name.length() > MAX_RULER_LENGTH) {
			return ActionResult.rejected("Your Ruler's name can be at most " + MAX_RULER_LENGTH + " characters.");
		}
		if (!COLOURS.contains(colour)) {
			return ActionResult.rejected("There is no colour " + colour + " to pick.");
		}
		if (!EMBLEMS.contains(emblem)) {
			return ActionResult.rejected("There is no emblem " + emblem + " to pick.");
		}
		if (players.stream().anyMatch(p -> p != player && p.colour().equals(colour))) {
			return ActionResult.rejected("Another Nation in this Room already has that colour.");
		}
		if (players.stream().anyMatch(p -> p != player && p.emblem().equals(emblem))) {
			return ActionResult.rejected("Another Nation in this Room already has that emblem.");
		}
		player.identify(name, colour, emblem);
		version++;
		return ActionResult.ACCEPTED;
	}

	/**
	 * Marks a Player ready or unready; the Game starts as soon as 2–4 Players
	 * are all ready. A Player must name their Ruler before readying up. Has no
	 * effect once the Game has started.
	 */
	public synchronized ActionResult ready(String token, boolean ready) {
		Player player = player(token);
		if (game != null) {
			return ActionResult.ACCEPTED;
		}
		if (ready && player.ruler().isEmpty()) {
			return ActionResult.rejected("Name your Ruler before you ready up.");
		}
		player.ready(ready);
		version++;
		if (players.size() >= MIN_PLAYERS && players.stream().allMatch(Player::ready)) {
			game = new Game(ruleset, players.stream().map(Player::nationName).toList(), new Random(SEEDS.nextLong()));
			turnEndsAt = clock.instant().plus(TURN_LENGTH);
			settle();
		}
		return ActionResult.ACCEPTED;
	}

	/**
	 * Marks a Player done with the current Turn; the Turn ends as soon as every
	 * Player still in the Game is done and it is not paused, or when its time
	 * runs out. A done sent for a Turn that has already ended is ignored, so it
	 * cannot carry over into the next Turn, and so is one from a Player whose
	 * Nation is out of the Game or once the Game is won.
	 */
	public synchronized void done(String token, int turn) {
		Player player = player(token);
		if (!playing() || game.turn() != turn || player.done() || !inGame(player)) {
			return;
		}
		player.done(true);
		version++;
		settle();
	}

	/**
	 * Whether the Game has started and is not yet won.
	 */
	private boolean playing() {
		return game != null && game.winner().isEmpty();
	}

	/**
	 * Whether a Player's Nation is still in the Game, neither conquered nor
	 * removed.
	 */
	private boolean inGame(Player player) {
		return game.inGame(player.nationName());
	}

	/**
	 * The Players still in the Game who are disconnected, in the order they
	 * joined; none before the Game starts or once it is won.
	 */
	private List<Player> waitingFor() {
		if (!playing()) {
			return List.of();
		}
		return players.stream().filter(p -> inGame(p) && !p.connected()).toList();
	}

	/**
	 * Brings the Game up to date with who is connected: counts the
	 * disconnection of every Player it is waiting for, pauses it while there are
	 * any, and once it is not paused ends the Turn if every Player still in the
	 * Game is done.
	 */
	private void settle() {
		Instant now = clock.instant();
		List<Player> waitingFor = waitingFor();
		players.forEach(p -> p.countDisconnection(waitingFor.contains(p), now));
		if (!waitingFor.isEmpty() && pausedSince == null) {
			pausedSince = now;
		}
		else if (waitingFor.isEmpty() && pausedSince != null) {
			turnEndsAt = turnEndsAt.plus(Duration.between(pausedSince, now));
			pausedSince = null;
		}
		if (playing() && pausedSince == null && players.stream().filter(this::inGame).allMatch(Player::done)) {
			endTurn();
		}
	}

	/**
	 * Records a connection of the Player with the given id opening.
	 */
	synchronized void connected(String playerId, String connection) {
		List<Player> waitingFor = waitingFor();
		playerWithId(playerId).connected(connection);
		presenceChanged(waitingFor);
	}

	/**
	 * Records a connection of the Player with the given id closing; the Game
	 * pauses if it was their last.
	 */
	synchronized void disconnected(String playerId, String connection) {
		List<Player> waitingFor = waitingFor();
		playerWithId(playerId).disconnected(connection);
		presenceChanged(waitingFor);
	}

	private void presenceChanged(List<Player> waitingForBefore) {
		if (!waitingFor().equals(waitingForBefore)) {
			version++;
			settle();
		}
	}

	/**
	 * The Players disconnected for longer in total than they may be.
	 */
	private List<Player> overdue() {
		Instant now = clock.instant();
		return waitingFor().stream()
			.filter(p -> p.disconnectedFor(now).compareTo(DISCONNECTION_ALLOWANCE) > 0)
			.toList();
	}

	/**
	 * Whether a Player has been disconnected for longer in total than they may
	 * be.
	 */
	synchronized boolean removalIsDue() {
		return !overdue().isEmpty();
	}

	/**
	 * Removes the Nation of every Player disconnected for longer in total than
	 * they may be; the Game goes on without them.
	 */
	synchronized void removeLongDisconnected() {
		List<Player> overdue = overdue();
		if (overdue.isEmpty()) {
			return;
		}
		overdue.forEach(p -> game.remove(p.nationName()));
		version++;
		settle();
	}

	/**
	 * Applies a Player's Action to their Nation at once, unless the Game is
	 * paused.
	 */
	public synchronized ActionResult act(String token, Action action) {
		Player player = player(token);
		if (game == null) {
			return ActionResult.rejected("The Game has not started yet.");
		}
		List<Player> waitingFor = waitingFor();
		if (!waitingFor.isEmpty() && inGame(player)) {
			return ActionResult.rejected("The Game is paused until "
					+ joined(waitingFor.stream().map(Player::nationName).toList())
					+ (waitingFor.size() == 1 ? " reconnects." : " reconnect."));
		}
		return game.act(player.nationName(), action);
	}

	/**
	 * Such as "Rome, Carthage and Egypt".
	 */
	private static String joined(List<String> items) {
		if (items.size() == 1) {
			return items.getFirst();
		}
		return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
	}

	/**
	 * Whether the current Turn's time has run out; never while the Game is
	 * paused or once it is won.
	 */
	synchronized boolean turnIsDue() {
		return playing() && pausedSince == null && !clock.instant().isBefore(turnEndsAt);
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

	synchronized boolean hasPlayerWithId(String playerId) {
		return playerWhere(p -> p.id().equals(playerId)).isPresent();
	}

	private Player playerWithId(String playerId) {
		return playerWhere(p -> p.id().equals(playerId))
			.orElseThrow(() -> new IllegalArgumentException("No Player in Room " + code + " has that id"));
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
		return game.viewOf(playerWithId(playerId).nationName());
	}

	/**
	 * What each Player may see of their own Nation, by Player id; none before
	 * the Game starts.
	 */
	synchronized Map<String, NationView> nationViews() {
		Map<String, NationView> views = new LinkedHashMap<>();
		if (game == null) {
			return views;
		}
		for (Player player : players) {
			views.put(player.id(), game.viewOf(player.nationName()));
		}
		return views;
	}

	synchronized int version() {
		return version;
	}

	public synchronized View view() {
		List<PlayerView> playerViews = players.stream()
			.map(p -> new PlayerView(p.id(), p.nationName(), p.ruler(), p.colour(), p.emblem(), p.ready(), p.done()))
			.toList();
		GameView gameView = null;
		if (game != null) {
			Instant now = clock.instant();
			Instant timerAt = pausedSince == null ? now : pausedSince;
			long millisLeft = game.winner().isPresent() ? 0
					: Math.max(0, Duration.between(timerAt, turnEndsAt).toMillis());
			List<Waiting> waiting = waitingFor().stream()
				.map(p -> new Waiting(p.nationName(),
						Math.max(0, DISCONNECTION_ALLOWANCE.minus(p.disconnectedFor(now)).toMillis())))
				.toList();
			gameView = new GameView(game.turn(), game.nations(), millisLeft, game.conquered(), game.removed(),
					game.winner().orElse(null), waiting);
		}
		return new View(code, version, COLOURS, EMBLEMS, playerViews, gameView);
	}

}
