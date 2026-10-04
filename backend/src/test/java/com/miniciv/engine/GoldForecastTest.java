package com.miniciv.engine;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.RULESET;
import static org.assertj.core.api.Assertions.assertThat;

class GoldForecastTest {

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage"), EVEN_LUCK);

	@Test
	void aNewNationIsForecastItsPopulationsIncomeAndNoUpkeep() {
		assertThat(game.viewOf("Rome").goldForecast()).isEqualTo(new GoldForecast(10, 0));
	}

	@Test
	void theForecastIsTheGoldChangeResolutionProducesWithBuildingsAndUnitsFinishingThisTurn() {
		game.act("Rome", new Action.StartBuilding("Bank"));
		game.act("Rome", new Action.TrainUnits("Scout", 1));
		assertThat(game.viewOf("Rome").goldForecast()).isEqualTo(new GoldForecast(9 + 4, 1));
		assertForecastMatchesResolution();

		game.act("Rome", new Action.TrainUnits("Warrior", 1));
		game.act("Rome", new Action.TrainUnits("Archer", 1));
		assertForecastMatchesResolution();
		assertForecastMatchesResolution();
	}

	@Test
	void theForecastCountsUnitsDesertingWhenUpkeepIsUnaffordable() {
		game.act("Rome", new Action.TrainUnits("Warrior", 1));
		game.act("Rome", new Action.TrainUnits("Horseman", 2));
		game.endTurn();

		GoldForecast forecast = game.viewOf("Rome").goldForecast();
		assertThat(forecast).isEqualTo(new GoldForecast(5, 7));
		assertForecastMatchesResolution();
	}

	@Test
	void trainingAUnitThatFinishesThisTurnLowersTheForecastByItsUpkeepAndThePopulationSpent() {
		GoldForecast before = game.viewOf("Rome").goldForecast();

		game.act("Rome", new Action.TrainUnits("Warrior", 2));

		GoldForecast after = game.viewOf("Rome").goldForecast();
		assertThat(after.upkeep()).isEqualTo(before.upkeep() + 2);
		assertThat(after.income()).isEqualTo(before.income() - 2 * GameTest.WARRIOR.population());
		assertThat(after.change()).isEqualTo(10 - 6 - 2);
	}

	@Test
	void trainingAUnitThatFinishesLaterAddsNoUpkeepToTheForecast() {
		game.act("Rome", new Action.TrainUnits("Archer", 1));

		assertThat(game.viewOf("Rome").goldForecast().upkeep()).isZero();
	}

	private void assertForecastMatchesResolution() {
		NationView before = game.viewOf("Rome");
		int forecast = before.goldForecast().change();
		game.endTurn();
		assertThat(game.viewOf("Rome").gold() - before.gold()).isEqualTo(forecast);
	}

}
