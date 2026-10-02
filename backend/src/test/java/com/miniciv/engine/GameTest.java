package com.miniciv.engine;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.entry;

class GameTest {

	static final BuildingType FARM = new BuildingType("Farm", 30, 2, 1, 0);

	static final BuildingType GRANARY = new BuildingType("Granary", 40, 1, 2, 0);

	static final BuildingType BANK = new BuildingType("Bank", 45, 1, 0, 0.5);

	static final UnitType WARRIOR = new UnitType("Warrior", 10, 3, 1, 1, 3, List.of("Horseman"));

	static final UnitType ARCHER = new UnitType("Archer", 15, 1, 2, 1, 3, List.of("Warrior"));

	static final UnitType HORSEMAN = new UnitType("Horseman", 20, 2, 2, 6, 4, List.of("Archer"));

	static final UnitType SCOUT = new UnitType("Scout", 5, 1, 1, 1, 0, List.of());

	static final Ruleset RULESET = new Ruleset(List.of(WARRIOR, ARCHER, HORSEMAN, SCOUT), List.of(FARM, GRANARY, BANK),
			List.of());

	static final String BASE_INCOME_REPORT = "Your Nation gained 10 Gold, 2 Population and 3 Research.";

	Game game = new Game(RULESET, List.of("Rome", "Carthage", "Egypt"), new Random(42));

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
				List.of(FARM, GRANARY, BANK), Map.of(), List.of(), List.of(WARRIOR, ARCHER, HORSEMAN), Map.of(), List.of(),
				List.of(), 3, 0,
				new TurnReport(1, List.of("Your Nation gained 10 Gold, 2 Population and 3 Research."))));
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
		assertThat(game.viewOf("Rome").availableBuildings()).containsExactly(FARM, GRANARY, BANK);
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
		assertThat(game.act("Rome", new Action.TrainUnits("Scout", 1)))
			.isEqualTo(ActionResult.rejected("There is no Unit called Scout that you can train."));
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
		assertThat(game.viewOf("Rome").availableUnits()).containsExactly(WARRIOR, ARCHER, HORSEMAN);
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
		assertThat(rome.warparties()).containsExactly(new Warparty("Carthage", Map.of("Warrior", 2), true, 3));
		assertThat(rome.turnReport().lines())
			.contains("Your Warparty of Warrior ×2 arrived at Carthage and is heading home.");
		assertThat(game.viewOf("Carthage").turnReport().lines()).containsExactly(
				"A Warparty from Rome arrived at your Nation.", BASE_INCOME_REPORT);
		assertThat(game.viewOf("Egypt").turnReport().lines()).containsExactly(BASE_INCOME_REPORT);
	}

	@Test
	void anArrivedWarpartyComesHomeOverTheSameTravelTimeAndRejoinsTheArmy() {
		armyOf("Rome", "Warrior", 3);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		for (int turn = 1; turn <= 5; turn++) {
			game.endTurn();
		}
		assertThat(game.viewOf("Rome").warparties()).containsExactly(new Warparty("Carthage", Map.of("Warrior", 2), true, 1));
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Warrior", 1));

		game.endTurn();

		NationView rome = game.viewOf("Rome");
		assertThat(rome.warparties()).isEmpty();
		assertThat(rome.army()).containsExactly(entry("Warrior", 3));
		assertThat(rome.turnReport().lines())
			.contains("Your Warparty came home from Carthage and Warrior ×2 rejoined your Army.");
		assertThat(game.viewOf("Carthage").turnReport().lines()).containsExactly(BASE_INCOME_REPORT);
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

	@Test
	void thereIsNoViewOfANationNotInTheGame() {
		assertThatIllegalArgumentException().isThrownBy(() -> game.viewOf("Persia"));
	}

}
