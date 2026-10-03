package com.miniciv.engine;

import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.EventsTest.ALWAYS;
import static com.miniciv.engine.GameTest.BASE_INCOME_REPORT;
import static com.miniciv.engine.GameTest.incomeReport;
import static com.miniciv.engine.GameTest.RULESET;
import static org.assertj.core.api.Assertions.assertThat;

class DilemmasTest {

	static final Event.Choice QUARANTINE = new Event.Choice("Quarantine", new Event.Effects(-15, -2, 0, 0, 0), false,
			"You quarantined the sick");

	static final Event.Choice LET_IT_SPREAD = new Event.Choice("Let it spread", new Event.Effects(0, -8, 0, 0, 0),
			true, "You let the sickness spread");

	static final Event OUTBREAK = new Event("Outbreak", Event.Subject.NATION, 0.5, null, null,
			"Sickness broke out in your Nation", List.of(QUARANTINE, LET_IT_SPREAD));

	static final Event BANDITS = new Event("Bandits", Event.Subject.NATION, 0.5, null, null,
			"Bandits are raiding your roads",
			List.of(new Event.Choice("Hunt them down", false,
					List.of(new Event.Outcome(0.75, new Event.Effects(0, -3, 0, 0, 0), "Your people hunted the bandits down"),
							new Event.Outcome(0.25, new Event.Effects(0, -6, 0, 0, 0), "The bandits ambushed your hunters"))),
					new Event.Choice("Ignore them", true,
							List.of(new Event.Outcome(0.8, new Event.Effects(-30, 0, 0, 0, 0),
									"You ignored the bandits and they plundered freely"),
									new Event.Outcome(0.2, new Event.Effects(10, 0, 0, 0, 0),
											"You ignored the bandits, who fell out and left their loot behind")))));

	Game game;

	void play(RandomGenerator luck, Event... events) {
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(), List.of(events)),
				Identities.of("Rome", "Carthage"), luck);
	}

	/**
	 * Luck that makes every Event happen for the given number of draws, then
	 * draws an even 0.5, which no Event with a chance of a half or less happens
	 * on and which leaves effects as authored.
	 */
	static RandomGenerator firstDraws(int draws) {
		int[] drawn = { 0 };
		return () -> drawn[0]++ < draws ? 0 : Long.MIN_VALUE;
	}

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	@Test
	void aDilemmaIsAnnouncedAndAwaitsItsPlayersChoiceThroughTheNextTurn() {
		play(ALWAYS, OUTBREAK);

		game.endTurn();

		assertThat(report("Rome")).containsExactly(
				"Sickness broke out in your Nation; choose what to do before this Turn ends.", BASE_INCOME_REPORT);
		assertThat(game.viewOf("Rome").dilemmas())
			.containsExactly(new Dilemma("Outbreak", "Sickness broke out in your Nation",
					List.of(new Dilemma.Choice("Quarantine", false), new Dilemma.Choice("Let it spread", true)), null));
		assertThat(game.viewOf("Rome").population()).isEqualTo(12);
	}

	@Test
	void anAnsweredDilemmasChoiceAppliesAsTheTurnEndsAndTheTurnReportDescribesIt() {
		play(firstDraws(2), OUTBREAK);
		game.endTurn();
		int version = game.viewOf("Rome").version();

		assertThat(game.act("Rome", new Action.AnswerDilemma("Outbreak", "Quarantine"))).isEqualTo(ActionResult.ACCEPTED);

		NationView rome = game.viewOf("Rome");
		assertThat(rome.version()).isGreaterThan(version);
		assertThat(rome.gold()).isEqualTo(60);
		assertThat(rome.population()).isEqualTo(12);
		assertThat(rome.dilemmas()).singleElement().extracting(Dilemma::chosen).isEqualTo("Quarantine");

		game.endTurn();

		assertThat(report("Rome")).containsExactly("You quarantined the sick: your Nation lost 15 Gold and 2 Population.",
				BASE_INCOME_REPORT);
		rome = game.viewOf("Rome");
		assertThat(rome.gold()).isEqualTo(55);
		assertThat(rome.population()).isEqualTo(12);
		assertThat(rome.dilemmas()).isEmpty();
	}

	@Test
	void aDilemmaLeftUnansweredWhenTheTurnEndsGetsItsWorstChoice() {
		play(firstDraws(2), OUTBREAK);
		game.endTurn();

		game.endTurn();

		assertThat(report("Carthage")).containsExactly(
				"You did not choose in time what to do about Outbreak. You let the sickness spread: your Nation lost 8 Population.",
				incomeReport(4));
		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.population()).isEqualTo(6);
		assertThat(carthage.dilemmas()).isEmpty();
	}

	@Test
	void luckVariesTheEffectsOfTheChoiceThatApplies() {
		GameTest.Rigged luck = new GameTest.Rigged();
		luck.then(0, 0);
		play(luck, OUTBREAK);
		game.endTurn();
		game.act("Rome", new Action.AnswerDilemma("Outbreak", "Quarantine"));
		// Rome's Gold low and Population high, then Carthage's Population high.
		luck.then(0, 0.99, 0.99);

		game.endTurn();

		assertThat(report("Rome")).first().isEqualTo("You quarantined the sick: your Nation lost 14 Gold and 3 Population.");
		assertThat(report("Carthage")).first()
			.isEqualTo("You did not choose in time what to do about Outbreak. "
					+ "You let the sickness spread: your Nation lost 9 Population.");
	}

	@Test
	void theChosenChoicePicksOneOfItsOutcomesByChanceAndTheTurnReportTellsIt() {
		GameTest.Rigged luck = new GameTest.Rigged();
		luck.then(0, 0);
		play(luck, BANDITS);
		game.endTurn();
		game.act("Rome", new Action.AnswerDilemma("Bandits", "Hunt them down"));
		// Rome's outcome, then its Population as authored; Carthage's outcome,
		// then its Gold as authored.
		luck.then(0.8, 0.5, 0.1, 0.5);

		game.endTurn();

		assertThat(report("Rome")).first().isEqualTo("The bandits ambushed your hunters: your Nation lost 6 Population.");
		assertThat(report("Carthage")).first()
			.isEqualTo("You did not choose in time what to do about Bandits. "
					+ "You ignored the bandits and they plundered freely: your Nation lost 30 Gold.");
	}

	@Test
	void theWorstChoiceCanTurnOutWellAndLuckVariesTheOutcomesEffects() {
		GameTest.Rigged luck = new GameTest.Rigged();
		luck.then(0, 0);
		play(luck, BANDITS);
		game.endTurn();
		game.act("Rome", new Action.AnswerDilemma("Bandits", "Hunt them down"));
		// Rome's outcome, then its Population low; Carthage's outcome, then
		// its Gold high.
		luck.then(0, 0, 0.9, 0.99);

		game.endTurn();

		assertThat(report("Rome")).first().isEqualTo("Your people hunted the bandits down: your Nation lost 2 Population.");
		assertThat(report("Carthage")).first()
			.isEqualTo("You did not choose in time what to do about Bandits. "
					+ "You ignored the bandits, who fell out and left their loot behind: your Nation gained 11 Gold.");
	}

	@Test
	void thePlayerSeesAChoicesNameButNotItsOutcomesOrTheirOdds() {
		play(ALWAYS, BANDITS);

		game.endTurn();

		assertThat(game.viewOf("Rome").dilemmas()).containsExactly(new Dilemma("Bandits", "Bandits are raiding your roads",
				List.of(new Dilemma.Choice("Hunt them down", false), new Dilemma.Choice("Ignore them", true)), null));
	}

	@Test
	void aDilemmaCanOnlyBeAnsweredOnceAndOnlyWithAChoiceItOffers() {
		play(firstDraws(2), OUTBREAK);
		game.endTurn();

		assertThat(game.act("Rome", new Action.AnswerDilemma("Outbreak", "Pray")))
			.isEqualTo(ActionResult.rejected("Outbreak offers no choice called Pray."));
		assertThat(game.act("Rome", new Action.AnswerDilemma("Outbreak", "Let it spread"))).isEqualTo(ActionResult.ACCEPTED);
		assertThat(game.act("Rome", new Action.AnswerDilemma("Outbreak", "Quarantine")))
			.isEqualTo(ActionResult.rejected("You have already chosen what to do about Outbreak."));
		assertThat(game.act("Rome", new Action.AnswerDilemma("Flood", "Flee")))
			.isEqualTo(ActionResult.rejected("There is no Dilemma called Flood awaiting your choice."));
		assertThat(game.viewOf("Rome").dilemmas()).singleElement().extracting(Dilemma::chosen).isEqualTo("Let it spread");
	}

	@Test
	void anOutbreakStrikesALargeEnoughPopulationAndLeftUnansweredSpreads() {
		play(firstDraws(1), EventsTest.starter("Outbreak"));
		for (int turn = 1; turn <= 2; turn++) {
			game.endTurn();
			assertThat(report("Rome")).containsExactly(incomeReport(10 + 2 * (turn - 1)));
		}
		game.endTurn();
		assertThat(report("Rome")).containsExactly(
				"Sickness broke out in your Nation; choose what to do before this Turn ends.", incomeReport(14));
		assertThat(game.viewOf("Rome").dilemmas()).singleElement()
			.extracting(dilemma -> dilemma.choices().stream().map(Dilemma.Choice::name).toList())
			.isEqualTo(List.of("Quarantine", "Call the healers", "Let it spread"));

		game.endTurn();

		assertThat(report("Rome")).first()
			.isEqualTo("You did not choose in time what to do about Outbreak. "
					+ "You let the sickness spread: your Nation lost 10 Population.");
	}

	@Test
	void noDilemmaAwaitsAnyoneOnceTheGameIsWon() {
		play(firstDraws(2), OUTBREAK);
		game.endTurn();

		game.remove("Carthage");

		assertThat(game.winner()).contains("Rome");
		assertThat(game.viewOf("Rome").dilemmas()).isEmpty();
	}

}
