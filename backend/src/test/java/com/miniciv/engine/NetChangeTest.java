package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.EventsTest.ALWAYS;
import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.RULESET;
import static org.assertj.core.api.Assertions.assertThat;

class NetChangeTest {

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage"), EVEN_LUCK);

	TurnReport.NetChange netChange(String nation) {
		return game.viewOf(nation).turnReport().netChange();
	}

	void armyOf(String nation, String unit, int count) {
		assertThat(game.act(nation, new Action.TrainUnits(unit, count))).isEqualTo(ActionResult.ACCEPTED);
		while (game.viewOf(nation).army().getOrDefault(unit, 0) < count) {
			game.endTurn();
		}
	}

	void arrived(String nation, String target, Map<String, Integer> units) {
		assertThat(game.act(nation, new Action.SendWarparty(target, units))).isEqualTo(ActionResult.ACCEPTED);
		while (game.viewOf(nation).warparties().getLast().turnsLeft() > 0) {
			game.endTurn();
		}
	}

	@Test
	void goldSpentDuringTheTurnDoesNotCountSoTheNetGoldChangeIsIncomeLessUpkeep() {
		armyOf("Rome", "Warrior", 2);
		assertThat(game.act("Rome", new Action.StartBuilding("Farm"))).isEqualTo(ActionResult.ACCEPTED);
		NationView before = game.viewOf("Rome");

		game.endTurn();

		NationView rome = game.viewOf("Rome");
		assertThat(rome.turnReport().lines()).contains("Your Units' upkeep cost 2 Gold.");
		assertThat(netChange("Rome")).isEqualTo(new TurnReport.NetChange(before.population() - 2, 2, 3));
		assertThat(netChange("Rome")).isEqualTo(new TurnReport.NetChange(rome.gold() - before.gold(),
				rome.population() - before.population(), rome.research() - before.research()));
	}

	@Test
	void plunderCarriedHomeCountsForTheNationItJoins() {
		armyOf("Rome", "Warrior", 2);
		arrived("Rome", "Carthage", Map.of("Warrior", 2));
		while (!game.viewOf("Rome").warparties().getFirst().homeward()
				|| game.viewOf("Rome").warparties().getFirst().turnsLeft() > 1) {
			game.endTurn();
		}
		int population = game.viewOf("Rome").population();

		game.endTurn();

		assertThat(game.viewOf("Rome").warparties()).isEmpty();
		assertThat(netChange("Rome")).isEqualTo(new TurnReport.NetChange(18 + population - 2, 2, 6 + 3));
	}

	@Test
	void plunderTakenAndPopulationLostInABattleCountAgainstTheDefender() {
		armyOf("Rome", "Warrior", 2);
		arrived("Rome", "Carthage", Map.of("Warrior", 2));
		int population = game.viewOf("Carthage").population();

		game.endTurn();

		assertThat(netChange("Carthage"))
			.isEqualTo(new TurnReport.NetChange(-18 + (population - 12), -12 + 2, -6 + 3));
	}

	@Test
	void anEventsEffectCounts() {
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(),
				List.of(EventsTest.starter("Good harvest"))), Identities.of("Rome", "Carthage"), ALWAYS);
		game.endTurn();

		game.endTurn();

		assertThat(game.viewOf("Rome").turnReport().lines())
			.contains("A good harvest filled your granaries: your Nation gained 3 Population.");
		assertThat(netChange("Rome")).isEqualTo(new TurnReport.NetChange(12, 2 + 3, 3));
	}

	@Test
	void tributeAcceptedDuringTheTurnDoesNotCountForEitherSide() {
		armyOf("Rome", "Warrior", 2);
		arrived("Rome", "Carthage", Map.of("Warrior", 2));
		game.act("Carthage", new Action.OfferTribute("Rome", 1, 20, 3));
		assertThat(game.act("Rome", new Action.AnswerTribute(1, true))).isEqualTo(ActionResult.ACCEPTED);
		int romePopulation = game.viewOf("Rome").population();
		int carthagePopulation = game.viewOf("Carthage").population();

		game.endTurn();

		assertThat(netChange("Rome")).isEqualTo(new TurnReport.NetChange(romePopulation - 2, 2, 3));
		assertThat(netChange("Carthage")).isEqualTo(new TurnReport.NetChange(carthagePopulation, 2, 3));
	}

}
