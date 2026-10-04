package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.EventsTest.ALWAYS;
import static com.miniciv.engine.GameTest.BASE_INCOME_REPORT;
import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.RULESET;
import static com.miniciv.engine.Resource.GOLD;
import static com.miniciv.engine.Resource.POPULATION;
import static com.miniciv.engine.Resource.RESEARCH;
import static com.miniciv.engine.TurnReport.Amount.Effect.GAIN;
import static com.miniciv.engine.TurnReport.Amount.Effect.LOSS;
import static com.miniciv.engine.TurnReport.Amount.Effect.NEUTRAL;
import static org.assertj.core.api.Assertions.assertThat;

class TurnReportAmountsTest {

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage"), EVEN_LUCK);

	/**
	 * The line of a Nation's Turn Report that reads as given.
	 */
	TurnReport.Line line(String nation, String text) {
		return game.viewOf(nation)
			.turnReport()
			.sections()
			.stream()
			.flatMap(section -> section.lines().stream())
			.filter(line -> line.text().equals(text))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No line reads: " + text));
	}

	void armyOf(String nation, String unit, int count) {
		assertThat(game.act(nation, new Action.TrainUnits(unit, count))).isEqualTo(ActionResult.ACCEPTED);
		while (game.viewOf(nation).army().getOrDefault(unit, 0) < count) {
			game.endTurn();
		}
	}

	@Test
	void incomeShowsAsAGainAndLeavesTheRestOfTheLinePlain() {
		game.endTurn();

		assertThat(line("Rome", BASE_INCOME_REPORT).parts()).containsExactly(
				new TurnReport.Text("Your Population earned "), new TurnReport.Amount(GOLD, 10, GAIN),
				new TurnReport.Text(", and your Nation gained "), new TurnReport.Amount(POPULATION, 2, GAIN),
				new TurnReport.Text(" and "), new TurnReport.Amount(RESEARCH, 3, GAIN), new TurnReport.Text("."));
	}

	@Test
	void upkeepShowsAsALoss() {
		armyOf("Rome", "Warrior", 1);

		assertThat(line("Rome", "Your Units' upkeep cost 1 Gold.").amounts())
			.containsExactly(new TurnReport.Amount(GOLD, 1, LOSS));
	}

	@Test
	void payingForAnEventsRepairsShowsAsALoss() {
		Event masons = new Event("Masons", Event.Subject.NATION, 1, null, new Event.Effects(-20, 0, 0, 0, 0),
				"You paid the masons to shore up your defensive Buildings");
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(), List.of(masons)),
				Identities.of("Rome", "Carthage"), ALWAYS);

		game.endTurn();

		assertThat(line("Rome", "You paid the masons to shore up your defensive Buildings: your Nation lost 18 Gold.")
			.amounts()).containsExactly(new TurnReport.Amount(GOLD, 18, LOSS));
	}

	@Test
	void plunderIsAGainForTheAttackerAndALossForTheDefender() {
		game = Locating.everyone(RULESET, Identities.of("Rome", "Carthage"), EVEN_LUCK);
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		while (game.viewOf("Rome").warparties().getFirst().turnsLeft() > 0) {
			game.endTurn();
		}

		game.endTurn();

		assertThat(line("Rome", "Your Warparty took 18 Gold and 6 Research from Carthage and is heading home.")
			.amounts()).containsExactly(new TurnReport.Amount(GOLD, 18, GAIN), new TurnReport.Amount(RESEARCH, 6, GAIN));
		assertThat(line("Carthage", "Caesar of Rome's Warparty took 18 Gold and 6 Research from you and is heading home.")
			.amounts()).containsExactly(new TurnReport.Amount(GOLD, 18, LOSS), new TurnReport.Amount(RESEARCH, 6, LOSS));
		assertThat(line("Carthage",
				"Caesar of Rome's Warparty cost your Nation 12 Population, doubled as you had no Army or Defense.")
			.amounts()).containsExactly(new TurnReport.Amount(POPULATION, 12, LOSS));
		assertThat(line("Rome", "Your Warparty cost Carthage 12 Population, doubled as Carthage had no Army or Defense.")
			.amounts()).containsExactly(new TurnReport.Amount(POPULATION, 12, NEUTRAL));
	}

}
