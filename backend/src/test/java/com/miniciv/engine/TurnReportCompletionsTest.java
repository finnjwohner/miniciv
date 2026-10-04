package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.RULESET;
import static com.miniciv.engine.TurnReport.Kind.COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

class TurnReportCompletionsTest {

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage"), EVEN_LUCK);

	List<String> completed(String nation) {
		return game.viewOf(nation)
			.turnReport()
			.sections()
			.stream()
			.filter(section -> section.kind() == COMPLETED)
			.flatMap(section -> section.lines().stream().map(TurnReport.Line::text))
			.toList();
	}

	void accepted(String nation, Action action) {
		assertThat(game.act(nation, action)).isEqualTo(ActionResult.ACCEPTED);
	}

	@Test
	void unitsOfOneTypeFinishingOnTheSameTurnShareOneLine() {
		accepted("Rome", new Action.TrainUnits("Archer", 1));
		accepted("Rome", new Action.TrainUnits("Archer", 2));
		game.endTurn();

		game.endTurn();

		assertThat(completed("Rome")).containsExactly("3 Archers finished training and joined your Army.");
	}

	@Test
	void unitsOfDifferentTypesFinishingOnTheSameTurnGetALineEach() {
		accepted("Rome", new Action.TrainUnits("Archer", 1));
		game.endTurn();
		accepted("Rome", new Action.TrainUnits("Warrior", 2));

		game.endTurn();

		assertThat(completed("Rome")).containsExactly("1 Archer finished training and joined your Army.",
				"2 Warriors finished training and joined your Army.");
	}

	@Test
	void buildingsOfOneTypeCompletingOnTheSameTurnShareOneLine() {
		accepted("Rome", new Action.StartBuilding("Moat"));
		accepted("Rome", new Action.StartBuilding("Moat"));

		game.endTurn();

		assertThat(completed("Rome")).containsExactly("Your 2 Moats have been built.");
	}

	@Test
	void aBuildingBuiltAndOneOfTheSameTypeRepairedOnTheSameTurnGetALineEachThatReadsAsBefore() {
		game = Locating.everyone(RULESET, Identities.of("Rome", "Carthage"), EVEN_LUCK);
		accepted("Carthage", new Action.StartBuilding("Walls"));
		accepted("Rome", new Action.TrainUnits("Warrior", 2));
		game.endTurn();
		accepted("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		while (game.viewOf("Rome").warparties().getFirst().turnsLeft() > 0) {
			game.endTurn();
		}
		// The Battle damages the first Walls while the second is under way.
		accepted("Carthage", new Action.StartBuilding("Walls"));
		game.endTurn();

		accepted("Carthage", new Action.RepairBuilding("Walls"));
		game.endTurn();

		assertThat(completed("Carthage")).containsExactly("Your Walls has been built.",
				"Your Walls has been repaired.");
	}

	@Test
	void destroyedBuildingsOfOneTypeRebuiltOnTheSameTurnShareOneLine() {
		game = Locating.everyone(RULESET, Identities.of("Rome", "Carthage"), EVEN_LUCK);
		accepted("Carthage", new Action.StartBuilding("Moat"));
		game.endTurn();
		accepted("Carthage", new Action.StartBuilding("Moat"));
		accepted("Rome", new Action.TrainUnits("Warrior", 4));
		game.endTurn();
		accepted("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 4)));
		while (game.viewOf("Rome").warparties().getFirst().turnsLeft() > 0) {
			game.endTurn();
		}
		game.endTurn();
		while (game.viewOf("Carthage").gold() < 20) {
			game.endTurn();
		}
		assertThat(game.viewOf("Carthage").damage()).singleElement()
			.extracting(BuildingDamage::destroyed)
			.isEqualTo(2);

		accepted("Carthage", new Action.RebuildBuilding("Moat"));
		accepted("Carthage", new Action.RebuildBuilding("Moat"));
		game.endTurn();

		assertThat(completed("Carthage")).containsExactly("Your 2 Moats have been rebuilt.");
	}

	@Test
	void pluralNamesReadNaturally() {
		assertThat(List.of("Archer", "Horseman", "Crossbowman", "Spy", "Library", "Observatory", "Walls", "Scout"))
			.extracting(Game::plural)
			.containsExactly("Archers", "Horsemen", "Crossbowmen", "Spies", "Libraries", "Observatories", "Walls",
					"Scouts");
	}

}
