package com.miniciv.engine;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.ARCHER;
import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.FARM;
import static com.miniciv.engine.GameTest.HORSEMAN;
import static com.miniciv.engine.GameTest.SCOUT;
import static com.miniciv.engine.GameTest.WALLS;
import static com.miniciv.engine.GameTest.WARRIOR;
import static com.miniciv.engine.GameTest.luck;
import static com.miniciv.engine.ScoutsTest.draw;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class SpiesTest {

	static final UnitType SPY = new UnitType("Spy", 10, 1, 1, 1, 0.1, List.of(), false, true);

	static final Ruleset RULESET = new Ruleset(List.of(WARRIOR, ARCHER, HORSEMAN, SCOUT, SPY), List.of(FARM, WALLS),
			List.of());

	/**
	 * Luck that makes every Spy in place report, always its target's Gold.
	 */
	static final RandomGenerator ALWAYS_GOLD = luck(0);

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage", "Egypt"), EVEN_LUCK);

	void play(RandomGenerator luck, String... nations) {
		game = new Game(RULESET, Identities.of(nations), luck);
	}

	/**
	 * Luck that makes every Spy in place report, always the fact the given
	 * draw picks: Gold below 2/9, then Population, Research and Army a further
	 * 2/9 each, and Buildings in the last ninth.
	 */
	static RandomGenerator reporting(double fact) {
		return luck(draw(0), draw(fact));
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

	ActionResult sendSpy(String nation, String target) {
		return game.act(nation, new Action.SendSpy(target));
	}

	/**
	 * Sends a Spy from Rome, which must have one at home, to Carthage and ends
	 * Turns until it has arrived.
	 */
	void spyArrivesAtCarthage() {
		assertThat(sendSpy("Rome", "Carthage")).isEqualTo(ActionResult.ACCEPTED);
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}
	}

	MapNation onMapOf(String nation, String other) {
		return game.viewOf(nation).map().stream().filter(entry -> entry.name().equals(other)).findFirst().orElseThrow();
	}

	@Test
	void spiesAreTrainedLikeOtherUnitsAndJoinTheArmy() {
		game.act("Rome", new Action.TrainUnits("Spy", 1));
		game.endTurn();

		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Spy", 1));
		assertThat(report("Rome")).contains("Spy ×1 finished training and joined your Army.");
	}

	@Test
	void spiesAreSentOnTheirOwnAndNotInAWarparty() {
		armyOf("Rome", "Spy", 1);
		armyOf("Rome", "Warrior", 1);

		assertThat(game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 1, "Spy", 1))))
			.isEqualTo(ActionResult.rejected("Spies are sent on their own, not in a Warparty."));
		assertThat(game.viewOf("Rome").warparties()).isEmpty();
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Spy", 1), entry("Warrior", 1));
	}

	@Test
	void aSpySentLeavesTheArmyAndTravelsForTheBaseTravelTime() {
		armyOf("Rome", "Spy", 2);

		assertThat(sendSpy("Rome", "Carthage")).isEqualTo(ActionResult.ACCEPTED);

		NationView rome = game.viewOf("Rome");
		assertThat(rome.army()).containsExactly(entry("Spy", 1));
		assertThat(rome.spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 3, 0));
		game.endTurn();
		game.endTurn();
		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 1, 2));
		assertThat(report("Rome")).noneMatch(line -> line.contains("Spy"));

		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 0, 3));
		assertThat(report("Rome")).contains("Your Spy arrived at Carthage.");
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Spy") || line.contains("Rome"));
	}

	@Test
	void aSpyStaysInPlaceOnceArrived() {
		armyOf("Rome", "Spy", 1);
		spyArrivesAtCarthage();

		for (int turn = 1; turn <= 5; turn++) {
			game.endTurn();
		}

		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 0, 3));
		assertThat(game.viewOf("Rome").army()).isEmpty();
	}

	@Test
	void aSpyAbroadCostsItsUpkeep() {
		armyOf("Rome", "Spy", 1);
		sendSpy("Rome", "Carthage");

		assertThat(game.viewOf("Rome").upkeep()).isEqualTo(1);
		game.endTurn();
		assertThat(report("Rome")).contains("Your Units' upkeep cost 1 Gold.");
	}

	@Test
	void aSpyAbroadDesertsWhenItsUpkeepCannotBePaid() {
		UnitType costly = new UnitType("Spy", 0, 1, 1, 40, 0, List.of(), false, true);
		game = new Game(new Ruleset(List.of(costly), List.of(FARM), List.of()), Identities.of("Rome", "Carthage"), EVEN_LUCK);
		game.act("Rome", new Action.TrainUnits("Spy", 1));
		game.endTurn();
		sendSpy("Rome", "Carthage");
		// Next Turn's Gold is what Rome has plus what its Population earns.
		NationView rome = game.viewOf("Rome");
		assertThat(rome.gold() + rome.population()).isLessThan(40);

		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(report("Rome")).contains("You could not pay your Units' upkeep, so your Spy sent to Carthage deserted.");
	}

	@Test
	void aSpyIsSentOnlyToAnotherNationInTheGameFromTheArmy() {
		assertThat(sendSpy("Rome", "Carthage")).isEqualTo(ActionResult.rejected("Your Army has no Spy."));
		armyOf("Rome", "Spy", 1);

		assertThat(sendSpy("Rome", "Rome")).isEqualTo(ActionResult.rejected("A Spy must be sent to another Nation."));
		assertThat(sendSpy("Rome", "Atlantis")).isEqualTo(ActionResult.rejected("There is no Nation called Atlantis."));
		game.remove("Egypt");
		assertThat(sendSpy("Rome", "Egypt")).isEqualTo(ActionResult.rejected("Egypt has been removed from the Game."));
		assertThat(game.viewOf("Rome").spies()).isEmpty();
	}

	@Test
	void aSpyInPlaceReportsItsTargetsGoldAndTheMapShowsItWithTheTurnLearned() {
		play(ALWAYS_GOLD, "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Spy", 1);

		spyArrivesAtCarthage();

		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage has 102 Gold.");
		assertThat(onMapOf("Rome", "Carthage").facts())
			.isEqualTo(new Facts(new Fact<>(102, 4), null, null, null, null, null));
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Spy"));
	}

	@Test
	void aSpyMayReportItsTargetsPopulation() {
		play(reporting(0.3), "Rome", "Carthage");
		armyOf("Rome", "Spy", 1);

		spyArrivesAtCarthage();

		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage has 18 Population.");
		assertThat(onMapOf("Rome", "Carthage").facts().population()).isEqualTo(new Fact<>(18, 4));
	}

	@Test
	void aSpyMayReportItsTargetsResearch() {
		play(reporting(0.5), "Rome", "Carthage");
		armyOf("Rome", "Spy", 1);

		spyArrivesAtCarthage();

		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage has 12 Research.");
		assertThat(onMapOf("Rome", "Carthage").facts().research()).isEqualTo(new Fact<>(12, 4));
	}

	@Test
	void aSpyMayReportItsTargetsArmyWhichNeverIncludesItsScouts() {
		// Carthage's Scout does not catch the Spy on its way.
		GameTest.Rigged luck = new GameTest.Rigged();
		play(luck, "Rome", "Carthage");
		armyOf("Rome", "Spy", 1);
		armyOf("Carthage", "Warrior", 2);
		armyOf("Carthage", "Scout", 1);
		sendSpy("Rome", "Carthage");
		game.endTurn();
		game.endTurn();
		luck.then(0, 0.8);

		game.endTurn();

		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage's Army is Warrior ×2.");
		assertThat(onMapOf("Rome", "Carthage").facts().army()).isEqualTo(new Fact<>(Map.of("Warrior", 2), 6));
	}

	@Test
	void aSpyMayReportThatItsTargetHasNoArmy() {
		play(reporting(0.8), "Rome", "Carthage");
		armyOf("Rome", "Spy", 1);

		spyArrivesAtCarthage();

		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage has no Army.");
		assertThat(onMapOf("Rome", "Carthage").facts().army()).isEqualTo(new Fact<>(Map.of(), 4));
	}

	@Test
	void aSpyOccasionallyReportsItsTargetsBuildingsRevealingItsDefense() {
		play(reporting(0.95), "Rome", "Carthage");
		armyOf("Rome", "Spy", 1);
		game.act("Carthage", new Action.StartBuilding("Walls"));

		spyArrivesAtCarthage();

		assertThat(report("Rome"))
			.contains("Your Spy in Carthage reported that Carthage has built Walls ×1, and its Defense is 10.");
		Facts facts = onMapOf("Rome", "Carthage").facts();
		assertThat(facts.buildings()).isEqualTo(new Fact<>(Map.of("Walls", 1), 4));
		assertThat(facts.defense()).isEqualTo(new Fact<>(10, 4));
	}

	@Test
	void aSpyMayReportThatItsTargetHasBuiltNothing() {
		play(reporting(0.95), "Rome", "Carthage");
		armyOf("Rome", "Spy", 1);

		spyArrivesAtCarthage();

		assertThat(report("Rome"))
			.contains("Your Spy in Carthage reported that Carthage has built nothing, and its Defense is 0.");
	}

	@Test
	void aSpyReportsNothingWhenLuckIsAgainstIt() {
		armyOf("Rome", "Spy", 1);

		spyArrivesAtCarthage();
		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.contains("reported"));
		assertThat(onMapOf("Rome", "Carthage").facts()).isEqualTo(Facts.NONE);
	}

	@Test
	void aLaterReportReplacesTheSameFactAndKeepsTheOthers() {
		play(luck(draw(0), draw(0), draw(0), draw(0.3)), "Rome", "Carthage");
		armyOf("Rome", "Spy", 1);
		spyArrivesAtCarthage();
		game.endTurn();
		game.act("Carthage", new Action.StartBuilding("Farm"));

		game.endTurn();

		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage has 110 Gold.");
		Facts facts = onMapOf("Rome", "Carthage").facts();
		assertThat(facts.gold()).isEqualTo(new Fact<>(110, 6));
		assertThat(facts.population()).isEqualTo(new Fact<>(20, 5));
	}

	@Test
	void onlyTheNationThatSentTheSpyLearnsWhatItReports() {
		play(ALWAYS_GOLD, "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Spy", 1);

		spyArrivesAtCarthage();

		for (String nation : List.of("Carthage", "Egypt")) {
			assertThat(game.viewOf(nation).map()).extracting(MapNation::facts).containsOnly(Facts.NONE);
		}
		assertThat(onMapOf("Rome", "Egypt").facts()).isEqualTo(Facts.NONE);
		assertThat(onMapOf("Rome", "Rome").facts()).isEqualTo(Facts.NONE);
	}

	@Test
	void aSpyInANationThatIsConqueredHeadsHomeAndRejoinsTheArmy() {
		UnitType giant = new UnitType("Giant", 10, 1, 1, 1, 100, List.of());
		game = new Game(new Ruleset(List.of(giant, SPY), List.of(), List.of()), Identities.of("Rome", "Carthage", "Egypt"),
				EVEN_LUCK);
		armyOf("Rome", "Spy", 1);
		armyOf("Egypt", "Giant", 1);
		spyArrivesAtCarthage();
		game.act("Egypt", new Action.SendWarparty("Carthage", Map.of("Giant", 1)));
		for (int turn = 1; turn <= 8; turn++) {
			game.endTurn();
		}
		assertThat(game.viewOf("Carthage").conqueredBy()).isEqualTo("Egypt");

		assertThat(report("Rome")).contains("Your Spy in Carthage is heading home, as Carthage was conquered.");
		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(1, "Spy", "Carthage", true, 3, 3));
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}

		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Spy", 1));
		assertThat(report("Rome")).contains("Your Spy came home from Carthage and rejoined your Army.");
	}

	@Test
	void aSpyHeadingToANationThatIsRemovedTurnsBackHome() {
		armyOf("Rome", "Spy", 1);
		sendSpy("Rome", "Carthage");
		game.endTurn();

		game.remove("Carthage");
		game.endTurn();

		assertThat(report("Rome")).contains("Your Spy heading to Carthage turned back home, as Carthage was removed from the Game.");
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Spy", 1));
	}

	@Test
	void aNationRemovedFromTheGameLosesItsSpiesAbroad() {
		armyOf("Rome", "Spy", 1);
		spyArrivesAtCarthage();

		game.remove("Rome");

		assertThat(game.viewOf("Rome").spies()).isEmpty();
	}

	/**
	 * Over many Turns from a fixed seed, Rome's Spy reports now and then and
	 * every fact Rome's Map shows of Carthage is one a report told it; nothing
	 * is learned of Egypt, where no Spy was sent.
	 */
	@Test
	void aSpyReportsNowAndThenFromAFixedSeedAndNothingElseIsLearned() {
		play(new Random(23), "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Spy", 1);
		spyArrivesAtCarthage();
		int reports = 0;
		int turns = 30;
		for (int turn = 1; turn <= turns; turn++) {
			game.endTurn();
			reports += (int) report("Rome").stream().filter(line -> line.startsWith("Your Spy in Carthage reported")).count();
			Facts facts = onMapOf("Rome", "Carthage").facts();
			if (facts.gold() != null && facts.gold().turn() == game.turn() - 1) {
				assertThat(report("Rome"))
					.contains("Your Spy in Carthage reported that Carthage has " + facts.gold().value() + " Gold.");
				assertThat(facts.gold().value()).isEqualTo(game.viewOf("Carthage").gold());
			}
		}

		assertThat(reports).isPositive().isLessThan(turns);
		assertThat(onMapOf("Rome", "Egypt").facts()).isEqualTo(Facts.NONE);
		assertThat(game.viewOf("Carthage").map()).extracting(MapNation::facts).containsOnly(Facts.NONE);
	}

}
