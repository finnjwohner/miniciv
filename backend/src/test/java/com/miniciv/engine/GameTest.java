package com.miniciv.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;

class GameTest {

	static final BuildingType FARM = new BuildingType("Farm", 30, 2, 1, 0, 0);

	static final BuildingType GRANARY = new BuildingType("Granary", 40, 1, 2, 0, 0);

	static final BuildingType BANK = new BuildingType("Bank", 45, 1, 0, 0.5, 0);

	static final BuildingType WALLS = new BuildingType("Walls", 30, 2, 0, 0, 10);

	static final BuildingType MOAT = new BuildingType("Moat", 20, 1, 0, 0, 4);

	static final UnitType WARRIOR = new UnitType("Warrior", 10, 3, 1, 1, 3, List.of("Horseman"));

	static final UnitType ARCHER = new UnitType("Archer", 15, 1, 2, 1, 3, List.of("Warrior"));

	static final UnitType HORSEMAN = new UnitType("Horseman", 20, 2, 2, 6, 4, List.of("Archer"));

	static final UnitType SCOUT = new UnitType("Scout", 5, 1, 1, 1, 0.1, List.of());

	static final Ruleset RULESET = new Ruleset(List.of(WARRIOR, ARCHER, HORSEMAN, SCOUT),
			List.of(FARM, GRANARY, BANK, WALLS, MOAT), List.of());

	static final String BASE_INCOME_REPORT = "Your Nation gained 10 Gold, 2 Population and 3 Research.";

	/**
	 * Luck that leaves every Battle to Strength and counters alone.
	 */
	static final RandomGenerator EVEN_LUCK = luck(Long.MIN_VALUE);

	Game game = new Game(RULESET, List.of("Rome", "Carthage", "Egypt"), EVEN_LUCK);

	/**
	 * A random source that draws the given values over and over; Long.MIN_VALUE
	 * draws a double of exactly 0.5, 0 the lowest and -1 the highest.
	 */
	static RandomGenerator luck(long... draws) {
		int[] next = { 0 };
		return () -> draws[next[0]++ % draws.length];
	}

	@Test
	void everyNationStartsWithTheSameBaseGoldPopulationAndResearch() {
		for (String nation : List.of("Rome", "Carthage", "Egypt")) {
			NationView view = game.viewOf(nation);
			assertThat(view.turn()).isEqualTo(1);
			assertThat(view.gold()).isEqualTo(50);
			assertThat(view.population()).isEqualTo(10);
			assertThat(view.research()).isEqualTo(0);
		}
	}

	@Test
	void endingATurnGivesEveryNationTheFlatBaseIncome() {
		game.endTurn();
		game.endTurn();

		for (String nation : List.of("Rome", "Carthage", "Egypt")) {
			NationView view = game.viewOf(nation);
			assertThat(view.turn()).isEqualTo(3);
			assertThat(view.gold()).isEqualTo(70);
			assertThat(view.population()).isEqualTo(14);
			assertThat(view.research()).isEqualTo(6);
		}
	}

	@Test
	void thereIsNoTurnReportBeforeTheFirstTurnHasEnded() {
		assertThat(game.viewOf("Rome").turnReport()).isNull();
	}

	@Test
	void endingATurnGivesEachNationATurnReportOfWhatHappened() {
		game.endTurn();

		TurnReport report = game.viewOf("Rome").turnReport();
		assertThat(report.turn()).isEqualTo(1);
		assertThat(report.lines()).containsExactly("Your Nation gained 10 Gold, 2 Population and 3 Research.");
	}

	@Test
	void aNationsViewShowsItsOwnStateAndOnlyTheNamesOfTheOtherNations() {
		game.endTurn();

		assertThat(game.viewOf("Carthage")).isEqualTo(new NationView(2, 2, "Carthage", 60, 12, 3, List.of("Rome", "Egypt"),
				List.of(FARM, GRANARY, BANK, WALLS, MOAT), Map.of(), List.of(), List.of(), 0, List.of(),
				List.of(WARRIOR, ARCHER, HORSEMAN, SCOUT), Map.of(), List.of(), List.of(), 3, 0,
				new TurnReport(1, List.of("Your Nation gained 10 Gold, 2 Population and 3 Research.")), null, null));
	}

	@Test
	void startingABuildingPaysItsGoldAtOnceAndPutsItUnderConstruction() {
		ActionResult result = game.act("Rome", new Action.StartBuilding("Farm"));

		assertThat(result).isEqualTo(ActionResult.ACCEPTED);
		NationView rome = game.viewOf("Rome");
		assertThat(rome.gold()).isEqualTo(20);
		assertThat(rome.construction()).containsExactly(new Construction("Farm", 2));
		assertThat(game.viewOf("Carthage").gold()).isEqualTo(50);
	}

	@Test
	void aBuildingTheNationCannotAffordIsRejectedWithAReasonAndCostsNothing() {
		game.act("Rome", new Action.StartBuilding("Farm"));

		ActionResult result = game.act("Rome", new Action.StartBuilding("Farm"));

		assertThat(result).isEqualTo(ActionResult.rejected("Farm costs 30 Gold, but you have only 20."));
		assertThat(game.viewOf("Rome").gold()).isEqualTo(20);
		assertThat(game.viewOf("Rome").construction()).containsExactly(new Construction("Farm", 2));
	}

	@Test
	void aBuildingThatIsNotInTheRulesetIsRejected() {
		ActionResult result = game.act("Rome", new Action.StartBuilding("Pyramid"));

		assertThat(result).isEqualTo(ActionResult.rejected("There is no Building called Pyramid."));
		assertThat(game.viewOf("Rome").gold()).isEqualTo(50);
	}

	@Test
	void severalBuildingsCanBeStartedInOneTurnWhileTheyAreAffordable() {
		game.endTurn();
		game.endTurn();

		assertThat(game.act("Rome", new Action.StartBuilding("Farm"))).isEqualTo(ActionResult.ACCEPTED);
		assertThat(game.act("Rome", new Action.StartBuilding("Farm"))).isEqualTo(ActionResult.ACCEPTED);

		NationView rome = game.viewOf("Rome");
		assertThat(rome.gold()).isEqualTo(10);
		assertThat(rome.construction()).containsExactly(new Construction("Farm", 2), new Construction("Farm", 2));
	}

	@Test
	void aBuildingCompletesOnceItsConstructionTurnsHaveResolved() {
		game.act("Rome", new Action.StartBuilding("Farm"));

		game.endTurn();
		NationView rome = game.viewOf("Rome");
		assertThat(rome.construction()).containsExactly(new Construction("Farm", 1));
		assertThat(rome.buildings()).isEmpty();

		game.endTurn();
		rome = game.viewOf("Rome");
		assertThat(rome.construction()).isEmpty();
		assertThat(rome.buildings()).containsExactly(entry("Farm", 1));
		assertThat(rome.turnReport().lines()).contains("Your Farm has been built.");
	}

	@Test
	void aNationsBuildingsAreCountedByType() {
		game.act("Rome", new Action.StartBuilding("Granary"));
		for (int turn = 1; turn <= 8; turn++) {
			game.endTurn();
		}
		game.act("Rome", new Action.StartBuilding("Granary"));
		game.act("Rome", new Action.StartBuilding("Bank"));
		game.endTurn();

		assertThat(game.viewOf("Rome").buildings()).containsExactly(entry("Granary", 2), entry("Bank", 1));
	}

	@Test
	void completedBuildingsAddPopulationEachTurnFromTheTurnTheyComplete() {
		game.act("Rome", new Action.StartBuilding("Granary"));

		game.endTurn();
		assertThat(game.viewOf("Rome").population()).isEqualTo(14);
		assertThat(game.viewOf("Rome").turnReport().lines()).containsExactly("Your Granary has been built.",
				"Your Nation gained 10 Gold, 2 Population and 3 Research.", "Your Buildings added 2 Population.");

		game.endTurn();
		assertThat(game.viewOf("Rome").population()).isEqualTo(18);
	}

	@Test
	void aBankAddsGoldForEveryPopulationTheNationHadWhenTheTurnEnded() {
		game.act("Rome", new Action.StartBuilding("Bank"));

		game.endTurn();
		assertThat(game.viewOf("Rome").gold()).isEqualTo(5 + 10 + 5);
		assertThat(game.viewOf("Rome").turnReport().lines()).contains("Your Buildings added 5 Gold.");

		game.endTurn();
		assertThat(game.viewOf("Rome").gold()).isEqualTo(20 + 10 + 6);
	}

	@Test
	void theTurnReportSumsUpWhatAllTheNationsBuildingsAdded() {
		for (int turn = 1; turn <= 4; turn++) {
			game.endTurn();
		}
		game.act("Rome", new Action.StartBuilding("Granary"));
		game.act("Rome", new Action.StartBuilding("Bank"));

		game.endTurn();

		assertThat(game.viewOf("Rome").turnReport().lines()).contains("Your Buildings added 2 Population and 9 Gold.");
	}

	@Test
	void aNationsViewListsTheBuildingsItCanStart() {
		assertThat(game.viewOf("Rome").availableBuildings()).containsExactly(FARM, GRANARY, BANK, WALLS, MOAT);
	}

	@Test
	void aNationsViewVersionRisesWhenTheNationActsOrATurnEnds() {
		int first = game.viewOf("Rome").version();

		game.act("Rome", new Action.StartBuilding("Farm"));
		int afterActing = game.viewOf("Rome").version();
		game.act("Rome", new Action.StartBuilding("Bank"));
		int afterARejection = game.viewOf("Rome").version();
		game.endTurn();
		int afterTheTurn = game.viewOf("Rome").version();

		assertThat(afterActing).isGreaterThan(first);
		assertThat(afterARejection).isEqualTo(afterActing);
		assertThat(afterTheTurn).isGreaterThan(afterActing);
	}

	@Test
	void trainingUnitsPaysTheirGoldAndPopulationAtOnceAndPutsThemInTraining() {
		ActionResult result = game.act("Rome", new Action.TrainUnits("Warrior", 3));

		assertThat(result).isEqualTo(ActionResult.ACCEPTED);
		NationView rome = game.viewOf("Rome");
		assertThat(rome.gold()).isEqualTo(20);
		assertThat(rome.population()).isEqualTo(1);
		assertThat(rome.training()).containsExactly(new Training("Warrior", 3, 2));
		assertThat(rome.army()).isEmpty();
		assertThat(game.viewOf("Carthage").gold()).isEqualTo(50);
	}

	@Test
	void unitsTheNationCannotAffordInGoldAreRejectedWithAReasonAndCostNothing() {
		ActionResult result = game.act("Rome", new Action.TrainUnits("Horseman", 3));

		assertThat(result).isEqualTo(ActionResult.rejected("Horseman ×3 costs 60 Gold, but you have only 50."));
		assertThat(game.viewOf("Rome").gold()).isEqualTo(50);
		assertThat(game.viewOf("Rome").population()).isEqualTo(10);
		assertThat(game.viewOf("Rome").training()).isEmpty();
	}

	@Test
	void unitsTheNationCannotAffordInPopulationAreRejectedWithAReasonAndCostNothing() {
		ActionResult result = game.act("Rome", new Action.TrainUnits("Warrior", 4));

		assertThat(result).isEqualTo(ActionResult.rejected("Warrior ×4 costs 12 Population, but you have only 10."));
		assertThat(game.viewOf("Rome").gold()).isEqualTo(50);
		assertThat(game.viewOf("Rome").population()).isEqualTo(10);
	}

	@Test
	void trainingNoUnitsOrAUnitThatCannotBeTrainedIsRejected() {
		assertThat(game.act("Rome", new Action.TrainUnits("Warrior", 0)))
			.isEqualTo(ActionResult.rejected("Train at least one Unit."));
		assertThat(game.act("Rome", new Action.TrainUnits("Catapult", 1)))
			.isEqualTo(ActionResult.rejected("There is no Unit called Catapult that you can train."));
		assertThat(game.viewOf("Rome").gold()).isEqualTo(50);
	}

	@Test
	void severalKindsOfUnitsCanBeTrainedInOneTurnWhileTheyAreAffordable() {
		assertThat(game.act("Rome", new Action.TrainUnits("Warrior", 1))).isEqualTo(ActionResult.ACCEPTED);
		assertThat(game.act("Rome", new Action.TrainUnits("Archer", 2))).isEqualTo(ActionResult.ACCEPTED);

		NationView rome = game.viewOf("Rome");
		assertThat(rome.gold()).isEqualTo(10);
		assertThat(rome.population()).isEqualTo(5);
		assertThat(rome.training()).containsExactly(new Training("Warrior", 1, 2), new Training("Archer", 2, 3));
	}

	@Test
	void trainedUnitsJoinTheArmyOnTheTurnTheyAreReady() {
		game.act("Rome", new Action.TrainUnits("Archer", 2));

		game.endTurn();
		assertThat(game.viewOf("Rome").army()).isEmpty();
		assertThat(game.viewOf("Rome").training()).containsExactly(new Training("Archer", 2, 3));

		game.endTurn();
		NationView rome = game.viewOf("Rome");
		assertThat(rome.turn()).isEqualTo(3);
		assertThat(rome.training()).isEmpty();
		assertThat(rome.army()).containsExactly(entry("Archer", 2));
		assertThat(rome.turnReport().lines()).contains("Archer ×2 finished training and joined your Army.");
	}

	@Test
	void theTurnReportListsEveryKindOfUnitThatJoinedTheArmy() {
		game.act("Rome", new Action.TrainUnits("Archer", 1));
		game.endTurn();
		game.act("Rome", new Action.TrainUnits("Warrior", 2));

		game.endTurn();

		assertThat(game.viewOf("Rome").turnReport().lines())
			.contains("Archer ×1 and Warrior ×2 finished training and joined your Army.");
	}

	@Test
	void theArmyCountsUnitsByType() {
		game.act("Rome", new Action.TrainUnits("Warrior", 1));
		game.endTurn();
		game.act("Rome", new Action.TrainUnits("Archer", 1));
		game.act("Rome", new Action.TrainUnits("Warrior", 1));
		game.endTurn();
		game.endTurn();

		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Warrior", 2), entry("Archer", 1));
	}

	@Test
	void theArmysUpkeepIsChargedEachTurnFromTheTurnItsUnitsJoin() {
		game.act("Rome", new Action.TrainUnits("Warrior", 2));

		game.endTurn();
		NationView rome = game.viewOf("Rome");
		assertThat(rome.gold()).isEqualTo(30 + 10 - 2);
		assertThat(rome.upkeep()).isEqualTo(2);
		assertThat(rome.turnReport().lines()).containsExactly("Warrior ×2 finished training and joined your Army.",
				"Your Nation gained 10 Gold, 2 Population and 3 Research.", "Your Units' upkeep cost 2 Gold.");

		game.endTurn();
		assertThat(game.viewOf("Rome").gold()).isEqualTo(38 + 10 - 2);
	}

	@Test
	void unitsInTrainingCostNoUpkeep() {
		game.act("Rome", new Action.TrainUnits("Archer", 1));

		game.endTurn();

		assertThat(game.viewOf("Rome").gold()).isEqualTo(35 + 10);
		assertThat(game.viewOf("Rome").upkeep()).isZero();
	}

	@Test
	void unitsDesertUntilTheRemainingUpkeepIsAffordableAndTheTurnReportSaysWho() {
		game.act("Rome", new Action.TrainUnits("Warrior", 1));
		game.act("Rome", new Action.TrainUnits("Horseman", 2));
		for (int turn = 1; turn <= 4; turn++) {
			game.endTurn();
		}
		NationView before = game.viewOf("Rome");
		assertThat(before.army()).containsExactly(entry("Warrior", 1), entry("Horseman", 2));
		assertThat(before.gold()).isZero();

		game.endTurn();

		NationView rome = game.viewOf("Rome");
		assertThat(rome.army()).containsExactly(entry("Warrior", 1), entry("Horseman", 1));
		assertThat(rome.gold()).isEqualTo(10 - 7);
		assertThat(rome.upkeep()).isEqualTo(7);
		assertThat(rome.turnReport().lines()).containsExactly(
				"Your Nation gained 10 Gold, 2 Population and 3 Research.",
				"You could not pay your Units' upkeep, so Horseman ×1 deserted from your Army.", "Your Units' upkeep cost 7 Gold.");
	}

	@Test
	void aNationsViewListsTheUnitsItCanTrainWithTheirStrengthCostsTrainingTimeUpkeepAndCounters() {
		assertThat(game.viewOf("Rome").availableUnits()).containsExactly(WARRIOR, ARCHER, HORSEMAN, SCOUT);
	}

	/**
	 * Trains Units for a Nation and ends Turns until they have joined its Army.
	 */
	void armyOf(String nation, String unit, int count) {
		assertThat(game.act(nation, new Action.TrainUnits(unit, count))).isEqualTo(ActionResult.ACCEPTED);
		while (game.viewOf(nation).army().getOrDefault(unit, 0) < count) {
			game.endTurn();
		}
	}

	@Test
	void sendingAWarpartyTakesItsUnitsOutOfTheArmyAndSetsThemTravellingForTheBaseTravelTime() {
		armyOf("Rome", "Warrior", 3);

		ActionResult result = game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));

		assertThat(result).isEqualTo(ActionResult.ACCEPTED);
		NationView rome = game.viewOf("Rome");
		assertThat(rome.army()).containsExactly(entry("Warrior", 1));
		assertThat(rome.warparties()).containsExactly(new Warparty("Carthage", Map.of("Warrior", 2), false, 3));
		assertThat(rome.travelTurns()).isEqualTo(3);
	}

	@Test
	void aWarpartyMustGoToAnotherNationInTheGame() {
		armyOf("Rome", "Warrior", 1);

		assertThat(game.act("Rome", new Action.SendWarparty("Rome", Map.of("Warrior", 1))))
			.isEqualTo(ActionResult.rejected("A Warparty must be sent to another Nation."));
		assertThat(game.act("Rome", new Action.SendWarparty("Persia", Map.of("Warrior", 1))))
			.isEqualTo(ActionResult.rejected("There is no Nation called Persia."));
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Warrior", 1));
		assertThat(game.viewOf("Rome").warparties()).isEmpty();
	}

	@Test
	void aWarpartyCanOnlyBeMadeOfUnitsTheArmyHas() {
		armyOf("Rome", "Warrior", 2);

		assertThat(game.act("Rome", new Action.SendWarparty("Carthage", Map.of())))
			.isEqualTo(ActionResult.rejected("Choose at least one Unit to send."));
		assertThat(game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 0))))
			.isEqualTo(ActionResult.rejected("Choose at least one Unit to send."));
		assertThat(game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", -1))))
			.isEqualTo(ActionResult.rejected("Choose at least one Unit to send."));
		assertThat(game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 3))))
			.isEqualTo(ActionResult.rejected("Your Army has only 2 Warrior."));
		assertThat(game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 1, "Archer", 1))))
			.isEqualTo(ActionResult.rejected("Your Army has no Archer."));
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Warrior", 2));
		assertThat(game.viewOf("Rome").warparties()).isEmpty();
	}

	@Test
	void aWarpartyCountsDownItsTravelAndOnArrivalBothSidesAreToldButTheDefenderLearnsNothingEarlier() {
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));

		game.endTurn();
		assertThat(game.viewOf("Rome").warparties()).containsExactly(new Warparty("Carthage", Map.of("Warrior", 2), false, 2));
		assertThat(game.viewOf("Carthage").turnReport().lines()).containsExactly(BASE_INCOME_REPORT);

		game.endTurn();
		assertThat(game.viewOf("Rome").warparties()).containsExactly(new Warparty("Carthage", Map.of("Warrior", 2), false, 1));
		assertThat(game.viewOf("Carthage").turnReport().lines()).containsExactly(BASE_INCOME_REPORT);

		game.endTurn();
		NationView rome = game.viewOf("Rome");
		assertThat(rome.warparties()).containsExactly(new Warparty("Carthage", Map.of("Warrior", 2), false, 0));
		assertThat(rome.turnReport().lines()).contains(
				"Your Warparty of Warrior ×2 arrived at Carthage; the Battle will be fought as this Turn ends.");
		assertThat(game.viewOf("Carthage").turnReport().lines()).containsExactly(
				"A Warparty from Rome arrived at your Nation; the Battle will be fought as this Turn ends.",
				BASE_INCOME_REPORT);
		assertThat(game.viewOf("Egypt").turnReport().lines()).containsExactly(BASE_INCOME_REPORT);
	}

	/**
	 * Sends a Warparty and ends Turns until it has arrived at its target, so
	 * that the Battle is fought as the next Turn ends.
	 */
	void arrived(String nation, String target, Map<String, Integer> units) {
		assertThat(game.act(nation, new Action.SendWarparty(target, units))).isEqualTo(ActionResult.ACCEPTED);
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}
	}

	@Test
	void aWarpartyMeetingNoArmyLosesNothingAndCarriesHomePlunderScaledByItsStrength() {
		armyOf("Rome", "Warrior", 2);
		arrived("Rome", "Carthage", Map.of("Warrior", 2));
		int carthageGold = game.viewOf("Carthage").gold();
		int carthageResearch = game.viewOf("Carthage").research();

		game.endTurn();

		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty("Carthage", Map.of("Warrior", 2), true, 3, new Plunder(18, 6)));
		assertThat(game.viewOf("Rome").turnReport().lines()).contains(
				"Your Warparty of Warrior ×2 met no Army at Carthage.",
				"Your Warparty took 18 Gold and 6 Research from Carthage and is heading home.");
		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.gold()).isEqualTo(carthageGold - 18 + 10);
		assertThat(carthage.research()).isEqualTo(carthageResearch - 6 + 3);
		assertThat(carthage.turnReport().lines()).containsExactly(
				"Rome's Warparty of Warrior ×2 met no Army at your Nation.",
				"Rome's Warparty reduced your Population by 12, doubled as you had no Army or Defense.",
				"Rome's Warparty took 18 Gold and 6 Research from you and is heading home.", BASE_INCOME_REPORT);
	}

	@Test
	void plunderJoinsTheNationWhenItsWarpartyComesHome() {
		armyOf("Rome", "Warrior", 2);
		arrived("Rome", "Carthage", Map.of("Warrior", 2));
		game.endTurn();
		game.endTurn();
		game.endTurn();
		int romeGold = game.viewOf("Rome").gold();
		int romeResearch = game.viewOf("Rome").research();

		game.endTurn();

		NationView rome = game.viewOf("Rome");
		assertThat(rome.warparties()).isEmpty();
		assertThat(rome.army()).containsExactly(entry("Warrior", 2));
		assertThat(rome.gold()).isEqualTo(romeGold + 18 + 10 - 2);
		assertThat(rome.research()).isEqualTo(romeResearch + 6 + 3);
		assertThat(rome.turnReport().lines()).contains(
				"Your Warparty came home from Carthage with 18 Gold and 6 Research, and Warrior ×2 rejoined your Army.");
	}

	@Test
	void aMuchStrongerWarpartyWipesOutTheArmyWithoutLossAndBothSidesAreToldOfTheBattle() {
		for (int turn = 1; turn <= 5; turn++) {
			game.endTurn();
		}
		armyOf("Rome", "Warrior", 4);
		armyOf("Carthage", "Warrior", 1);
		arrived("Rome", "Carthage", Map.of("Warrior", 4));
		int carthageGold = game.viewOf("Carthage").gold();
		int carthageResearch = game.viewOf("Carthage").research();
		int carthagePopulation = game.viewOf("Carthage").population();
		assertThat(carthagePopulation).isGreaterThan(24);

		game.endTurn();

		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty("Carthage", Map.of("Warrior", 4), true, 3, new Plunder(36, 12)));
		assertThat(game.viewOf("Rome").turnReport().lines()).contains(
				"Your Warparty of Warrior ×4 fought Carthage's Army of Warrior ×1. You lost no Units and Carthage lost Warrior ×1.",
				"Your Warparty took 36 Gold and 12 Research from Carthage and is heading home.");
		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.army()).isEmpty();
		assertThat(carthage.gold()).isEqualTo(carthageGold - 36 + 10);
		assertThat(carthage.research()).isEqualTo(carthageResearch - 12 + 3);
		assertThat(carthage.turnReport().lines()).containsExactly(
				"Your Army of Warrior ×1 fought Rome's Warparty of Warrior ×4. You lost Warrior ×1 and Rome lost no Units.",
				"Rome's Warparty reduced your Population by 24, doubled as your Army was wiped out.",
				"Rome's Warparty took 36 Gold and 12 Research from you and is heading home.", BASE_INCOME_REPORT);
	}

	@Test
	void inACloseFightBothSidesLoseUnitsAndTheSurvivorsPlunderLess() {
		game.endTurn();
		armyOf("Rome", "Warrior", 2);
		armyOf("Carthage", "Warrior", 2);
		arrived("Rome", "Carthage", Map.of("Warrior", 2));

		game.endTurn();

		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty("Carthage", Map.of("Warrior", 1), true, 3, new Plunder(9, 3)));
		assertThat(game.viewOf("Carthage").army()).containsExactly(entry("Warrior", 1));
		assertThat(game.viewOf("Rome").turnReport().lines()).contains(
				"Your Warparty of Warrior ×2 fought Carthage's Army of Warrior ×2. You lost Warrior ×1 and Carthage lost Warrior ×1.");
	}

	@Test
	void unitsFightingATypeTheyCounterAreStrongerSoEvenNumbersAreNoLongerAnEvenFight() {
		armyOf("Rome", "Archer", 2);
		armyOf("Carthage", "Warrior", 2);
		arrived("Rome", "Carthage", Map.of("Archer", 2));

		game.endTurn();

		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty("Carthage", Map.of("Archer", 2), true, 3, new Plunder(18, 6)));
		assertThat(game.viewOf("Carthage").army()).isEmpty();
		assertThat(game.viewOf("Rome").turnReport().lines()).contains(
				"Your Warparty of Archer ×2 fought Carthage's Army of Warrior ×2. You lost no Units and Carthage lost Warrior ×2.");
	}

	@Test
	void unitsTheDefenderFinishesTrainingInTheTurnAWarpartyArrivesDefendAgainstIt() {
		armyOf("Rome", "Warrior", 2);
		arrived("Rome", "Carthage", Map.of("Warrior", 2));
		assertThat(game.viewOf("Carthage").army()).isEmpty();
		game.act("Carthage", new Action.TrainUnits("Warrior", 3));

		game.endTurn();

		assertThat(game.viewOf("Rome").warparties()).isEmpty();
		assertThat(game.viewOf("Carthage").army()).containsExactly(entry("Warrior", 2));
		assertThat(game.viewOf("Carthage").turnReport().lines()).containsSequence(
				"Warrior ×3 finished training and joined your Army.",
				"Your Army of Warrior ×3 fought Rome's Warparty of Warrior ×2. You lost Warrior ×1 and Rome lost Warrior ×2.",
				"Rome's Warparty was destroyed.");
	}

	@Test
	void scoutsInABeatenWarpartyAreLostWithIt() {
		armyOf("Rome", "Warrior", 1);
		armyOf("Rome", "Scout", 1);
		armyOf("Carthage", "Warrior", 3);
		arrived("Rome", "Carthage", Map.of("Warrior", 1, "Scout", 1));

		game.endTurn();

		assertThat(game.viewOf("Rome").warparties()).isEmpty();
		assertThat(game.viewOf("Rome").turnReport().lines()).contains(
				"Your Warparty of Warrior ×1 and Scout ×1 fought Carthage's Army of Warrior ×3. You lost Warrior ×1 and Scout ×1 and Carthage lost no Units.",
				"Your Warparty sent to Carthage was destroyed.");
	}

	@Test
	void scoutsAtHomeAreLostWithABeatenArmy() {
		game.endTurn();
		armyOf("Rome", "Warrior", 4);
		armyOf("Carthage", "Warrior", 1);
		armyOf("Carthage", "Scout", 1);
		arrived("Rome", "Carthage", Map.of("Warrior", 4));

		game.endTurn();

		assertThat(game.viewOf("Carthage").army()).isEmpty();
		assertThat(game.viewOf("Carthage").turnReport().lines()).contains(
				"Your Army of Warrior ×1 and Scout ×1 fought Rome's Warparty of Warrior ×4. You lost Warrior ×1 and Scout ×1 and Rome lost no Units.");
	}

	@Test
	void luckMovesTheOutcomeOfABattleALittle() {
		game = new Game(RULESET, List.of("Rome", "Carthage", "Egypt"), luck(-1, 0));
		game.endTurn();
		armyOf("Rome", "Warrior", 4);
		game.endTurn();
		armyOf("Carthage", "Warrior", 4);
		arrived("Rome", "Carthage", Map.of("Warrior", 4));

		game.endTurn();

		assertThat(game.viewOf("Rome").turnReport().lines()).contains(
				"Your Warparty of Warrior ×4 fought Carthage's Army of Warrior ×4. You lost Warrior ×1 and Carthage lost Warrior ×3.");
	}

	@Test
	void aBattleFoughtFromTheSameSeedHasTheSameOutcome() {
		List<List<String>> reports = new ArrayList<>();
		for (int replay = 1; replay <= 2; replay++) {
			game = new Game(RULESET, List.of("Rome", "Carthage", "Egypt"), new Random(7));
			game.endTurn();
			armyOf("Rome", "Warrior", 4);
			game.endTurn();
			armyOf("Carthage", "Warrior", 4);
			arrived("Rome", "Carthage", Map.of("Warrior", 4));
			game.endTurn();
			reports.add(game.viewOf("Rome").turnReport().lines());
		}

		assertThat(reports.get(0)).anyMatch(line -> line.startsWith("Your Warparty of Warrior ×4 fought"));
		assertThat(reports.get(1)).isEqualTo(reports.get(0));
	}

	@Test
	void completedDefensiveBuildingsRaiseTheNationsDefense() {
		assertThat(game.viewOf("Rome").defense()).isZero();
		game.act("Rome", new Action.StartBuilding("Walls"));
		game.act("Rome", new Action.StartBuilding("Moat"));
		assertThat(game.viewOf("Rome").defense()).isZero();
		game.endTurn();
		assertThat(game.viewOf("Rome").defense()).isEqualTo(4);

		game.endTurn();

		assertThat(game.viewOf("Rome").defense()).isEqualTo(14);
		assertThat(game.viewOf("Rome").damage()).isEmpty();
	}

	@Test
	void survivingStrengthWearsDefenseDownDamagingADefensiveBuildingAndSparingThePopulation() {
		game.act("Carthage", new Action.StartBuilding("Walls"));
		armyOf("Rome", "Warrior", 2);
		arrived("Rome", "Carthage", Map.of("Warrior", 2));
		int carthagePopulation = game.viewOf("Carthage").population();

		game.endTurn();

		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.defense()).isEqualTo(4);
		assertThat(carthage.damage()).containsExactly(new BuildingDamage("Walls", 0, 6, 18, 2));
		assertThat(carthage.population()).isEqualTo(carthagePopulation + 2);
		assertThat(carthage.turnReport().lines()).containsExactly(
				"Rome's Warparty of Warrior ×2 met no Army at your Nation.",
				"Rome's Warparty wore your Defense down from 10 to 4, damaging Walls.",
				"Rome's Warparty took 18 Gold and 6 Research from you and is heading home.", BASE_INCOME_REPORT);
		assertThat(game.viewOf("Rome").turnReport().lines())
			.contains("Your Warparty wore Carthage's Defense down by 6, damaging Walls.");
	}

	@Test
	void defenseIsWornDownOneDefensiveBuildingAtATimeInTheOrderTheyWereCompleted() {
		game.act("Carthage", new Action.StartBuilding("Walls"));
		game.endTurn();
		game.act("Carthage", new Action.StartBuilding("Moat"));
		armyOf("Rome", "Warrior", 4);
		arrived("Rome", "Carthage", Map.of("Warrior", 4));

		game.endTurn();

		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.defense()).isEqualTo(2);
		assertThat(carthage.damage()).containsExactly(new BuildingDamage("Walls", 1, 0, 0, 0),
				new BuildingDamage("Moat", 0, 2, 10, 1));
		assertThat(carthage.turnReport().lines())
			.contains("Rome's Warparty wore your Defense down from 14 to 2, destroying Walls ×1 and damaging Moat.");
	}

	@Test
	void onlyStrengthLeftOnceDefenseIsDownReducesPopulation() {
		game.act("Carthage", new Action.StartBuilding("Moat"));
		game.endTurn();
		armyOf("Rome", "Warrior", 4);
		arrived("Rome", "Carthage", Map.of("Warrior", 4));
		int carthagePopulation = game.viewOf("Carthage").population();

		game.endTurn();

		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.defense()).isZero();
		assertThat(carthage.damage()).containsExactly(new BuildingDamage("Moat", 1, 0, 0, 0));
		assertThat(carthage.population()).isEqualTo(carthagePopulation - 8 + 2);
		assertThat(carthage.turnReport().lines()).containsSequence(
				"Rome's Warparty wore your Defense down from 4 to 0, destroying Moat ×1.",
				"Rome's Warparty reduced your Population by 8.");
		assertThat(game.viewOf("Rome").turnReport().lines()).containsSequence(
				"Your Warparty wore Carthage's Defense down by 4, destroying Moat ×1.",
				"Your Warparty reduced Carthage's Population by 8.");
	}

	@Test
	void anArmyThatSurvivesTheBattleKeepsThePopulationLossDown() {
		game.endTurn();
		armyOf("Rome", "Warrior", 2);
		armyOf("Carthage", "Warrior", 2);
		arrived("Rome", "Carthage", Map.of("Warrior", 2));
		int carthagePopulation = game.viewOf("Carthage").population();

		game.endTurn();

		assertThat(game.viewOf("Carthage").population()).isEqualTo(carthagePopulation - 3 + 2);
		assertThat(game.viewOf("Carthage").turnReport().lines())
			.contains("Rome's Warparty reduced your Population by 3.");
		assertThat(game.viewOf("Rome").turnReport().lines()).contains("Your Warparty reduced Carthage's Population by 3.");
	}

	@Test
	void anArmyWipedOutInTheBattleDoublesThePopulationLoss() {
		game.endTurn();
		game.endTurn();
		game.endTurn();
		armyOf("Rome", "Archer", 2);
		armyOf("Carthage", "Warrior", 1);
		arrived("Rome", "Carthage", Map.of("Archer", 2));
		int carthagePopulation = game.viewOf("Carthage").population();

		game.endTurn();

		assertThat(game.viewOf("Carthage").army()).isEmpty();
		assertThat(game.viewOf("Carthage").population()).isEqualTo(carthagePopulation - 12 + 2);
		assertThat(game.viewOf("Carthage").turnReport().lines())
			.contains("Rome's Warparty reduced your Population by 12, doubled as your Army was wiped out.");
		assertThat(game.viewOf("Rome").turnReport().lines())
			.contains("Your Warparty reduced Carthage's Population by 12, doubled as Carthage's Army was wiped out.");
	}

	@Test
	void populationIsNotReducedBelowZero() {
		UnitType giant = new UnitType("Giant", 10, 1, 1, 1, 100, List.of());
		game = new Game(new Ruleset(List.of(giant), List.of(), List.of()), List.of("Rome", "Carthage"), EVEN_LUCK);
		armyOf("Rome", "Giant", 1);
		arrived("Rome", "Carthage", Map.of("Giant", 1));
		int carthagePopulation = game.viewOf("Carthage").population();

		game.endTurn();

		assertThat(game.viewOf("Carthage").population()).isZero();
		assertThat(game.viewOf("Carthage").turnReport().lines()).contains("Rome's Warparty reduced your Population by "
				+ carthagePopulation + ", doubled as you had no Army or Defense.");
	}

	@Test
	void aDestroyedBuildingAddsNothingUntilRebuiltButADamagedOneStillDoes() {
		BuildingType fort = new BuildingType("Fort", 20, 1, 1, 0, 3);
		UnitType spearman = new UnitType("Spearman", 10, 1, 1, 1, 2.5, List.of());
		game = new Game(new Ruleset(List.of(spearman), List.of(fort), List.of()), List.of("Rome", "Carthage"),
				EVEN_LUCK);
		game.act("Carthage", new Action.StartBuilding("Fort"));
		game.act("Carthage", new Action.StartBuilding("Fort"));
		armyOf("Rome", "Spearman", 2);
		arrived("Rome", "Carthage", Map.of("Spearman", 2));
		int carthagePopulation = game.viewOf("Carthage").population();

		game.endTurn();

		assertThat(game.viewOf("Carthage").damage()).containsExactly(new BuildingDamage("Fort", 1, 2, 14, 1));
		assertThat(game.viewOf("Carthage").population()).isEqualTo(carthagePopulation + 2 + 1);
		assertThat(game.viewOf("Carthage").turnReport().lines()).contains("Your Buildings added 1 Population.");
	}

	/**
	 * Has Rome's Warparty of Warrior ×2 wear 6 Defense off Carthage's Walls,
	 * which has 10.
	 */
	void wallsDamaged() {
		game.act("Carthage", new Action.StartBuilding("Walls"));
		armyOf("Rome", "Warrior", 2);
		arrived("Rome", "Carthage", Map.of("Warrior", 2));
		game.endTurn();
	}

	@Test
	void repairingADamagedBuildingPaysGoldInProportionToTheDefenseItLostAndTakesTurnsInProportionToo() {
		wallsDamaged();
		assertThat(game.viewOf("Carthage").damage()).containsExactly(new BuildingDamage("Walls", 0, 6, 18, 2));
		int carthageGold = game.viewOf("Carthage").gold();

		ActionResult result = game.act("Carthage", new Action.RepairBuilding("Walls"));

		assertThat(result).isEqualTo(ActionResult.ACCEPTED);
		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.gold()).isEqualTo(carthageGold - 18);
		assertThat(carthage.repairs()).containsExactly(new Repair("Walls", false, 6, 2));
		assertThat(carthage.damage()).isEmpty();
		assertThat(carthage.defense()).isEqualTo(4);

		game.endTurn();
		assertThat(game.viewOf("Carthage").repairs()).containsExactly(new Repair("Walls", false, 6, 1));
		assertThat(game.viewOf("Carthage").defense()).isEqualTo(4);

		game.endTurn();
		assertThat(game.viewOf("Carthage").repairs()).isEmpty();
		assertThat(game.viewOf("Carthage").defense()).isEqualTo(10);
		assertThat(game.viewOf("Carthage").turnReport().lines()).contains("Your Walls has been repaired.");
	}

	@Test
	void rebuildingADestroyedBuildingCostsWhatConstructingItDoesAndEachIsRebuiltSeparately() {
		game.act("Carthage", new Action.StartBuilding("Moat"));
		game.endTurn();
		game.act("Carthage", new Action.StartBuilding("Moat"));
		armyOf("Rome", "Warrior", 4);
		arrived("Rome", "Carthage", Map.of("Warrior", 4));
		game.endTurn();
		assertThat(game.viewOf("Carthage").damage()).containsExactly(new BuildingDamage("Moat", 2, 0, 0, 0));
		game.endTurn();
		game.endTurn();
		game.endTurn();
		int carthageGold = game.viewOf("Carthage").gold();

		assertThat(game.act("Carthage", new Action.RebuildBuilding("Moat"))).isEqualTo(ActionResult.ACCEPTED);
		assertThat(game.viewOf("Carthage").damage()).containsExactly(new BuildingDamage("Moat", 1, 0, 0, 0));
		assertThat(game.act("Carthage", new Action.RebuildBuilding("Moat"))).isEqualTo(ActionResult.ACCEPTED);

		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.gold()).isEqualTo(carthageGold - 40);
		assertThat(carthage.repairs()).containsExactly(new Repair("Moat", true, 4, 1), new Repair("Moat", true, 4, 1));
		assertThat(carthage.damage()).isEmpty();

		game.endTurn();
		assertThat(game.viewOf("Carthage").defense()).isEqualTo(8);
		assertThat(game.viewOf("Carthage").turnReport().lines()).contains("Your Moat has been rebuilt.",
				"Your Moat has been rebuilt.");
	}

	@Test
	void aRepairRestoresOnlyTheDefenseItWasPaidForEvenIfMoreIsLostMeanwhile() {
		game.act("Carthage", new Action.StartBuilding("Walls"));
		armyOf("Rome", "Warrior", 3);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		game.endTurn();
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 1)));
		game.endTurn();
		game.endTurn();
		game.endTurn();
		assertThat(game.viewOf("Carthage").defense()).isEqualTo(4);
		game.act("Carthage", new Action.RepairBuilding("Walls"));

		game.endTurn();
		assertThat(game.viewOf("Carthage").defense()).isEqualTo(1);

		game.endTurn();
		assertThat(game.viewOf("Carthage").defense()).isEqualTo(7);
		assertThat(game.viewOf("Carthage").damage()).containsExactly(new BuildingDamage("Walls", 0, 3, 9, 1));
	}

	@Test
	void onlyDamagedBuildingsCanBeRepairedAndDestroyedOnesRebuiltWhileTheNationCanAffordIt() {
		wallsDamaged();
		while (game.viewOf("Carthage").gold() >= 20) {
			game.act("Carthage", new Action.StartBuilding("Moat"));
		}
		if (game.viewOf("Carthage").gold() >= 18) {
			game.act("Carthage", new Action.TrainUnits("Scout", 1));
		}
		int carthageGold = game.viewOf("Carthage").gold();

		assertThat(game.act("Carthage", new Action.RepairBuilding("Pyramid")))
			.isEqualTo(ActionResult.rejected("There is no Building called Pyramid."));
		assertThat(game.act("Carthage", new Action.RebuildBuilding("Pyramid")))
			.isEqualTo(ActionResult.rejected("There is no Building called Pyramid."));
		assertThat(game.act("Carthage", new Action.RepairBuilding("Moat")))
			.isEqualTo(ActionResult.rejected("You have no damaged Moat to repair."));
		assertThat(game.act("Carthage", new Action.RebuildBuilding("Walls")))
			.isEqualTo(ActionResult.rejected("You have no destroyed Walls to rebuild."));
		assertThat(game.act("Carthage", new Action.RepairBuilding("Walls")))
			.isEqualTo(ActionResult.rejected("Repairing Walls costs 18 Gold, but you have only " + carthageGold + "."));
		assertThat(game.viewOf("Carthage").gold()).isEqualTo(carthageGold);
		assertThat(game.viewOf("Carthage").repairs()).isEmpty();
	}

	@Test
	void aBuildingAlreadyBeingRepairedCannotBeRepairedAgain() {
		wallsDamaged();
		game.act("Carthage", new Action.RepairBuilding("Walls"));

		assertThat(game.act("Carthage", new Action.RepairBuilding("Walls")))
			.isEqualTo(ActionResult.rejected("You have no damaged Walls to repair."));
	}

	@Test
	void unitsAbroadInAWarpartyStillCostUpkeep() {
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		assertThat(game.viewOf("Rome").upkeep()).isEqualTo(2);

		game.endTurn();

		assertThat(game.viewOf("Rome").gold()).isEqualTo(38 + 10 - 2);
		assertThat(game.viewOf("Rome").turnReport().lines()).contains("Your Units' upkeep cost 2 Gold.");
	}

	@Test
	void unitsCanDesertFromAWarpartyAbroadWhichDisappearsOnceItHasNoUnitsLeft() {
		game.act("Rome", new Action.TrainUnits("Warrior", 1));
		game.act("Rome", new Action.TrainUnits("Horseman", 2));
		game.endTurn();
		game.endTurn();
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Horseman", 1)));
		game.endTurn();
		game.endTurn();
		assertThat(game.viewOf("Rome").gold()).isZero();

		game.endTurn();

		NationView rome = game.viewOf("Rome");
		assertThat(rome.army()).containsExactly(entry("Warrior", 1), entry("Horseman", 1));
		assertThat(rome.warparties()).isEmpty();
		assertThat(rome.gold()).isEqualTo(10 - 7);
		assertThat(rome.turnReport().lines())
			.contains("You could not pay your Units' upkeep, so Horseman ×1 deserted from your Warparty sent to Carthage.");
	}

	static final UnitType GIANT = new UnitType("Giant", 10, 1, 1, 1, 100, List.of());

	/**
	 * A Game in which a Giant is strong enough to conquer any Nation it
	 * reaches.
	 */
	void gameWithGiants(String... nations) {
		UnitType warrior = new UnitType("Warrior", 10, 3, 1, 1, 3, List.of());
		game = new Game(new Ruleset(List.of(warrior, GIANT), List.of(), List.of()), List.of(nations), EVEN_LUCK);
	}

	@Test
	void aNationLeftWithNoPopulationAndNoArmyAfterABattleIsConqueredEvenWithAWarpartyAbroad() {
		gameWithGiants("Rome", "Carthage", "Egypt");
		game.act("Rome", new Action.TrainUnits("Giant", 1));
		game.act("Carthage", new Action.TrainUnits("Warrior", 1));
		game.endTurn();
		game.act("Carthage", new Action.SendWarparty("Egypt", Map.of("Warrior", 1)));
		arrived("Rome", "Carthage", Map.of("Giant", 1));
		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.warparties()).hasSize(1);
		int carthagePopulation = carthage.population();
		int romeGold = game.viewOf("Rome").gold();
		int romeResearch = game.viewOf("Rome").research();
		int seizedGold = carthage.gold() * 3 / 4;
		int seizedResearch = carthage.research() * 3 / 4;
		String seized = seizedGold + " Gold and " + seizedResearch + " Research";

		game.endTurn();

		carthage = game.viewOf("Carthage");
		assertThat(carthage.conqueredBy()).isEqualTo("Rome");
		assertThat(carthage.population()).isZero();
		assertThat(carthage.warparties()).isEmpty();
		assertThat(carthage.turnReport().lines()).containsExactly("Rome's Warparty of Giant ×1 met no Army at your Nation.",
				"Rome's Warparty reduced your Population by " + carthagePopulation + ", doubled as you had no Army or Defense.",
				"Rome's Warparty conquered your Nation, seizing " + seized + ". You are knocked out of the Game.");
		NationView rome = game.viewOf("Rome");
		assertThat(rome.gold()).isEqualTo(romeGold + seizedGold + 10 - 1);
		assertThat(rome.research()).isEqualTo(romeResearch + seizedResearch + 3);
		assertThat(rome.warparties()).containsExactly(new Warparty("Carthage", Map.of("Giant", 1), true, 3));
		assertThat(rome.turnReport().lines())
			.contains("Your Warparty conquered Carthage, seizing " + seized + ", and is heading home.");
		assertThat(rome.otherNations()).containsExactly("Egypt");
		NationView egypt = game.viewOf("Egypt");
		assertThat(egypt.turnReport().lines()).containsExactly("Rome conquered Carthage.", BASE_INCOME_REPORT);
		assertThat(egypt.otherNations()).containsExactly("Rome");
		assertThat(egypt.winner()).isNull();
	}

	@Test
	void aWarpartyWipedOutInTheBattleCannotConquerEvenANationLeftWithNoPopulationAndNoArmy() {
		UnitType warrior = new UnitType("Warrior", 10, 3, 1, 1, 3, List.of());
		UnitType recruit = new UnitType("Recruit", 0, 1, 20, 0, 1, List.of());
		game = new Game(new Ruleset(List.of(warrior, recruit), List.of(), List.of()), List.of("Rome", "Carthage", "Egypt"),
				EVEN_LUCK);
		game.act("Rome", new Action.TrainUnits("Warrior", 1));
		game.act("Carthage", new Action.TrainUnits("Warrior", 1));
		game.endTurn();
		arrived("Rome", "Carthage", Map.of("Warrior", 1));
		game.act("Carthage", new Action.TrainUnits("Recruit", game.viewOf("Carthage").population()));
		assertThat(game.viewOf("Carthage").population()).isZero();

		game.endTurn();

		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.army()).isEmpty();
		assertThat(carthage.conqueredBy()).isNull();
		assertThat(game.conquered()).isEmpty();
		assertThat(carthage.turnReport().lines()).containsExactly(
				"Your Army of Warrior ×1 fought Rome's Warparty of Warrior ×1. You lost Warrior ×1 and Rome lost Warrior ×1.",
				"Rome's Warparty was destroyed.", BASE_INCOME_REPORT);
		assertThat(game.viewOf("Rome").warparties()).isEmpty();
		assertThat(game.viewOf("Rome").otherNations()).containsExactly("Carthage", "Egypt");
	}

	@Test
	void warpartiesHeadingToAConqueredNationTurnBackHomeTakingAsLongAsTheyHaveTravelled() {
		gameWithGiants("Rome", "Carthage", "Egypt");
		game.act("Rome", new Action.TrainUnits("Giant", 1));
		game.act("Egypt", new Action.TrainUnits("Warrior", 2));
		game.endTurn();
		game.act("Egypt", new Action.SendWarparty("Carthage", Map.of("Warrior", 1)));
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Giant", 1)));
		game.endTurn();
		game.act("Egypt", new Action.SendWarparty("Carthage", Map.of("Warrior", 1)));
		game.endTurn();
		game.endTurn();
		assertThat(game.viewOf("Egypt").warparties()).containsExactly(
				new Warparty("Carthage", Map.of("Warrior", 1), false, 0),
				new Warparty("Carthage", Map.of("Warrior", 1), false, 1));

		game.endTurn();

		NationView egypt = game.viewOf("Egypt");
		assertThat(egypt.warparties()).containsExactly(new Warparty("Carthage", Map.of("Warrior", 1), true, 3),
				new Warparty("Carthage", Map.of("Warrior", 1), true, 2));
		assertThat(egypt.turnReport().lines()).containsExactly("Rome conquered Carthage.",
				"Your Warparty of Warrior ×1 heading to Carthage turned back home, as Carthage was conquered.",
				"Your Warparty of Warrior ×1 heading to Carthage turned back home, as Carthage was conquered.",
				BASE_INCOME_REPORT, "Your Units' upkeep cost 2 Gold.");

		game.endTurn();
		game.endTurn();

		egypt = game.viewOf("Egypt");
		assertThat(egypt.army()).containsExactly(entry("Warrior", 1));
		assertThat(egypt.warparties()).containsExactly(new Warparty("Carthage", Map.of("Warrior", 1), true, 1));
	}

	@Test
	void aConqueredNationTakesNoFurtherPartAndCannotBeAttacked() {
		gameWithGiants("Rome", "Carthage", "Egypt");
		game.act("Rome", new Action.TrainUnits("Giant", 2));
		game.endTurn();
		arrived("Rome", "Carthage", Map.of("Giant", 1));
		game.endTurn();
		NationView conquered = game.viewOf("Carthage");

		game.endTurn();

		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.version()).isEqualTo(conquered.version());
		assertThat(carthage.gold()).isEqualTo(conquered.gold());
		assertThat(carthage.turnReport()).isEqualTo(conquered.turnReport());
		assertThat(game.viewOf("Egypt").turnReport().lines()).containsExactly(BASE_INCOME_REPORT);
		assertThat(game.nations()).containsExactly("Rome", "Carthage", "Egypt");
		assertThat(game.conquered()).containsExactly("Carthage");
		assertThat(game.act("Carthage", new Action.TrainUnits("Warrior", 1)))
			.isEqualTo(ActionResult.rejected("Your Nation has been conquered."));
		assertThat(game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Giant", 1))))
			.isEqualTo(ActionResult.rejected("Carthage has been conquered."));
	}

	@Test
	void whenOnlyOneNationRemainsItWinsTheGameAndEveryPlayerSeesIt() {
		gameWithGiants("Rome", "Carthage", "Egypt");
		game.act("Rome", new Action.TrainUnits("Giant", 2));
		game.endTurn();
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Giant", 1)));
		game.endTurn();
		arrived("Rome", "Egypt", Map.of("Giant", 1));
		assertThat(game.conquered()).containsExactly("Carthage");
		assertThat(game.winner()).isEmpty();
		int carthageVersion = game.viewOf("Carthage").version();

		game.endTurn();

		assertThat(game.winner()).contains("Rome");
		NationView rome = game.viewOf("Rome");
		assertThat(rome.winner()).isEqualTo("Rome");
		assertThat(rome.turnReport().lines()).last().isEqualTo("You have won the Game.");
		NationView egypt = game.viewOf("Egypt");
		assertThat(egypt.winner()).isEqualTo("Rome");
		assertThat(egypt.turnReport().lines()).last().isEqualTo("Rome has won the Game.");
		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.winner()).isEqualTo("Rome");
		assertThat(carthage.version()).isGreaterThan(carthageVersion);
	}

	@Test
	void onceTheGameIsWonTurnsNoLongerEndAndNoActionIsAccepted() {
		gameWithGiants("Rome", "Carthage");
		game.act("Rome", new Action.TrainUnits("Giant", 1));
		game.endTurn();
		arrived("Rome", "Carthage", Map.of("Giant", 1));
		game.endTurn();
		NationView rome = game.viewOf("Rome");

		game.endTurn();

		assertThat(game.viewOf("Rome")).isEqualTo(rome);
		assertThat(game.act("Rome", new Action.TrainUnits("Warrior", 1)))
			.isEqualTo(ActionResult.rejected("The Game is over."));
	}

	@Test
	void thereIsNoViewOfANationNotInTheGame() {
		assertThatIllegalArgumentException().isThrownBy(() -> game.viewOf("Persia"));
	}

}
