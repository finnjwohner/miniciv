package com.miniciv;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import com.miniciv.engine.BuildingType;
import com.miniciv.engine.Ruleset;
import com.miniciv.engine.UnitType;
import com.miniciv.room.Rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Turns, and the actions Players take in them, as Players experience them over
 * STOMP. Time only moves when a test advances the clock, and the test then asks
 * the server to end any Turn that is due, as its scheduler would.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TurnsTest {

	@TestConfiguration
	static class Config {

		@Bean
		@Primary
		TestClock testClock() {
			return new TestClock();
		}

		@Bean
		@Primary
		Ruleset testRuleset() {
			return new Ruleset(
					List.of(new UnitType("Warrior", 20, 2, 1, 1, 3, List.of()),
							new UnitType("Catapult", 50, 1, 1, 1, 20, List.of()), PATHFINDER),
					List.of(new BuildingType("Farm", 30, 2, 1, 0, 0, 0, 0)), List.of());
		}

		@Bean
		@Primary
		Supplier<RandomGenerator> testLuck() {
			return TurnsTest::locatingLuck;
		}

	}

	/**
	 * A free Scout, costing no upkeep, for a Player to Locate another Nation
	 * with; see {@link #romeLocatesCarthage}.
	 */
	static final UnitType PATHFINDER = new UnitType("Pathfinder", 0, 0, 1, 0, 0, List.of(), true);

	/**
	 * Luck whose first draw, which the first Locate Task makes as no Event or
	 * Warparty draws before it, succeeds, and whose every later draw is even.
	 */
	static RandomGenerator locatingLuck() {
		int[] draws = { 0 };
		return () -> draws[0]++ == 0 ? 0L : Long.MIN_VALUE;
	}

	/**
	 * The Turn Report's income line for a Nation whose Population earned 10
	 * Gold, as its Player receives it: text with each amount marked.
	 */
	static final Map<String, Object> BASE_INCOME_REPORT = Map.of("parts",
			List.of(Map.of("text", "Your Population earned "), amount("GOLD", 10, "GAIN"),
					Map.of("text", ", and your Nation gained "), amount("POPULATION", 2, "GAIN"), Map.of("text", " and "),
					amount("RESEARCH", 3, "GAIN"), Map.of("text", ".")));

	/**
	 * An amount in a Turn Report line, as its Player receives it.
	 */
	static Map<String, Object> amount(String resource, int amount, String effect) {
		return Map.of("resource", resource, "amount", amount, "effect", effect);
	}

	@LocalServerPort
	int port;

	@Autowired
	TestClock clock;

	@Autowired
	Rooms rooms;

	RestClient rest() {
		return RestClient.create("http://localhost:" + port);
	}

	/**
	 * A Player in a started Game, connected with their token and following
	 * their private destination.
	 */
	record Connected(StompConnection stomp, StompConnection.Messages privately, String playerId, String token)
			implements AutoCloseable {

		@Override
		public void close() {
			stomp.close();
		}

	}

	String code;

	Connected rome;

	Connected carthage;

	/**
	 * A third Player, in the tests that need one.
	 */
	Connected egypt;

	/**
	 * Creates a Room with Rome and Carthage, connects both and readies them so
	 * that the Game starts.
	 */
	void startGame() throws Exception {
		joinRomeAndCarthage();
		readyBoth();
	}

	void joinRomeAndCarthage() throws Exception {
		code = (String) rest().post().uri("/api/rooms").retrieve().body(Map.class).get("code");
		rome = connect(join("Rome"));
		carthage = connect(join("Carthage"));
	}

	void readyBoth() {
		readyUp(rome.stomp(), code, rome.token(), 0);
		readyUp(carthage.stomp(), code, carthage.token(), 1);
	}

	/**
	 * Names a Player's Ruler, keeping the colour and emblem a Player joining
	 * the Room at the given seat, counting from 0, is given, and readies them
	 * up.
	 */
	static void readyUp(StompConnection stomp, String code, String token, int seat) {
		stomp.send("/app/rooms/" + code + "/identity", Map.of("token", token, "ruler", "Ruler " + (seat + 1), "colour",
				IdentityTest.COLOURS.get(seat), "emblem", IdentityTest.EMBLEMS.get(seat)));
		stomp.send("/app/rooms/" + code + "/ready", Map.of("token", token, "ready", true));
	}

	Map<?, ?> join(String nationName) {
		return rest().post().uri("/api/rooms/{code}/players", code).body(Map.of("nationName", nationName)).retrieve().body(Map.class);
	}

	Connected connect(Map<?, ?> joined) throws Exception {
		String token = (String) joined.get("token");
		StompConnection stomp = new StompConnection(port, token);
		return new Connected(stomp, stomp.subscribe("/user/queue/game"), (String) joined.get("playerId"), token);
	}

	void startBuilding(Connected player, String building) {
		player.stomp().send("/app/rooms/" + code + "/buildings", Map.of("token", player.token(), "building", building));
	}

	void repairBuilding(Connected player, String building) {
		player.stomp().send("/app/rooms/" + code + "/repairs", Map.of("token", player.token(), "building", building));
	}

	void rebuildBuilding(Connected player, String building) {
		player.stomp().send("/app/rooms/" + code + "/rebuilds", Map.of("token", player.token(), "building", building));
	}

	void sellBuilding(Connected player, String building) {
		player.stomp().send("/app/rooms/" + code + "/sales", Map.of("token", player.token(), "building", building));
	}

	void trainUnits(Connected player, String unit, int count) {
		player.stomp().send("/app/rooms/" + code + "/units", Map.of("token", player.token(), "unit", unit, "count", count));
	}

	void sendWarparty(Connected player, String target, Map<String, Integer> units) {
		player.stomp()
			.send("/app/rooms/" + code + "/warparties", Map.of("token", player.token(), "target", target, "units", units));
	}

	void setTask(Connected player, String kind, String target, int scouts) {
		player.stomp()
			.send("/app/rooms/" + code + "/tasks",
					Map.of("token", player.token(), "kind", kind, "target", target, "scouts", scouts));
	}

	/**
	 * Has Rome Locate Carthage as Turns 1 and 2 end, once every given Player
	 * has received their view of Turn 1: a Pathfinder trained on Turn 1 is set
	 * to Locate Carthage on Turn 2, and {@link #locatingLuck} has it succeed.
	 * Every given Player has then received their view of Turn 3.
	 */
	void romeLocatesCarthage(Connected... players) throws Exception {
		trainUnits(rome, "Pathfinder", 1);
		rome.privately().next();
		endTurn(1, players);
		setTask(rome, "LOCATE", "Carthage", 1);
		rome.privately().next();
		endTurn(2, players);
	}

	void recallWarparty(Connected player, int warparty) {
		player.stomp()
			.send("/app/rooms/" + code + "/recalls", Map.of("token", player.token(), "warparty", warparty));
	}

	void done(Connected player, int turn) {
		player.stomp().send("/app/rooms/" + code + "/done", Map.of("token", player.token(), "turn", turn));
	}

	Map<?, ?> currentGame() throws Exception {
		try (StompConnection stomp = new StompConnection(port)) {
			return (Map<?, ?>) stomp.subscribe("/app/rooms/" + code).next().get("game");
		}
	}

	/**
	 * A Nation on the Map as a Player receives it, with nothing learned of it,
	 * and the Ruler, colour and emblem {@link #readyUp} gives a Player at the
	 * given seat.
	 */
	static Map<String, Object> onMap(String nation, int seat, String standing) {
		Map<String, Object> facts = new HashMap<>();
		for (String fact : List.of("gold", "population", "research", "army", "buildings", "defense", "damaged")) {
			facts.put(fact, null);
		}
		return Map.of("name", nation, "ruler", "Ruler " + (seat + 1), "colour", IdentityTest.COLOURS.get(seat),
				"emblem", IdentityTest.EMBLEMS.get(seat), "standing", standing, "facts", facts);
	}

	/**
	 * A Nation's view as its Player receives it.
	 */
	static Map<String, Object> view(int version, int turn, String nation, int gold, int population, int research,
			List<Map<String, Object>> construction, Map<String, Object> turnReport) {
		return view(version, turn, nation, gold, population, research, construction, List.of(), turnReport);
	}

	static Map<String, Object> view(int version, int turn, String nation, int gold, int population, int research,
			List<Map<String, Object>> construction, List<Map<String, Object>> training,
			Map<String, Object> turnReport) {
		Map<String, Object> view = new HashMap<>();
		view.put("version", version);
		view.put("turn", turn);
		view.put("nation", nation);
		view.put("gold", gold);
		view.put("population", population);
		view.put("research", research);
		view.put("map", List.of(onMap("Rome", 0, "IN_GAME"), onMap("Carthage", 1, "IN_GAME")));
		view.put("availableBuildings", List.of(Map.of("name", "Farm", "gold", 30, "turns", 2, "populationPerTurn", 1,
				"goldPerTurn", 0, "goldPerPopulation", 0.0, "researchPerTurn", 0, "defense", 0)));
		view.put("buildings", Map.of());
		view.put("construction", construction);
		view.put("repairs", List.of());
		view.put("defense", 0);
		view.put("damage", List.of());
		view.put("sellValues", Map.of());
		view.put("availableUnits",
				List.of(Map.of("name", "Warrior", "gold", 20, "population", 2, "turns", 1, "upkeep", 1, "strength", 3.0,
						"counters", List.of(), "scout", false, "spy", false),
						Map.of("name", "Catapult", "gold", 50, "population", 1, "turns", 1, "upkeep", 1, "strength", 20.0,
								"counters", List.of(), "scout", false, "spy", false),
						Map.of("name", "Pathfinder", "gold", 0, "population", 0, "turns", 1, "upkeep", 0, "strength", 0.0,
								"counters", List.of(), "scout", true, "spy", false)));
		view.put("army", Map.of());
		view.put("scouts", 0);
		view.put("idleScouts", 0);
		view.put("tasks", List.of());
		view.put("located", List.of());
		view.put("training", training);
		view.put("warparties", List.of());
		view.put("spies", List.of());
		view.put("missions", List.of());
		view.put("travelBrackets",
				List.of(Map.of("strength", 0, "turns", 3), Map.of("strength", 10, "turns", 4),
						Map.of("strength", 25, "turns", 5), Map.of("strength", 50, "turns", 6),
						Map.of("strength", 100, "turns", 7)));
		view.put("spyTravelTurns", 3);
		view.put("upkeep", 0);
		// Only the Population earns Gold, and every Unit here costs 1 upkeep.
		int trainedNextTurn = training.stream()
			.filter(order -> order.get("readyOnTurn").equals(turn + 1))
			.mapToInt(order -> (int) order.get("count"))
			.sum();
		view.put("goldForecast", Map.of("income", population, "upkeep", trainedNextTurn, "lastingGold", 0));
		view.put("lastingEvent", null);
		view.put("arrivals", List.of());
		view.put("tributes", List.of());
		view.put("encounters", List.of());
		view.put("techTree", List.of());
		view.put("turnReport", turnReport);
		view.put("dilemmas", List.of());
		view.put("conqueredBy", null);
		view.put("winner", null);
		return view;
	}

	@AfterEach
	void disconnect() {
		rome.close();
		carthage.close();
		if (egypt != null) {
			egypt.close();
		}
	}

	/**
	 * Has every given Player mark themselves done with a Turn, and waits until
	 * each has received their view of the next one.
	 */
	void endTurn(int turn, Connected... players) throws Exception {
		for (Connected player : players) {
			done(player, turn);
		}
		for (Connected player : players) {
			assertThat(player.privately().next().get("turn")).isEqualTo(turn + 1);
		}
	}

	/**
	 * Has Rome train a Catapult on Turn 1 and, once it has Located Carthage,
	 * send it there on Turn 3; it conquers Carthage as Turn 7 ends, its trip
	 * taking 4 Turns.
	 */
	void romeConquersCarthage(Connected... players) throws Exception {
		trainUnits(rome, "Catapult", 1);
		rome.privately().next();
		romeLocatesCarthage(players);
		sendWarparty(rome, "Carthage", Map.of("Catapult", 1));
		rome.privately().next();
		for (int turn = 3; turn <= 7; turn++) {
			endTurn(turn, players);
		}
	}

	@Test
	void eachPlayerReceivesTheirOwnViewPrivatelyWhenTheGameStarts() throws Exception {
		startGame();

		assertThat(rome.privately().next()).isEqualTo(view(1, 1, "Rome", 50, 10, 0, List.of(), null));
		assertThat(carthage.privately().next()).isEqualTo(view(1, 1, "Carthage", 50, 10, 0, List.of(), null));
	}

	@Test
	void theTurnEndsEarlyOnceEveryPlayerIsDoneAndEachPlayerReceivesOnlyTheirOwnTurnReport() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();

		done(rome, 1);
		done(carthage, 1);

		Map<String, Object> report = Map.of("turn", 1, "netChange",
				Map.of("gold", 10, "population", 2, "research", 3), "sections",
				List.of(Map.of("kind", "RESOURCES", "lines", List.of(BASE_INCOME_REPORT))));
		assertThat(rome.privately().next()).isEqualTo(view(2, 2, "Rome", 60, 12, 3, List.of(), report));
		assertThat(carthage.privately().next()).isEqualTo(view(2, 2, "Carthage", 60, 12, 3, List.of(), report));
		rome.privately().expectNone();
		carthage.privately().expectNone();
	}

	@Test
	void theTurnEndsAfterTwoMinutesEvenIfNotEveryPlayerIsDone() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();
		done(rome, 1);

		clock.advance(Duration.ofSeconds(119));
		rooms.endDueTurns();
		assertThat(currentGame().get("turn")).isEqualTo(1);

		clock.advance(Duration.ofSeconds(1));
		rooms.endDueTurns();
		assertThat(rome.privately().next().get("turn")).isEqualTo(2);
		assertThat(carthage.privately().next().get("turn")).isEqualTo(2);
	}

	@Test
	void everyoneInTheRoomSeesHowLongIsLeftInTheTurn() throws Exception {
		startGame();
		rome.privately().next();
		assertThat(currentGame().get("millisLeft")).isEqualTo(120_000);

		clock.advance(Duration.ofSeconds(30));
		assertThat(currentGame().get("millisLeft")).isEqualTo(90_000);

		done(rome, 1);
		done(carthage, 1);
		rome.privately().next();
		Map<String, Object> game = new HashMap<>();
		game.put("turn", 2);
		game.put("nations", List.of("Rome", "Carthage"));
		game.put("millisLeft", 120_000);
		game.put("conquered", List.of());
		game.put("removed", List.of());
		game.put("winner", null);
		game.put("waitingFor", List.of());
		assertThat(currentGame()).isEqualTo(game);
	}

	@Test
	void aPlayerWhoReconnectsMidGameGetsTheirCurrentView() throws Exception {
		startGame();
		rome.privately().next();

		try (StompConnection reconnected = new StompConnection(port, rome.token())) {
			assertThat(reconnected.subscribe("/app/rooms/" + code + "/nation").next())
				.isEqualTo(view(1, 1, "Rome", 50, 10, 0, List.of(), null));
		}
	}

	@Test
	void everyoneInTheRoomSeesWhichPlayersAreDoneWithTheTurn() throws Exception {
		startGame();
		rome.privately().next();
		StompConnection.Messages updates = rome.stomp().subscribe("/topic/rooms/" + code);

		done(rome, 1);

		assertThat((List<?>) updates.next().get("players")).extracting("nationName", "done")
			.containsExactly(tuple("Rome", true), tuple("Carthage", false));
	}

	@Test
	void aPlayerCannotFollowAnotherPlayersPrivateDestination() throws Exception {
		joinRomeAndCarthage();
		StompConnection.Messages snooped = rome.stomp().subscribe("/user/" + carthage.playerId() + "/queue/game");
		readyBoth();

		assertThat(carthage.privately().next().get("nation")).isEqualTo("Carthage");
		assertThat(rome.privately().next().get("nation")).isEqualTo("Rome");
		snooped.expectNone();
	}

	@Test
	void aDoneSentForATurnThatHasAlreadyEndedIsIgnored() throws Exception {
		startGame();
		rome.privately().next();
		done(rome, 1);
		done(carthage, 1);
		assertThat(rome.privately().next().get("turn")).isEqualTo(2);

		done(rome, 1);

		assertThat((List<?>) rome.stomp().subscribe("/app/rooms/" + code).next().get("players")).extracting("done")
			.containsExactly(false, false);
	}

	@Test
	void aPlayerWhoStartsABuildingReceivesTheirUpdatedViewAtOnce() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();

		startBuilding(rome, "Farm");

		assertThat(rome.privately().next()).isEqualTo(view(2, 1, "Rome", 20, 10, 0,
				List.of(Map.of("building", "Farm", "turnsLeft", 2)), null));
		carthage.privately().expectNone();
	}

	@Test
	void aPlayerWhoseBuildingIsRejectedIsToldWhyPrivately() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();
		StompConnection.Messages romeRejections = rome.stomp().subscribe("/user/queue/rejections");
		StompConnection.Messages carthageRejections = carthage.stomp().subscribe("/user/queue/rejections");
		startBuilding(rome, "Farm");
		rome.privately().next();

		startBuilding(rome, "Farm");

		assertThat(romeRejections.next()).isEqualTo(Map.of("reason", "Farm costs 30 Gold, but you have only 20."));
		rome.privately().expectNone();
		carthageRejections.expectNone();
	}

	@Test
	void aPlayerWhoTrainsUnitsReceivesTheirUpdatedViewAtOnce() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();

		trainUnits(rome, "Warrior", 2);

		assertThat(rome.privately().next()).isEqualTo(view(2, 1, "Rome", 10, 6, 0, List.of(),
				List.of(Map.of("unit", "Warrior", "count", 2, "readyOnTurn", 2)), null));
		carthage.privately().expectNone();
	}

	@Test
	void aPlayerWhoseTrainingIsRejectedIsToldWhyPrivately() throws Exception {
		startGame();
		rome.privately().next();
		StompConnection.Messages romeRejections = rome.stomp().subscribe("/user/queue/rejections");

		trainUnits(rome, "Warrior", 3);

		assertThat(romeRejections.next()).isEqualTo(Map.of("reason", "Warrior ×3 costs 60 Gold, but you have only 50."));
		rome.privately().expectNone();
	}

	@Test
	void aPlayerWhoSendsAWarpartyReceivesTheirUpdatedViewAtOnceAndTheTargetIsNotTold() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();
		romeLocatesCarthage(rome, carthage);
		trainUnits(rome, "Warrior", 2);
		rome.privately().next();
		done(rome, 3);
		done(carthage, 3);
		assertThat(rome.privately().next().get("army")).isEqualTo(Map.of("Warrior", 2));
		carthage.privately().next();

		sendWarparty(rome, "Carthage", Map.of("Warrior", 2));

		Map<?, ?> view = rome.privately().next();
		assertThat(view.get("army")).isEqualTo(Map.of());
		assertThat(view.get("warparties")).isEqualTo(List.of(
				Map.of("id", 1, "target", "Carthage", "units", Map.of("Warrior", 2), "strength", 6.0, "homeward", false, "turnsLeft", 3,
						"turnsTravelled", 0, "plunder", Map.of("gold", 0, "research", 0), "strengthSent", 6.0)));
		carthage.privately().expectNone();
	}

	@Test
	void aPlayerWhoRecallsAWarpartyReceivesTheirUpdatedViewAtOnceOrIsToldWhyNot() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();
		StompConnection.Messages romeRejections = rome.stomp().subscribe("/user/queue/rejections");
		romeLocatesCarthage(rome, carthage);
		trainUnits(rome, "Warrior", 2);
		rome.privately().next();
		done(rome, 3);
		done(carthage, 3);
		rome.privately().next();
		carthage.privately().next();
		sendWarparty(rome, "Carthage", Map.of("Warrior", 2));
		rome.privately().next();

		recallWarparty(rome, 1);

		Map<?, ?> view = rome.privately().next();
		assertThat(view.get("warparties")).isEqualTo(List.of(
				Map.of("id", 1, "target", "Carthage", "units", Map.of("Warrior", 2), "strength", 6.0, "homeward", true, "turnsLeft", 1,
						"turnsTravelled", 0, "plunder", Map.of("gold", 0, "research", 0), "strengthSent", 6.0)));
		carthage.privately().expectNone();

		recallWarparty(rome, 1);

		assertThat(romeRejections.next()).isEqualTo(Map.of("reason", "That Warparty is already heading home."));
	}

	@Test
	void aPlayerWhoseWarpartyIsRejectedIsToldWhyPrivately() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();
		romeLocatesCarthage(rome, carthage);
		StompConnection.Messages romeRejections = rome.stomp().subscribe("/user/queue/rejections");

		sendWarparty(rome, "Carthage", Map.of("Warrior", 1));

		assertThat(romeRejections.next()).isEqualTo(Map.of("reason", "Your Army has no Warrior."));
		rome.privately().expectNone();
	}

	@Test
	void whenOneNationIsLeftTheGameEndsEveryoneSeesTheWinnerAndTurnsNoLongerEnd() throws Exception {
		startGame();
		rome.privately().next();
		carthage.privately().next();

		romeConquersCarthage(rome, carthage);

		Map<?, ?> game = currentGame();
		assertThat(game.get("conquered")).isEqualTo(List.of("Carthage"));
		assertThat(game.get("winner")).isEqualTo("Rome");
		assertThat(game.get("millisLeft")).isEqualTo(0);
		assertThat(carthage.stomp().subscribe("/app/rooms/" + code + "/nation").next())
			.extracting("conqueredBy", "winner")
			.containsExactly("Rome", "Rome");
		clock.advance(Duration.ofMinutes(2));
		rooms.endDueTurns();
		done(rome, 8);
		assertThat(currentGame().get("turn")).isEqualTo(8);
		rome.privately().expectNone();
	}

	@Test
	void aKnockedOutPlayerNeedNotBeDoneForTheTurnToEnd() throws Exception {
		joinRomeAndCarthage();
		egypt = connect(join("Egypt"));
		readyBoth();
		readyUp(egypt.stomp(), code, egypt.token(), 2);
		rome.privately().next();
		carthage.privately().next();
		egypt.privately().next();
		romeConquersCarthage(rome, carthage, egypt);
		assertThat(currentGame().get("winner")).isNull();

		endTurn(8, rome, egypt);

		assertThat(currentGame().get("turn")).isEqualTo(9);
	}

	@Test
	void aPlayerWhoseRepairOrRebuildIsRejectedIsToldWhyPrivately() throws Exception {
		startGame();
		rome.privately().next();
		StompConnection.Messages romeRejections = rome.stomp().subscribe("/user/queue/rejections");

		repairBuilding(rome, "Farm");
		rebuildBuilding(rome, "Farm");

		assertThat(romeRejections.next()).isEqualTo(Map.of("reason", "You have no damaged Farm to repair."));
		assertThat(romeRejections.next()).isEqualTo(Map.of("reason", "You have no destroyed Farm to rebuild."));
		rome.privately().expectNone();
	}

	@Test
	void aPlayerWhoseSaleIsRejectedIsToldWhyPrivately() throws Exception {
		startGame();
		rome.privately().next();
		StompConnection.Messages romeRejections = rome.stomp().subscribe("/user/queue/rejections");

		sellBuilding(rome, "Farm");

		assertThat(romeRejections.next()).isEqualTo(Map.of("reason", "You have no Farm to sell."));
		rome.privately().expectNone();
	}

}
