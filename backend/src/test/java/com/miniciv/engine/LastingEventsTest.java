package com.miniciv.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.RULESET;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Events whose change applies again each Turn for some Turns.
 */
class LastingEventsTest {

	Game game;

	void play(RandomGenerator luck, Event... events) {
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(), List.of(events)),
				Identities.of("Rome", "Carthage"), luck);
	}

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	static Event.Effects lasting(Event.Lasting lasting) {
		return new Event.Effects(0, 0, 0, 0, 0, null, 0, null, 0, lasting);
	}

	static final Event DROUGHT = new Event("Drought", Event.Subject.NATION, 1, null,
			lasting(new Event.Lasting(3, 0, 0, 0, true)), "A drought parched your fields");

	static final Event TAX_REVOLT = new Event("Tax revolt", Event.Subject.NATION, 1, null,
			lasting(new Event.Lasting(2, -5, 0, 0, false)), "Your tax collectors were driven out");

	@Test
	void aLastingEventAppliesItsChangeOnEachOfItsTurnsAndThenStops() {
		play(EVEN_LUCK, DROUGHT);

		game.endTurn();

		assertThat(report("Rome"))
			.contains("A drought parched your fields: for 3 Turns, your Nation will have no Population growth.");
		assertThat(game.viewOf("Rome").population()).isEqualTo(12);
		assertThat(game.viewOf("Rome").lastingEvent()).isEqualTo(new LastingEvent("Drought", 3, 0, 0, 0, true));

		for (int turnsLeft = 2; turnsLeft >= 0; turnsLeft--) {
			game.endTurn();

			assertThat(game.viewOf("Rome").population()).isEqualTo(12);
			assertThat(report("Rome")).contains("Drought: no Population growth.",
					"Your Population earned 12 Gold, and your Nation gained 3 Research.");
			assertThat(game.viewOf("Rome").lastingEvent())
				.isEqualTo(turnsLeft == 0 ? null : new LastingEvent("Drought", turnsLeft, 0, 0, 0, true));
		}
		assertThat(report("Rome")).contains("Drought has ended.");

		game.endTurn();

		assertThat(game.viewOf("Rome").population()).isEqualTo(14);
		assertThat(report("Rome")).noneMatch(line -> line.startsWith("Drought"));
	}

	@Test
	void aLastingEventCanChangeGoldEachTurn() {
		play(EVEN_LUCK, TAX_REVOLT);
		game.endTurn();
		int gold = game.viewOf("Rome").gold();

		game.endTurn();

		assertThat(game.viewOf("Rome").gold()).isEqualTo(gold + 12 - 5);
		assertThat(report("Rome")).contains("Tax revolt: your Nation lost 5 Gold.");
		assertThat(game.viewOf("Rome").lastingEvent()).isEqualTo(new LastingEvent("Tax revolt", 1, -5, 0, 0, false));
	}

	@Test
	void noLastingEventBeginsWhileOneIsActiveOrForThreeTurnsAfterItEnds() {
		play(EVEN_LUCK, TAX_REVOLT, DROUGHT);

		List<String> begun = new ArrayList<>();
		for (int turn = 1; turn <= 12; turn++) {
			game.endTurn();
			for (String line : report("Rome")) {
				if (line.startsWith("Your tax collectors") || line.startsWith("A drought")) {
					begun.add(turn + " " + line.substring(0, 5));
				}
			}
		}

		// The Tax revolt applies as Turns 2 and 3 resolve, ending as Turn 3
		// does, so neither can begin again before Turn 7, nor before 13 once
		// the Tax revolt begins again then.
		assertThat(begun).containsExactly("1 Your ", "7 Your ");
	}

	@Test
	void aDilemmaOutcomeBlockedFromStartingALastingEventStillAppliesItsOtherEffects() {
		Event locusts = new Event("Locusts", Event.Subject.NATION, 1, null, null, "Locusts swarmed over the hills",
				List.of(new Event.Choice("Burn the fields", new Event.Effects(0, 0, 0, 0, 0), false, "You burned the fields"),
						new Event.Choice("Pray", new Event.Effects(20, 0, 0, 0, 0, null, 0, null, 0,
								new Event.Lasting(2, 0, 0, 0, true)), true, "The priests collected offerings")));
		play(EVEN_LUCK, DROUGHT, locusts);
		game.endTurn();
		int gold = game.viewOf("Rome").gold();

		game.endTurn();

		assertThat(report("Rome")).contains("You did not choose in time what to do about Locusts. "
				+ "The priests collected offerings: your Nation gained 20 Gold.");
		assertThat(game.viewOf("Rome").gold()).isEqualTo(gold + 20 + 12);
		assertThat(game.viewOf("Rome").lastingEvent().name()).isEqualTo("Drought");
	}

	@Test
	void aDilemmaOutcomeCanStartALastingEventNamedAfterTheDilemma() {
		Event plague = new Event("Plague of rats", Event.Subject.NATION, 1, null, null, "Rats overran your granaries",
				List.of(new Event.Choice("Hire ratcatchers", new Event.Effects(-5, 0, 0, 0, 0), false, "Ratcatchers came"),
						new Event.Choice("Ignore them", lasting(new Event.Lasting(2, 0, 0, 0, true)), true,
								"The rats ate your grain")));
		play(EVEN_LUCK, plague);
		game.endTurn();

		game.endTurn();

		assertThat(report("Rome")).contains("You did not choose in time what to do about Plague of rats. "
				+ "The rats ate your grain: for 2 Turns, your Nation will have no Population growth.");
		assertThat(game.viewOf("Rome").lastingEvent()).isEqualTo(new LastingEvent("Plague of rats", 2, 0, 0, 0, true));
	}

	@Test
	void theGoldForecastIncludesAnActiveLastingEvent() {
		play(EVEN_LUCK, TAX_REVOLT);
		game.endTurn();

		NationView before = game.viewOf("Rome");
		assertThat(before.goldForecast()).isEqualTo(new GoldForecast(12, 0, -5));
		assertThat(before.goldForecast().change()).isEqualTo(7);

		game.endTurn();

		assertThat(game.viewOf("Rome").gold() - before.gold()).isEqualTo(7);
	}

	@Test
	void resolutionStaysDeterministic() {
		Event bounty = new Event("Bounty", Event.Subject.NATION, 0.3, null,
				lasting(new Event.Lasting(3, 4, 1, 2, false)), "Your harvests overflowed");
		Event drought = new Event("Drought", Event.Subject.NATION, 0.3, null,
				lasting(new Event.Lasting(3, 0, 0, 0, true)), "A drought parched your fields");

		assertThat(played(bounty, drought)).isEqualTo(played(bounty, drought));
	}

	private List<List<String>> played(Event... events) {
		play(new SplittableRandom(7), events);
		List<List<String>> reports = new ArrayList<>();
		for (int turn = 1; turn <= 15; turn++) {
			game.endTurn();
			reports.add(report("Rome"));
			reports.add(report("Carthage"));
		}
		return reports;
	}

}
