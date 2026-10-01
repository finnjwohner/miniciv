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

	static final Ruleset RULESET = new Ruleset(List.of(), List.of(FARM, GRANARY, BANK), List.of());

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
				List.of(FARM, GRANARY, BANK), Map.of(), List.of(),
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
	void thereIsNoViewOfANationNotInTheGame() {
		assertThatIllegalArgumentException().isThrownBy(() -> game.viewOf("Persia"));
	}

}
