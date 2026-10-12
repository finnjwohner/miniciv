package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.Rigged;
import static org.assertj.core.api.Assertions.assertThat;

class BarbarianCampsTest {

	static final Barbarians BARBARIANS = new Barbarians(List.of("Mountain Passage", "Black Marsh"), List.of("Grak"),
			List.of(new Barbarians.Building("Spike Pit", 4)));

	static final Ruleset RULESET = new Ruleset(GameTest.RULESET.units(), GameTest.RULESET.buildings(), List.of(),
			List.of(), List.of(), BARBARIANS);

	static final Ruleset RULESET_WITH_KNIGHTS = new Ruleset(List.of(GameTest.WARRIOR, GameTest.ARCHER,
			GameTest.HORSEMAN, new UnitType("Knight", 60, 3, 3, 3, 7, List.of())), List.of(), List.of());

	static final String CAMP = "Black Marsh Barbarian Camp";

	static final String GRAK = "Grak of " + CAMP;

	/**
	 * Draws nothing lucky, 0.99, unless told to: no Camp appears, grows or
	 * raids, and every pick is the last of those it is made from.
	 */
	Rigged luck = new Rigged();

	/**
	 * A Game on its third Turn in which every Nation has Located every other,
	 * each with two Idle Pathfinders.
	 */
	Game game = Locating.everyone(RULESET, Identities.of("Rome", "Carthage", "Egypt"), luck);

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	void untilTurn(int turn) {
		while (game.turn() < turn) {
			game.endTurn();
		}
	}

	void accepted(String nation, Action action) {
		assertThat(game.act(nation, action)).isEqualTo(ActionResult.ACCEPTED);
	}

	void armyOf(String nation, String unit, int count) {
		accepted(nation, new Action.TrainUnits(unit, count));
		while (game.viewOf(nation).army().getOrDefault(unit, 0) < count) {
			game.endTurn();
		}
	}

	/**
	 * Has the Black Marsh Barbarian Camp appear as Turn 8 resolves, with Grak
	 * as its Ruler, every Nation as its Target, Horseman ×5 and Spike Pit ×2;
	 * the Game is then on Turn 9.
	 */
	void campAppears() {
		untilTurn(Game.FIRST_CAMP_TURN);
		luck.then(0);
		game.endTurn();
		assertThat(game.camps()).containsExactly(CAMP);
	}

	/**
	 * Has Rome Locate the Camp with a Pathfinder searching the wilds, as the
	 * Turn resolves.
	 */
	void romeLocatesTheCamp() {
		accepted("Rome", new Action.SetTask("LOCATE", null, 1));
		luck.then(0);
		game.endTurn();
	}

	/**
	 * Has the Camp grow a Horseman and send Horseman ×3 at the Target the
	 * given draw picks, from Rome, Carthage and Egypt, as the Turn resolves.
	 */
	void campRaids(double target) {
		luck.then(0, 0.99, target);
		game.endTurn();
	}

	@Test
	void noCampAppearsBeforeTurnEightNorBeforeAFewTurnsHavePassedSinceTheLast() {
		luck.always(0);
		untilTurn(Game.FIRST_CAMP_TURN);

		assertThat(game.camps()).isEmpty();

		game.endTurn();

		assertThat(game.camps()).containsExactly("Mountain Passage Barbarian Camp");

		untilTurn(Game.FIRST_CAMP_TURN + Game.TURNS_BETWEEN_CAMPS);

		assertThat(game.camps()).hasSize(1);

		game.endTurn();

		assertThat(game.camps()).containsExactly("Mountain Passage Barbarian Camp", CAMP);
	}

	@Test
	void campsNeverOutnumberTheNationsLeftLessOne() {
		game = Locating.everyone(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(), List.of(), List.of(),
				new Barbarians(List.of("A", "B", "C"), List.of("Grak"), List.of())), Identities.of("Rome", "Carthage"),
				luck);
		luck.always(0);

		untilTurn(30);

		assertThat(game.camps()).containsExactly("A Barbarian Camp");
	}

	@Test
	void noCampEverAppearsWithoutBarbariansInTheRuleset() {
		game = Locating.everyone(GameTest.RULESET, Identities.of("Rome", "Carthage", "Egypt"), luck);
		luck.always(0);

		untilTurn(30);

		assertThat(game.camps()).isEmpty();
	}

	@Test
	void noNationKnowsOfACampWhenItAppears() {
		campAppears();

		for (String nation : List.of("Rome", "Carthage", "Egypt")) {
			assertThat(game.viewOf(nation).camps()).isEmpty();
			assertThat(report(nation)).noneMatch(line -> line.contains("Barbarian"));
		}
	}

	@Test
	void scoutsSearchingTheWildsLocateACampShowingItsArmyBuildingsAndTargetsAsTheyWere() {
		campAppears();

		romeLocatesTheCamp();

		assertThat(game.viewOf("Rome").camps()).containsExactly(new MapCamp(CAMP, "Grak", Camp.COLOUR, Camp.EMBLEM,
				false, new CampFacts(Map.of("Horseman", 5), Map.of("Spike Pit", 2), 8, List.of("Rome", "Carthage", "Egypt"),
						9)));
		assertThat(report("Rome")).contains("Your Scouts Located " + CAMP + ", so you can now send Warparties there:"
				+ " its Army is Horseman ×5, its Defense is 8, and it raids Rome, Carthage and Egypt.");
		assertThat(game.viewOf("Rome").tasks()).isEmpty();
		assertThat(game.viewOf("Carthage").camps()).isEmpty();
	}

	@Test
	void searchingTheWildsFindsNothingWhileThereIsNoCampAndDrawsNothing() {
		accepted("Rome", new Action.SetTask("LOCATE", null, 2));
		luck.then(0);

		game.endTurn();

		assertThat(game.viewOf("Rome").camps()).isEmpty();
		assertThat(game.viewOf("Rome").tasks()).containsExactly(new Task(3, Task.Kind.LOCATE, null, 2));
		assertThat(game.act("Rome", new Action.SetTask("LOCATE", null, 1)))
			.isEqualTo(ActionResult.rejected("Your Scouts are already searching the wilds."));
	}

	@Test
	void aCampUnheardOfCannotBeSearchedForByNameNorSentWarparties() {
		campAppears();
		armyOf("Rome", "Warrior", 1);

		assertThat(game.act("Rome", new Action.SetTask("LOCATE", CAMP, 1)))
			.isEqualTo(ActionResult.rejected("There is no Nation called " + CAMP + "."));
		assertThat(game.act("Rome", new Action.SendWarparty(CAMP, Map.of("Warrior", 1))))
			.isEqualTo(ActionResult.rejected("There is no Nation called " + CAMP + "."));
	}

	@Test
	void aCampsWarpartyRaidsATargetWhichHearsItsNameAndLosesGoldPopulationAndCaptives() {
		campAppears();
		campRaids(0);
		untilTurn(14);

		assertThat(report("Rome")).contains(GRAK
				+ "'s Warparty arrived at your Nation; the Battle will be fought as this Turn ends.");
		assertThat(game.viewOf("Rome").camps())
			.containsExactly(new MapCamp(CAMP, "Grak", Camp.COLOUR, Camp.EMBLEM, false, null));
		assertThat(game.viewOf("Rome").arrivals()).isEmpty();
		assertThat(game.act("Rome", new Action.OfferTribute(CAMP, 1, 10, 0)))
			.isEqualTo(ActionResult.rejected("Barbarian Camps accept no Tribute."));
		int population = game.viewOf("Rome").population();
		int gold = game.viewOf("Rome").gold();

		game.endTurn();

		assertThat(report("Rome")).contains(GRAK + "'s Warparty of Horseman ×3 met no Army at your Nation.",
				GRAK + "'s Warparty cost your Nation 24 Population, doubled as you had no Army or Defense.",
				GRAK + "'s Warparty took 36 Gold and 6 Population as Captives from you and is heading home.");
		assertThat(game.viewOf("Rome").population()).isEqualTo(population - 24 - 6 + 2);
		assertThat(game.viewOf("Rome").gold()).isEqualTo(gold - 36 + population - 30);
	}

	@Test
	void aCampNeverTakesTheLastOfANationsPopulation() {
		campAppears();
		campRaids(0);
		untilTurn(13);
		int population = game.viewOf("Rome").population();
		accepted("Rome", new Action.TrainUnits("Scout", population - 2));
		untilTurn(15);

		assertThat(report("Rome")).contains(GRAK + "'s Warparty cost your Nation 3 Population, doubled as you had no"
				+ " Army or Defense.", GRAK + "'s Warparty took 36 Gold from you and is heading home.");
		assertThat(game.inGame("Rome")).isTrue();
		assertThat(game.viewOf("Rome").population()).isEqualTo(1 + 2);
	}

	@Test
	void aWarpartyThatDestroysACampTakesABountyAndItsOtherWarpartiesFinishTheirTripThenDisband() {
		campAppears();
		romeLocatesTheCamp();
		armyOf("Rome", "Warrior", 9);
		accepted("Rome", new Action.SendWarparty(CAMP, Map.of("Warrior", 9)));
		untilTurn(17);

		assertThat(report("Rome")).contains(
				"Your Warparty of Warrior ×9 fought " + CAMP + "'s Army of Horseman ×5. You lost Warrior ×1 and " + CAMP
						+ " lost Horseman ×5.",
				"Your Warparty wore " + CAMP + "'s Defense down by 8, destroying Spike Pit ×2.",
				"Your Warparty destroyed " + CAMP + ", taking 48 Gold as a Bounty, and is heading home.");
		assertThat(game.viewOf("Rome").camps()).extracting(MapCamp::destroyed).containsExactly(true);
		assertThat(game.viewOf("Rome").warparties()).extracting(Warparty::plunder).containsExactly(new Plunder(48, 0));
		assertThat(game.act("Rome", new Action.SendWarparty(CAMP, Map.of("Warrior", 1))))
			.isEqualTo(ActionResult.rejected(CAMP + " has been destroyed."));
	}

	@Test
	void aNationThatFightsABattleAtACampBecomesOneOfItsTargets() {
		untilTurn(Game.FIRST_CAMP_TURN);
		// The Camp appears with Egypt as its only Target.
		luck.then(0, 0.99, 0.99, 0, 0.99);
		game.endTurn();
		romeLocatesTheCamp();
		armyOf("Rome", "Warrior", 1);
		accepted("Rome", new Action.SendWarparty(CAMP, Map.of("Warrior", 1)));
		untilTurn(15);

		assertThat(report("Rome")).contains("Your Warparty sent to " + CAMP + " was destroyed.");

		accepted("Carthage", new Action.SetTask("LOCATE", null, 1));
		luck.then(0);
		game.endTurn();

		assertThat(game.viewOf("Rome").camps().getFirst().located().targets()).containsExactly("Egypt");
		assertThat(game.viewOf("Carthage").camps().getFirst().located().targets()).containsExactly("Egypt", "Rome");
	}

	@Test
	void campsNeverCountTowardsTheLastNationStanding() {
		campAppears();

		game.remove("Carthage");
		game.remove("Egypt");

		assertThat(game.winner()).contains("Rome");
	}

	@Test
	void aCampsWarpartyChoosesToFightOnlyWhenAtLeastAsStrongAndTheNationHearsOfIt() {
		campAppears();
		armyOf("Rome", "Warrior", 1);
		accepted("Rome", new Action.SendWarparty("Egypt", Map.of("Warrior", 1)));
		// The Camp grows a Horseman, raids Carthage, and its Warparty meets Rome's.
		luck.then(0, 0.99, 0.4, 0);
		game.endTurn();

		assertThat(game.viewOf("Rome").encounters()).containsExactly(
				new Encounter(new Encounter.Side("Rome", 1, Map.of("Warrior", 1), Encounter.Choice.NONE),
						new Encounter.Side(CAMP, 1, Map.of("Horseman", 3), Encounter.Choice.FIGHT)));
		assertThat(report("Rome")).contains("Your Warparty of Warrior ×1 heading to Egypt met " + GRAK
				+ "'s Warparty of Horseman ×3; choose whether to fight before this Turn ends.");
		assertThat(game.viewOf("Rome").camps()).extracting(MapCamp::name).containsExactly(CAMP);

		game.endTurn();

		assertThat(report("Rome")).contains(
				"Your Warparty of Warrior ×1 heading to Egypt fought " + GRAK + "'s Warparty of Horseman ×3, as " + CAMP
						+ " chose to fight. You lost Warrior ×1 and " + CAMP + " lost no Units.",
				"Your Warparty heading to Egypt was destroyed.");
	}

	@Test
	void wipingOutACampsWarpartyTakesItsPlunderToKeepOrSendBackAndMakesTheNationATarget() {
		campAppears();
		campRaids(0.4);
		untilTurn(13);
		armyOf("Rome", "Warrior", 9);
		accepted("Rome", new Action.SendWarparty("Egypt", Map.of("Warrior", 9)));
		// The Battle at Carthage; the Camp neither grows nor has another appear;
		// Rome's Warparty meets the Camp's heading home.
		luck.then(0.99, 0.99, 0.99, 0.99, 0);
		game.endTurn();

		assertThat(game.viewOf("Rome").encounters()).extracting(encounter -> encounter.second().choice())
			.containsExactly(Encounter.Choice.PASS);

		accepted("Rome", new Action.AnswerEncounter(1, true));
		game.endTurn();

		assertThat(report("Rome")).contains("Your Warparty took " + CAMP + "'s Plunder of 36 Gold and 6 Population, taken"
				+ " from Carthage; choose whether to keep it or send it back before this Turn ends.");
		assertThat(game.viewOf("Rome").hauls()).containsExactly(
				new HaulDecision(1, "Carthage", new Haul(Resource.GOLD, 36), HaulDecision.Choice.NONE, true),
				new HaulDecision(2, "Carthage", new Haul(Resource.POPULATION, 6), HaulDecision.Choice.NONE, true));
	}

	@Test
	void nationsCannotSendSpiesToACamp() {
		Ruleset withSpies = new Ruleset(List.of(GameTest.WARRIOR, GameTest.ARCHER, GameTest.HORSEMAN, new UnitType("Spy", 0, 0, 1, 0, 0.1, List.of(), false,
				true)), List.of(), List.of(), List.of(), List.of(), BARBARIANS);
		game = Locating.everyone(withSpies, Identities.of("Rome", "Carthage", "Egypt"), luck);
		campAppears();
		romeLocatesTheCamp();
		armyOf("Rome", "Spy", 1);

		assertThat(game.act("Rome", new Action.SendSpy(CAMP)))
			.isEqualTo(ActionResult.rejected("Spies cannot be sent to a Barbarian Camp."));
	}

	@Test
	void aCampWillNotRaidATargetItLostAWarpartyAtUntilItsArmyOutgrowsThatWarparty() {
		Camp camp = new Camp("Black Marsh", "Grak", List.of("Rome", "Carthage"), Map.of("Horseman", 12), Map.of());
		Warparty lost = camp.raid(List.of("Rome", "Carthage"), RULESET, GameTest.luck(0)).orElseThrow();
		camp.foughtBattle(lost, Map.of(), Plunder.NONE, Plunder.NONE, RULESET);

		assertThat(lost.target()).isEqualTo("Rome");
		assertThat(camp.raid(List.of("Rome", "Carthage"), RULESET, GameTest.luck(0)))
			.map(Warparty::target)
			.contains("Carthage");
	}

	@Test
	void aRaidSendsHalfTheCampsUnitsAndNeverEmptiesItsArmy() {
		Camp camp = new Camp("Black Marsh", "Grak", List.of("Rome"),
				Map.of("Warrior", 1, "Archer", 1, "Horseman", 3, "Knight", 1), Map.of());

		Warparty sent = camp.raid(List.of("Rome"), RULESET_WITH_KNIGHTS, GameTest.luck(0)).orElseThrow();

		assertThat(sent.unitCount()).isEqualTo(3);
		assertThat(camp.army().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(3);
	}

	@Test
	void aCampRaidsNoTargetOutOfTheGame() {
		Camp camp = new Camp("Black Marsh", "Grak", List.of("Rome", "Carthage"), Map.of("Horseman", 6), Map.of());

		assertThat(camp.raid(List.of("Carthage"), RULESET, GameTest.luck(0))).map(Warparty::target).contains("Carthage");
	}

}
