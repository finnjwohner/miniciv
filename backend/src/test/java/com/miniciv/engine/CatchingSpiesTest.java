package com.miniciv.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.Rigged;
import static com.miniciv.engine.GameTest.luck;
import static com.miniciv.engine.ScoutsTest.draw;
import static com.miniciv.engine.SpiesTest.RULESET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class CatchingSpiesTest {

	/**
	 * Luck that catches every Spy it may, and has it die: a draw low enough
	 * to catch, then one picking the first outcome.
	 */
	static final RandomGenerator CAUGHT_AND_DIES = luck(draw(0), draw(0.1));

	static final RandomGenerator CAUGHT_AND_SILENT = luck(draw(0), draw(0.5));

	static final RandomGenerator CAUGHT_AND_CONFESSES = luck(draw(0), draw(0.9));

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage", "Egypt"), EVEN_LUCK);

	void play(RandomGenerator luck, String... nations) {
		game = new Game(RULESET, Identities.of(nations), luck);
	}

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	void armyOf(String nation, String unit, int count) {
		assertThat(game.act(nation, new Action.TrainUnits(unit, count))).isEqualTo(ActionResult.ACCEPTED);
		while (!game.viewOf(nation).training().isEmpty()) {
			game.endTurn();
		}
	}

	ActionResult sendSpy(String nation, String target) {
		return game.act(nation, new Action.SendSpy(target));
	}

	/**
	 * Sends a Spy from Rome, which must have one at home, to Carthage and ends
	 * Turns until it has arrived.
	 */
	void spyArrivesAtCarthage() {
		assertThat(sendSpy("Rome", "Carthage")).isEqualTo(ActionResult.ACCEPTED);
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}
	}

	MapNation onMapOf(String nation, String other) {
		return game.viewOf(nation).map().stream().filter(entry -> entry.name().equals(other)).findFirst().orElseThrow();
	}

	@Test
	void spiesAtHomeCatchAnEnemySpyInTheNationWhichMayDieBeforeSayingWhoSentIt() {
		play(CAUGHT_AND_DIES, "Rome", "Carthage", "Egypt");
		armyOf("Carthage", "Spy", 1);
		armyOf("Rome", "Spy", 1);

		spyArrivesAtCarthage();

		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(report("Carthage"))
			.contains("Your Spies caught a Spy in your Nation, and it died before saying who sent it.");
		assertThat(onMapOf("Carthage", "Rome").facts()).isEqualTo(Facts.NONE);
		assertThat(game.viewOf("Carthage").army()).containsExactly(entry("Spy", 1));
	}

	@Test
	void aCaughtSpyMayStaySilent() {
		play(CAUGHT_AND_SILENT, "Rome", "Carthage");
		armyOf("Carthage", "Spy", 1);
		armyOf("Rome", "Spy", 1);

		spyArrivesAtCarthage();

		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(report("Carthage")).contains("Your Spies caught a Spy in your Nation, and it would not say who sent it.");
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Rome"));
		assertThat(onMapOf("Carthage", "Rome").facts()).isEqualTo(Facts.NONE);
	}

	@Test
	void aCaughtSpyMayConfessRevealingItsSenderAndACurrentSnapshotOfItsNation() {
		play(CAUGHT_AND_CONFESSES, "Rome", "Carthage");
		armyOf("Carthage", "Spy", 1);
		armyOf("Rome", "Spy", 2);
		armyOf("Rome", "Warrior", 1);
		game.act("Rome", new Action.StartBuilding("Walls"));

		spyArrivesAtCarthage();

		NationView rome = game.viewOf("Rome");
		int turn = game.turn() - 1;
		assertThat(report("Carthage")).containsSubsequence(
				"Your Spies caught a Spy in your Nation, and it confessed that Caesar of Rome sent it.",
				"From its confession, Rome has " + rome.gold() + " Gold, " + rome.population() + " Population and "
						+ rome.research() + " Research; Rome's Army is Spy ×1 and Warrior ×1; Rome has built Walls ×1, "
						+ "and its Defense is 10.");
		assertThat(onMapOf("Carthage", "Rome").facts())
			.isEqualTo(new Facts(new Fact<>(rome.gold(), turn), new Fact<>(rome.population(), turn),
					new Fact<>(rome.research(), turn), new Fact<>(Map.of("Spy", 1, "Warrior", 1), turn),
					new Fact<>(Map.of("Walls", 1), turn), new Fact<>(10, turn)));
		assertThat(rome.spies()).isEmpty();
	}

	@Test
	void theSenderIsNotToldOfTheCaptureWithoutAnotherSpyThere() {
		play(CAUGHT_AND_CONFESSES, "Rome", "Carthage");
		armyOf("Carthage", "Spy", 1);
		armyOf("Rome", "Spy", 1);

		spyArrivesAtCarthage();

		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(report("Rome")).noneMatch(line -> line.contains("caught") || line.contains("confess"));
		assertThat(game.viewOf("Rome").army()).isEmpty();
	}

	@Test
	void theSenderLearnsOfTheCaptureWhenAnotherOfItsSpiesInTheSameNationReportsIt() {
		// The first Spy is caught and dies; the second is not caught and
		// reports nothing of its own.
		play(luck(draw(0), draw(0.1), draw(0.99), draw(0.99)), "Rome", "Carthage");
		armyOf("Carthage", "Spy", 1);
		armyOf("Rome", "Spy", 2);
		sendSpy("Rome", "Carthage");

		spyArrivesAtCarthage();

		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(2, "Spy", "Carthage", false, 0, 3));
		assertThat(report("Rome"))
			.contains("Your Spy in Carthage reported that Carthage caught your other Spy there, and it died.");
	}

	@Test
	void aSpyCaughtLaterInTheSameTurnDoesNotReportAnEarlierCapture() {
		play(CAUGHT_AND_DIES, "Rome", "Carthage");
		armyOf("Carthage", "Spy", 1);
		armyOf("Rome", "Spy", 2);
		sendSpy("Rome", "Carthage");

		spyArrivesAtCarthage();

		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(report("Carthage")).filteredOn(line -> line.startsWith("Your Spies caught")).hasSize(2);
		assertThat(report("Rome")).noneMatch(line -> line.contains("caught"));
	}

	@Test
	void anotherSpyReportsWhetherTheCaughtSpyStayedSilentOrConfessed() {
		play(luck(draw(0), draw(0.5), draw(0.99), draw(0.99)), "Rome", "Carthage");
		armyOf("Carthage", "Spy", 1);
		armyOf("Rome", "Spy", 2);
		sendSpy("Rome", "Carthage");
		spyArrivesAtCarthage();
		assertThat(report("Rome"))
			.contains("Your Spy in Carthage reported that Carthage caught your other Spy there, and it stayed silent.");

		play(luck(draw(0), draw(0.9), draw(0.99), draw(0.99)), "Rome", "Carthage");
		armyOf("Carthage", "Spy", 1);
		armyOf("Rome", "Spy", 2);
		sendSpy("Rome", "Carthage");
		spyArrivesAtCarthage();
		assertThat(report("Rome"))
			.contains("Your Spy in Carthage reported that Carthage caught your other Spy there, and it confessed.");
	}

	@Test
	void spiesAtHomeDoNotCatchSpiesStillTravelling() {
		play(CAUGHT_AND_DIES, "Rome", "Carthage");
		armyOf("Carthage", "Spy", 1);
		armyOf("Rome", "Spy", 1);
		sendSpy("Rome", "Carthage");

		game.endTurn();
		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 1, 2));
		assertThat(report("Carthage")).noneMatch(line -> line.contains("caught"));
	}

	@Test
	void aSpyIsNotCaughtWhenLuckIsAgainstIt() {
		armyOf("Carthage", "Spy", 1);
		armyOf("Rome", "Spy", 1);

		spyArrivesAtCarthage();
		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 0, 3));
		assertThat(report("Carthage")).noneMatch(line -> line.contains("caught"));
	}

	@Test
	void scoutsCatchAnyOtherNationsTravellingSpy() {
		play(CAUGHT_AND_DIES, "Rome", "Carthage", "Egypt");
		armyOf("Egypt", "Scout", 1);
		armyOf("Rome", "Spy", 1);
		sendSpy("Rome", "Carthage");

		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(report("Egypt"))
			.contains("Your Scouts caught a Spy heading to Carthage, and it died before saying who sent it.");
		assertThat(report("Rome")).noneMatch(line -> line.contains("caught"));
	}

	@Test
	void scoutsCatchASpyHeadingHome() {
		play(CAUGHT_AND_SILENT, "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Spy", 1);
		sendSpy("Rome", "Carthage");
		game.endTurn();
		game.endTurn();
		game.remove("Carthage");
		game.act("Egypt", new Action.TrainUnits("Scout", 1));

		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(report("Egypt"))
			.contains("Your Scouts caught a Spy heading home from Carthage, and it would not say who sent it.");
	}

	@Test
	void scoutsDoNotCatchSpiesInPlaceOrTheirOwnNationsSpies() {
		Rigged luck = new Rigged();
		play(luck, "Rome", "Carthage");
		armyOf("Rome", "Scout", 1);
		armyOf("Rome", "Spy", 1);
		spyArrivesAtCarthage();
		armyOf("Carthage", "Scout", 1);
		luck.always(0);

		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 0, 3));
		assertThat(report("Carthage")).noneMatch(line -> line.contains("caught"));
		assertThat(report("Rome")).noneMatch(line -> line.contains("caught"));
	}

	@Test
	void aTravellingWarpartyCatchesAnyOtherNationsTravellingSpy() {
		play(CAUGHT_AND_CONFESSES, "Rome", "Carthage", "Egypt");
		armyOf("Carthage", "Warrior", 1);
		armyOf("Rome", "Spy", 1);
		game.act("Carthage", new Action.SendWarparty("Egypt", Map.of("Warrior", 1)));
		sendSpy("Rome", "Egypt");

		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(report("Carthage")).contains(
				"Your Warparty of Warrior ×1 heading to Egypt caught a Spy heading to Egypt, and it confessed that Caesar of Rome sent it.");
		assertThat(report("Carthage")).anyMatch(line -> line.startsWith("From its confession, Rome has "));
		assertThat(onMapOf("Carthage", "Rome").facts().gold()).isNotNull();
	}

	@Test
	void aSpyCaughtTravellingIsReportedToItsSenderByAnotherOfItsSpiesInTheCatchingNation() {
		Rigged luck = new Rigged();
		play(luck, "Rome", "Carthage", "Egypt");
		armyOf("Rome", "Spy", 2);
		sendSpy("Rome", "Egypt");
		armyOf("Egypt", "Scout", 1);
		game.endTurn();
		game.endTurn();
		assertThat(game.viewOf("Rome").spies()).first().matches(Spy::inPlace);
		sendSpy("Rome", "Carthage");
		luck.then(0, 0.1);

		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(1, "Spy", "Egypt", false, 0, 3));
		assertThat(report("Egypt"))
			.contains("Your Scouts caught a Spy heading to Carthage, and it died before saying who sent it.");
		assertThat(report("Rome"))
			.contains("Your Spy in Egypt reported that Egypt caught your Spy heading to Carthage, and it died.");
	}

	/**
	 * Over many Turns from a fixed seed, Carthage's Spies at home catch Rome's
	 * Spies now and then, and every outcome happens.
	 */
	@Test
	void caughtSpiesDieStaySilentOrConfessFromAFixedSeed() {
		play(new Random(7), "Rome", "Carthage");
		armyOf("Carthage", "Spy", 2);
		List<String> caught = new ArrayList<>();
		for (int turn = 1; turn <= 60; turn++) {
			if (game.viewOf("Rome").spies().isEmpty() && game.viewOf("Rome").army().getOrDefault("Spy", 0) == 0) {
				game.act("Rome", new Action.TrainUnits("Spy", 1));
			}
			if (game.viewOf("Rome").army().getOrDefault("Spy", 0) > 0) {
				sendSpy("Rome", "Carthage");
			}
			game.endTurn();
			report("Carthage").stream().filter(line -> line.startsWith("Your Spies caught")).forEach(caught::add);
		}

		assertThat(caught).anyMatch(line -> line.endsWith("died before saying who sent it."))
			.anyMatch(line -> line.endsWith("would not say who sent it."))
			.anyMatch(line -> line.endsWith("confessed that Caesar of Rome sent it."));
	}

}
