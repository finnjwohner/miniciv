package com.miniciv.engine;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class GameTest {

	Game game = new Game(List.of("Rome", "Carthage", "Egypt"), new Random(42));

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

		assertThat(game.viewOf("Carthage")).isEqualTo(new NationView(2, "Carthage", 60, 12, 3, List.of("Rome", "Egypt"),
				new TurnReport(1, List.of("Your Nation gained 10 Gold, 2 Population and 3 Research."))));
	}

	@Test
	void thereIsNoViewOfANationNotInTheGame() {
		assertThatIllegalArgumentException().isThrownBy(() -> game.viewOf("Persia"));
	}

}
