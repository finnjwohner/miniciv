package com.miniciv.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.RULESET;
import static com.miniciv.engine.GameTest.luck;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class ScoutsTest {

	/**
	 * Luck that makes every Scout discover every Warparty it may.
	 */
	static final RandomGenerator ALWAYS = luck(0);

	/**
	 * Luck that makes no Scout discover anything.
	 */
	static final RandomGenerator NEVER = luck(-1);

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage", "Egypt"), EVEN_LUCK);

	/**
	 * A draw of the given double, between 0 and 1, from {@link GameTest#luck}.
	 */
	static long draw(double value) {
		return (long) (value * 0x1.0p53) << 11;
	}

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	void armyOf(String nation, String unit, int count) {
		assertThat(game.act(nation, new Action.TrainUnits(unit, count))).isEqualTo(ActionResult.ACCEPTED);
		while (!game.viewOf(nation).training().isEmpty()) {
			game.endTurn();
		}
	}

	ActionResult send(String nation, String target, Map<String, Integer> units) {
		return game.act(nation, new Action.SendWarparty(target, units));
	}

	void play(RandomGenerator luck, String... nations) {
		game = new Game(RULESET, Identities.of(nations), luck);
	}

	/**
	 * Plays with the given luck once one Nation has Located every other; see
	 * {@link Locating#by}.
	 */
	void playLocatedBy(String nation, RandomGenerator luck, String... nations) {
		game = Locating.by(nation, RULESET, Identities.of(nations), luck);
	}

	@Test
	void scoutsAreTrainedLikeOtherUnitsAndSetOutIntoTheWildsNotTheArmy() {
		armyOf("Rome", "Warrior", 1);
		game.act("Rome", new Action.TrainUnits("Scout", 2));

		game.endTurn();

		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Warrior", 1));
		assertThat(game.viewOf("Rome").scouts()).isEqualTo(2);
		assertThat(report("Rome")).contains("2 Scouts set out into the wilds.")
			.noneMatch(line -> line.contains("finished training"));
	}

	@Test
	void oneScoutSetsOutIntoTheWildsAlongsideOtherUnitsFinishingTraining() {
		game.act("Rome", new Action.TrainUnits("Scout", 1));
		game.act("Rome", new Action.TrainUnits("Warrior", 1));

		game.endTurn();

		assertThat(report("Rome")).contains("1 Scout set out into the wilds.",
				"1 Warrior finished training and joined your Army.");
	}

	@Test
	void scoutsKeepPayingUpkeep() {
		armyOf("Rome", "Scout", 3);

		game.endTurn();

		assertThat(game.viewOf("Rome").upkeep()).isEqualTo(3);
		assertThat(report("Rome")).contains("Your Units' upkeep cost 3 Gold.");
	}

	@Test
	void scoutsRoamTheWildsAndCannotBeSentInAWarparty() {
		playLocatedBy("Rome", EVEN_LUCK, "Rome", "Carthage", "Egypt");
		int pathfinders = game.viewOf("Rome").scouts();
		armyOf("Rome", "Scout", 2);
		armyOf("Rome", "Warrior", 1);
		ActionResult kept = ActionResult.rejected("Scouts roam the wilds and cannot be sent in a Warparty.");

		assertThat(send("Rome", "Carthage", Map.of("Scout", 1))).isEqualTo(kept);
		assertThat(send("Rome", "Carthage", Map.of("Warrior", 1, "Scout", 2))).isEqualTo(kept);

		assertThat(game.viewOf("Rome").warparties()).isEmpty();
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Warrior", 1));
		assertThat(game.viewOf("Rome").scouts()).isEqualTo(pathfinders + 2);
	}

	@Test
	void scoutsTakeNoPartInABattleAndSurviveIt() {
		playLocatedBy("Rome", EVEN_LUCK, "Rome", "Carthage", "Egypt");
		game.endTurn();
		armyOf("Rome", "Warrior", 1);
		armyOf("Carthage", "Warrior", 3);
		armyOf("Carthage", "Scout", 2);
		send("Rome", "Carthage", Map.of("Warrior", 1));
		for (int turn = 1; turn <= 5 && report("Carthage").stream().noneMatch(line -> line.startsWith("Your Army")); turn++) {
			game.endTurn();
		}

		assertThat(report("Carthage")).anyMatch(line -> line.startsWith("Your Army of Warrior ×3 fought Caesar of Rome's"))
			.noneMatch(line -> line.contains("Scout"));
		assertThat(game.viewOf("Carthage").conqueredBy()).isNull();
		assertThat(game.viewOf("Carthage").scouts()).isEqualTo(2);
	}

	@Test
	void aNationWithOnlyScoutsMeetsAWarpartyWithNoArmy() {
		playLocatedBy("Rome", EVEN_LUCK, "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Warrior", 2);
		armyOf("Carthage", "Scout", 1);
		send("Rome", "Carthage", Map.of("Warrior", 2));
		for (int turn = 1; turn <= 4; turn++) {
			game.endTurn();
		}

		assertThat(report("Carthage")).contains("Caesar of Rome's Warparty of Warrior ×2 met no Army at your Nation.");
		assertThat(report("Rome")).contains("Your Warparty of Warrior ×2 met no Army at Carthage.");
		assertThat(game.viewOf("Carthage").army()).isEmpty();
		assertThat(game.viewOf("Carthage").scouts()).isEqualTo(1);
	}

	@Test
	void aNationWithOnlyScoutsLeftAtNoPopulationIsConqueredAndLosesThem() {
		UnitType giant = new UnitType("Giant", 10, 1, 1, 1, 100, List.of());
		game = Locating.by("Rome", new Ruleset(List.of(giant, GameTest.SCOUT), List.of(), List.of()), Identities.of("Rome", "Carthage"),
				EVEN_LUCK);
		game.act("Rome", new Action.TrainUnits("Giant", 1));
		game.act("Carthage", new Action.TrainUnits("Scout", 1));
		game.endTurn();
		send("Rome", "Carthage", Map.of("Giant", 1));
		for (int turn = 1; turn <= 8; turn++) {
			game.endTurn();
		}

		assertThat(game.viewOf("Carthage").conqueredBy()).isEqualTo("Rome");
		assertThat(game.viewOf("Carthage").scouts()).isZero();
		assertThat(game.winner()).contains("Rome");
	}

	@Test
	void scoutsDesertWhenTheirUpkeepCannotBePaid() {
		UnitType costly = new UnitType("Scout", 5, 1, 1, 100, 0, List.of(), true);
		game = new Game(new Ruleset(List.of(costly), List.of(), List.of()), Identities.of("Rome", "Carthage"), EVEN_LUCK);
		game.act("Rome", new Action.TrainUnits("Scout", 2));

		game.endTurn();

		assertThat(game.viewOf("Rome").scouts()).isZero();
		assertThat(report("Rome")).contains("You could not pay your Units' upkeep, so 2 Scouts roaming the wilds deserted.");
	}

	@Test
	void aRemovedNationLosesItsScouts() {
		armyOf("Rome", "Scout", 2);

		game.remove("Rome");

		assertThat(game.viewOf("Rome").scouts()).isZero();
		assertThat(game.viewOf("Rome").upkeep()).isZero();
	}

	@Test
	void scoutsDiscoverAnotherNationsWarpartyAbroadNamingItsNationAndMakeup() {
		playLocatedBy("Carthage", ALWAYS, "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Scout", 1);
		armyOf("Carthage", "Warrior", 2);

		send("Carthage", "Egypt", Map.of("Warrior", 2));
		game.endTurn();

		assertThat(report("Rome")).contains("Your Scouts discovered Hannibal of Carthage's Warparty of Warrior ×2 heading to Egypt.");
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Scouts"));
		assertThat(report("Egypt")).noneMatch(line -> line.contains("Scouts"));
	}

	@Test
	void scoutsTellWhereADiscoveredWarpartyIsHeading() {
		game = Locating.in(RULESET, Identities.of("Rome", "Carthage", "Egypt"), ALWAYS)
			.locate("Carthage", "Rome", "Egypt")
			.locate("Egypt", "Carthage")
			.game();
		armyOf("Rome", "Scout", 1);
		armyOf("Carthage", "Warrior", 2);
		armyOf("Egypt", "Warrior", 2);

		send("Egypt", "Carthage", Map.of("Warrior", 2));
		game.endTurn();
		game.endTurn();
		send("Carthage", "Rome", Map.of("Warrior", 1));
		send("Carthage", "Egypt", Map.of("Warrior", 1));
		game.act("Egypt", new Action.RecallWarparty(1));
		game.endTurn();

		assertThat(report("Rome")).containsSubsequence(
				"Your Scouts discovered Hannibal of Carthage's Warparty of Warrior ×1 heading to your Nation.",
				"Your Scouts discovered Hannibal of Carthage's Warparty of Warrior ×1 heading to Egypt.",
				"Your Scouts discovered Cleopatra of Egypt's Warparty of Warrior ×2 heading home from Carthage.");
	}

	@Test
	void scoutsTellOfAWarpartyWaitingAtAnotherNationButNotOfOneAlreadyArrivedAtTheirOwn() {
		playLocatedBy("Carthage", ALWAYS, "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Scout", 1);
		armyOf("Carthage", "Warrior", 2);
		send("Carthage", "Rome", Map.of("Warrior", 1));
		send("Carthage", "Egypt", Map.of("Warrior", 1));
		game.endTurn();
		game.endTurn();

		game.endTurn();

		assertThat(report("Rome")).contains("Hannibal of Carthage's Warparty arrived at your Nation; the Battle will be fought as this Turn ends.",
				"Your Scouts discovered Hannibal of Carthage's Warparty of Warrior ×1 waiting at Egypt for the Battle.");
		assertThat(report("Rome")).filteredOn(line -> line.startsWith("Your Scouts")).hasSize(1);
	}

	@Test
	void nothingIsDiscoveredWhenLuckIsAgainstTheScouts() {
		playLocatedBy("Carthage", NEVER, "Rome", "Carthage");
		armyOf("Rome", "Scout", 5);
		armyOf("Carthage", "Warrior", 2);

		send("Carthage", "Rome", Map.of("Warrior", 2));
		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.startsWith("Your Scouts"));
	}

	@Test
	void aNationWithNoScoutsDiscoversNothing() {
		playLocatedBy("Carthage", ALWAYS, "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Warrior", 1);
		armyOf("Carthage", "Warrior", 2);

		send("Carthage", "Egypt", Map.of("Warrior", 2));
		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.startsWith("Your Scouts"));
	}

	@Test
	void eachScoutRaisesTheChanceOfDiscoveringAWarparty() {
		// A fifth of a chance for one Scout, and 1 - 0.8 × 0.8 = 0.36 for two.
		for (int scouts = 1; scouts <= 2; scouts++) {
			playLocatedBy("Carthage", luck(draw(0.3)), "Rome", "Carthage");
			armyOf("Rome", "Scout", scouts);
			armyOf("Carthage", "Warrior", 2);

			send("Carthage", "Rome", Map.of("Warrior", 2));
			game.endTurn();

			if (scouts == 1) {
				assertThat(report("Rome")).noneMatch(line -> line.startsWith("Your Scouts"));
			}
			else {
				assertThat(report("Rome")).contains("Your Scouts discovered Hannibal of Carthage's Warparty of Warrior ×2 heading to your Nation.");
			}
		}
	}

	@Test
	void eachWarpartyIsDiscoveredOrNotOnItsOwnDraw() {
		playLocatedBy("Carthage", luck(draw(0.9), draw(0.1)), "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Scout", 1);
		armyOf("Carthage", "Warrior", 2);

		send("Carthage", "Rome", Map.of("Warrior", 1));
		send("Carthage", "Egypt", Map.of("Warrior", 1));
		game.endTurn();

		assertThat(report("Rome")).filteredOn(line -> line.startsWith("Your Scouts"))
			.containsExactly("Your Scouts discovered Hannibal of Carthage's Warparty of Warrior ×1 heading to Egypt.");
	}

	/**
	 * How many Warparties a Nation's Scouts discover over many Turns of Carthage
	 * sending Warparties to Persia, from a fixed seed.
	 */
	int discoveriesWith(int scouts) {
		playLocatedBy("Carthage", new Random(17), "Rome", "Carthage", "Persia");
		game.endTurn();
		armyOf("Rome", "Scout", scouts);
		int discoveries = 0;
		for (int turn = 1; turn <= 30; turn++) {
			if (game.viewOf("Carthage").army().containsKey("Warrior")) {
				send("Carthage", "Persia", Map.of("Warrior", 1));
			}
			else if (game.viewOf("Carthage").gold() >= 10) {
				game.act("Carthage", new Action.TrainUnits("Warrior", 1));
			}
			game.endTurn();
			discoveries += (int) report("Rome").stream().filter(line -> line.startsWith("Your Scouts")).count();
		}
		return discoveries;
	}

	@Test
	void moreScoutsDiscoverMoreWarpartiesFromAFixedSeed() {
		List<Integer> discoveries = new ArrayList<>();
		for (int scouts : new int[] { 1, 6 }) {
			discoveries.add(discoveriesWith(scouts));
		}

		assertThat(discoveries.get(0)).isPositive();
		assertThat(discoveries.get(1)).isGreaterThan(discoveries.get(0) * 2);
	}

}
