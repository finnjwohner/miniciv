package com.miniciv.engine;

import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.EventsTest.ALWAYS;
import static com.miniciv.engine.GameTest.BASE_INCOME_REPORT;
import static com.miniciv.engine.GameTest.RULESET;
import static org.assertj.core.api.Assertions.assertThat;

class DilemmasTest {

	static final Event.Choice QUARANTINE = new Event.Choice("Quarantine", new Event.Effects(-15, -2, 0, 0, 0), false,
			"You quarantined the sick");

	static final Event.Choice LET_IT_SPREAD = new Event.Choice("Let it spread", new Event.Effects(0, -8, 0, 0, 0),
			true, "You let the sickness spread");

	static final Event OUTBREAK = new Event("Outbreak", Event.Subject.NATION, 0.5, null, null,
			"Sickness broke out in your Nation", List.of(QUARANTINE, LET_IT_SPREAD));

	Game game;

	void play(RandomGenerator luck, Event... events) {
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(), List.of(events)),
				List.of("Rome", "Carthage"), luck);
	}

	/**
	 * Luck that makes every Event happen for the given number of draws, and
	 * none after.
	 */
	static RandomGenerator firstDraws(int draws) {
		int[] drawn = { 0 };
		return () -> drawn[0]++ < draws ? 0 : -1;
	}

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	@Test
	void aDilemmaIsAnnouncedAndAwaitsItsPlayersChoiceThroughTheNextTurn() {
		play(ALWAYS, OUTBREAK);

		game.endTurn();

		assertThat(report("Rome")).containsExactly(BASE_INCOME_REPORT,
				"Sickness broke out in your Nation; choose what to do before this Turn ends.");
		assertThat(game.viewOf("Rome").dilemmas())
			.containsExactly(new Dilemma("Outbreak", "Sickness broke out in your Nation", List.of(QUARANTINE, LET_IT_SPREAD)));
		assertThat(game.viewOf("Rome").population()).isEqualTo(12);
	}

	@Test
	void answeringADilemmaAppliesTheChoiceAtOnceAndTheTurnReportDescribesIt() {
		play(firstDraws(2), OUTBREAK);
		game.endTurn();
		int version = game.viewOf("Rome").version();

		assertThat(game.act("Rome", new Action.AnswerDilemma("Outbreak", "Quarantine"))).isEqualTo(ActionResult.ACCEPTED);

		NationView rome = game.viewOf("Rome");
		assertThat(rome.version()).isGreaterThan(version);
		assertThat(rome.gold()).isEqualTo(45);
		assertThat(rome.population()).isEqualTo(10);
		assertThat(rome.dilemmas()).isEmpty();
		game.endTurn();
		assertThat(report("Rome")).containsExactly("You quarantined the sick: your Nation lost 15 Gold and 2 Population.",
				BASE_INCOME_REPORT);
	}

	@Test
	void aDilemmaLeftUnansweredWhenTheTurnEndsGetsItsWorstChoice() {
		play(firstDraws(2), OUTBREAK);
		game.endTurn();

		game.endTurn();

		assertThat(report("Carthage")).containsExactly(
				"You did not choose in time what to do about Outbreak. You let the sickness spread: your Nation lost 8 Population.",
				BASE_INCOME_REPORT);
		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.population()).isEqualTo(6);
		assertThat(carthage.dilemmas()).isEmpty();
	}

	@Test
	void aDilemmaCanOnlyBeAnsweredOnceAndOnlyWithAChoiceItOffers() {
		play(firstDraws(2), OUTBREAK);
		game.endTurn();

		assertThat(game.act("Rome", new Action.AnswerDilemma("Outbreak", "Pray")))
			.isEqualTo(ActionResult.rejected("Outbreak offers no choice called Pray."));
		assertThat(game.act("Rome", new Action.AnswerDilemma("Outbreak", "Let it spread"))).isEqualTo(ActionResult.ACCEPTED);
		assertThat(game.act("Rome", new Action.AnswerDilemma("Outbreak", "Quarantine")))
			.isEqualTo(ActionResult.rejected("There is no Dilemma called Outbreak awaiting your choice."));
		assertThat(game.viewOf("Rome").gold()).isEqualTo(60);
		assertThat(game.viewOf("Rome").population()).isEqualTo(4);
	}

	@Test
	void anOutbreakStrikesALargeEnoughPopulationAndLeftUnansweredSpreads() {
		play(firstDraws(1), EventsTest.starter("Outbreak"));
		for (int turn = 1; turn <= 2; turn++) {
			game.endTurn();
			assertThat(report("Rome")).containsExactly(BASE_INCOME_REPORT);
		}
		game.endTurn();
		assertThat(report("Rome")).containsExactly(BASE_INCOME_REPORT,
				"Sickness broke out in your Nation; choose what to do before this Turn ends.");
		assertThat(game.viewOf("Rome").dilemmas()).singleElement()
			.extracting(dilemma -> dilemma.choices().stream().map(Event.Choice::name).toList())
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
