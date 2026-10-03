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

	Game game = new Game(RULESET, List.of("Rome", "Carthage", "Egypt"), EVEN_LUCK);

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
		while (game.viewOf(nation).army().getOrDefault(unit, 0) < count) {
			game.endTurn();
		}
	}

	ActionResult send(String nation, String target, Map<String, Integer> units) {
		return game.act(nation, new Action.SendWarparty(target, units));
	}

	void play(RandomGenerator luck, String... nations) {
		game = new Game(RULESET, List.of(nations), luck);
	}

	@Test
	void scoutsAreTrainedLikeOtherUnitsAndJoinTheArmy() {
		game.act("Rome", new Action.TrainUnits("Scout", 2));
		assertThat(game.viewOf("Rome").gold()).isEqualTo(40);
		assertThat(game.viewOf("Rome").population()).isEqualTo(8);

		game.endTurn();

		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Scout", 2));
		assertThat(report("Rome")).contains("Scout ×2 finished training and joined your Army.");
	}

	@Test
	void scoutsStayAtHomeAndCannotBeSentInAWarparty() {
		armyOf("Rome", "Scout", 2);
		armyOf("Rome", "Warrior", 1);
		ActionResult kept = ActionResult.rejected("Scouts stay at home and cannot be sent in a Warparty.");

		assertThat(send("Rome", "Carthage", Map.of("Scout", 1))).isEqualTo(kept);
		assertThat(send("Rome", "Carthage", Map.of("Warrior", 1, "Scout", 2))).isEqualTo(kept);

		assertThat(game.viewOf("Rome").warparties()).isEmpty();
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Scout", 2), entry("Warrior", 1));
	}

	@Test
	void scoutsAtHomeTakeNoPartInABattleAndSurviveIt() {
		game.endTurn();
		armyOf("Rome", "Warrior", 4);
		armyOf("Carthage", "Warrior", 1);
		armyOf("Carthage", "Scout", 2);
		send("Rome", "Carthage", Map.of("Warrior", 4));
		for (int turn = 1; turn <= 5; turn++) {
			game.endTurn();
		}

		assertThat(report("Carthage")).contains(
				"Your Army of Warrior ×1 fought Rome's Warparty of Warrior ×4. You lost Warrior ×1 and Rome lost no Units.");
		assertThat(game.viewOf("Carthage").army()).containsExactly(entry("Scout", 2));
	}

	@Test
	void aNationWithOnlyScoutsAtHomeMeetsAWarpartyWithNoArmy() {
		armyOf("Rome", "Warrior", 2);
		armyOf("Carthage", "Scout", 1);
		send("Rome", "Carthage", Map.of("Warrior", 2));
		for (int turn = 1; turn <= 4; turn++) {
			game.endTurn();
		}

		assertThat(report("Carthage")).contains("Rome's Warparty of Warrior ×2 met no Army at your Nation.");
		assertThat(report("Rome")).contains("Your Warparty of Warrior ×2 met no Army at Carthage.");
		assertThat(game.viewOf("Carthage").army()).containsExactly(entry("Scout", 1));
	}

	@Test
	void scoutsCannotHoldOffConquest() {
		UnitType giant = new UnitType("Giant", 10, 1, 1, 1, 100, List.of());
		game = new Game(new Ruleset(List.of(giant, GameTest.SCOUT), List.of(), List.of()), List.of("Rome", "Carthage"),
				EVEN_LUCK);
		game.act("Rome", new Action.TrainUnits("Giant", 1));
		game.act("Carthage", new Action.TrainUnits("Scout", 1));
		game.endTurn();
		send("Rome", "Carthage", Map.of("Giant", 1));
		for (int turn = 1; turn <= 8; turn++) {
			game.endTurn();
		}

		assertThat(game.viewOf("Carthage").conqueredBy()).isEqualTo("Rome");
		assertThat(game.winner()).contains("Rome");
	}

	@Test
	void scoutsDiscoverAnotherNationsWarpartyAbroadNamingItsNationAndMakeup() {
		play(ALWAYS, "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Scout", 1);
		armyOf("Carthage", "Warrior", 2);

		send("Carthage", "Egypt", Map.of("Warrior", 2));
		game.endTurn();

		assertThat(report("Rome")).contains("Your Scouts discovered Carthage's Warparty of Warrior ×2 heading to Egypt.");
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Scouts"));
		assertThat(report("Egypt")).noneMatch(line -> line.contains("Scouts"));
	}

	@Test
	void scoutsTellWhereADiscoveredWarpartyIsHeading() {
		play(ALWAYS, "Rome", "Carthage", "Egypt");
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
				"Your Scouts discovered Carthage's Warparty of Warrior ×1 heading to your Nation.",
				"Your Scouts discovered Carthage's Warparty of Warrior ×1 heading to Egypt.",
				"Your Scouts discovered Egypt's Warparty of Warrior ×2 heading home from Carthage.");
	}

	@Test
	void scoutsTellOfAWarpartyWaitingAtAnotherNationButNotOfOneAlreadyArrivedAtTheirOwn() {
		play(ALWAYS, "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Scout", 1);
		armyOf("Carthage", "Warrior", 2);
		send("Carthage", "Rome", Map.of("Warrior", 1));
		send("Carthage", "Egypt", Map.of("Warrior", 1));
		game.endTurn();
		game.endTurn();

		game.endTurn();

		assertThat(report("Rome")).contains("A Warparty from Carthage arrived at your Nation; the Battle will be fought as this Turn ends.",
				"Your Scouts discovered Carthage's Warparty of Warrior ×1 waiting at Egypt for the Battle.");
		assertThat(report("Rome")).filteredOn(line -> line.startsWith("Your Scouts")).hasSize(1);
	}

	@Test
	void nothingIsDiscoveredWhenLuckIsAgainstTheScouts() {
		play(NEVER, "Rome", "Carthage");
		armyOf("Rome", "Scout", 5);
		armyOf("Carthage", "Warrior", 2);

		send("Carthage", "Rome", Map.of("Warrior", 2));
		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.startsWith("Your Scouts"));
	}

	@Test
	void aNationWithNoScoutsAtHomeDiscoversNothing() {
		play(ALWAYS, "Rome", "Carthage", "Egypt");
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
			play(luck(draw(0.3)), "Rome", "Carthage");
			armyOf("Rome", "Scout", scouts);
			armyOf("Carthage", "Warrior", 2);

			send("Carthage", "Rome", Map.of("Warrior", 2));
			game.endTurn();

			if (scouts == 1) {
				assertThat(report("Rome")).noneMatch(line -> line.startsWith("Your Scouts"));
			}
			else {
				assertThat(report("Rome")).contains("Your Scouts discovered Carthage's Warparty of Warrior ×2 heading to your Nation.");
			}
		}
	}

	@Test
	void eachWarpartyIsDiscoveredOrNotOnItsOwnDraw() {
		play(luck(draw(0.9), draw(0.1)), "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Scout", 1);
		armyOf("Carthage", "Warrior", 2);

		send("Carthage", "Rome", Map.of("Warrior", 1));
		send("Carthage", "Egypt", Map.of("Warrior", 1));
		game.endTurn();

		assertThat(report("Rome")).filteredOn(line -> line.startsWith("Your Scouts"))
			.containsExactly("Your Scouts discovered Carthage's Warparty of Warrior ×1 heading to Egypt.");
	}

	/**
	 * How many Warparties a Nation's Scouts discover over many Turns of Carthage
	 * sending Warparties to Persia, from a fixed seed.
	 */
	int discoveriesWith(int scouts) {
		play(new Random(17), "Rome", "Carthage", "Persia");
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
