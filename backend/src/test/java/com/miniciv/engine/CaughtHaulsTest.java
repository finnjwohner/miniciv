package com.miniciv.engine;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.Rigged;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Spy carrying a Haul home caught on the way, and what becomes of its Haul.
 */
class CaughtHaulsTest {

	/**
	 * Luck that draws nothing lucky, 0.99, unless told otherwise.
	 */
	Rigged luck = new Rigged();

	Game game;

	/**
	 * The Haul Rome's thief carried when it was caught.
	 */
	Haul haul;

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	/**
	 * The lines of one section of a Nation's Turn Report.
	 */
	List<String> section(String nation, TurnReport.Kind kind) {
		return game.viewOf(nation)
			.turnReport()
			.sections()
			.stream()
			.filter(section -> section.kind() == kind)
			.flatMap(section -> section.lines().stream().map(TurnReport.Line::text))
			.toList();
	}

	void act(String nation, Action action) {
		assertThat(game.act(nation, action)).isEqualTo(ActionResult.ACCEPTED);
	}

	void armyOf(String nation, String unit, int count) {
		act(nation, new Action.TrainUnits(unit, count));
		while (!game.viewOf(nation).training().isEmpty()) {
			game.endTurn();
		}
	}

	/**
	 * Has Rome's Spy steal Gold from Carthage and set off home with it, then
	 * has the given Nation's Scout, Hunting Travelling Spies, catch it as the
	 * next Turn resolves, after the given draws, and it stays silent.
	 */
	void thiefCaughtBy(String catcher, Ruleset ruleset, double... before) {
		game = Locating.by("Rome", ruleset, Identities.of("Rome", "Carthage", "Egypt"), luck);
		armyOf(catcher, "Scout", 1);
		act(catcher, new Action.SetTask("HUNT", null, 1));
		armyOf("Rome", "Spy", 1);
		act("Rome", new Action.SendSpy("Carthage"));
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}
		act("Rome", new Action.SetMission(1, "Steal Gold"));
		while (game.viewOf("Rome").spies().getFirst().missionTurnsLeft() > 1) {
			game.endTurn();
		}
		// The Steal succeeds; then half of what Carthage gains is taken, and
		// the thief is not Unmasked.
		luck.then(0);
		game.endTurn();
		luck.then(0.5, 0.99);
		game.endTurn();
		haul = game.viewOf("Rome").spies().getFirst().haul();
		assertThat(haul).isNotNull();
		luck.then(before);
		luck.then(0, 0.5);
		game.endTurn();
		assertThat(game.viewOf("Rome").spies()).isEmpty();
	}

	void thiefCaughtBy(String catcher) {
		thiefCaughtBy(catcher, StealTest.RULESET);
	}

	/**
	 * Such as "12 Gold".
	 */
	String stolen() {
		return haul.amount() + " Gold";
	}

	@Test
	void catchingAThiefWithItsHaulPresentsAHaulDecision() {
		thiefCaughtBy("Egypt");

		assertThat(game.viewOf("Egypt").hauls())
			.containsExactly(new HaulDecision(1, "Carthage", haul, HaulDecision.Choice.NONE, false));
		assertThat(report("Egypt"))
			.contains("Your Scouts caught a Spy heading home from Carthage, and it would not say who sent it.");
		assertThat(section("Egypt", TurnReport.Kind.DECISIONS_AND_ALERTS)).contains("The Spy your Scouts caught carried "
				+ stolen() + " stolen from Carthage; choose whether to keep it or send it back before this Turn ends.");
		assertThat(game.viewOf("Carthage").hauls()).isEmpty();
	}

	@Test
	void aHaulDecisionCanBeChangedUntilTheTurnEnds() {
		thiefCaughtBy("Egypt");

		act("Egypt", new Action.AnswerHaul(1, false));
		assertThat(game.viewOf("Egypt").hauls()).singleElement()
			.extracting(HaulDecision::choice)
			.isEqualTo(HaulDecision.Choice.RETURN);
		act("Egypt", new Action.AnswerHaul(1, true));
		assertThat(game.viewOf("Egypt").hauls()).singleElement()
			.extracting(HaulDecision::choice)
			.isEqualTo(HaulDecision.Choice.KEEP);

		assertThat(game.act("Egypt", new Action.AnswerHaul(2, true)))
			.isEqualTo(ActionResult.rejected("You have no such Haul to decide on."));
		assertThat(game.act("Carthage", new Action.AnswerHaul(1, false)))
			.isEqualTo(ActionResult.rejected("You have no such Haul to decide on."));
	}

	@Test
	void anUnansweredHaulIsKeptAndArrivesAfterThreeTurns() {
		thiefCaughtBy("Egypt");

		game.endTurn();

		assertThat(game.viewOf("Egypt").hauls()).isEmpty();
		assertThat(report("Egypt")).contains("You kept the " + stolen() + " stolen from Carthage; it will arrive in 3 Turns.");
		for (int turn = 1; turn <= 2; turn++) {
			game.endTurn();
			assertThat(report("Egypt")).noneMatch(line -> line.contains("arrived"));
		}
		int before = game.viewOf("Egypt").gold();
		game.endTurn();

		assertThat(report("Egypt")).contains("The " + stolen() + " you kept, stolen from Carthage, arrived.");
		assertThat(game.viewOf("Egypt").gold() - before).isGreaterThanOrEqualTo(haul.amount());
		assertThat(report("Carthage")).noneMatch(line -> line.contains("returned"));
	}

	@Test
	void aHaulSentBackArrivesAtTheNationItWasStolenFromAfterThreeTurnsWithoutLocatingIt() {
		thiefCaughtBy("Egypt");
		assertThat(game.viewOf("Egypt").located()).doesNotContain("Carthage");
		act("Egypt", new Action.AnswerHaul(1, false));

		game.endTurn();

		assertThat(report("Egypt")).contains("You sent the " + stolen() + " back to Carthage; it will arrive in 3 Turns.");
		for (int turn = 1; turn <= 2; turn++) {
			game.endTurn();
			assertThat(report("Carthage")).noneMatch(line -> line.contains("returned"));
		}
		game.endTurn();

		assertThat(report("Carthage"))
			.contains("Cleopatra of Egypt returned the " + stolen() + " stolen from your Nation.");
		assertThat(report("Egypt")).noneMatch(line -> line.contains("arrived"));
	}

	/**
	 * The Gold a Nation gained or lost as each of the next Turns resolved.
	 */
	List<Integer> goldChanges(String nation, int turns) {
		List<Integer> changes = new ArrayList<>();
		for (int turn = 1; turn <= turns; turn++) {
			int before = game.viewOf(nation).gold();
			game.endTurn();
			changes.add(game.viewOf(nation).gold() - before);
		}
		return changes;
	}

	@Test
	void aHaulOnItsWayCannotBeInterceptedAndArrivesWhole() {
		thiefCaughtBy("Egypt");
		act("Egypt", new Action.AnswerHaul(1, false));
		// Every draw from now on is as lucky as can be: a Spy would be caught
		// by anything that might catch it.
		luck.always(0);

		List<Integer> changes = goldChanges("Carthage", 4);

		assertThat(report("Carthage"))
			.contains("Cleopatra of Egypt returned the " + stolen() + " stolen from your Nation.");
		assertThat(changes.get(3) - changes.get(2)).isGreaterThanOrEqualTo(haul.amount());
	}

	@Test
	void whenTheVictimCatchesTheThiefItsHaulComesHomeAfterThreeTurnsWithoutADecision() {
		thiefCaughtBy("Carthage");

		assertThat(game.viewOf("Carthage").hauls()).isEmpty();
		assertThat(report("Carthage")).contains("The Spy your Scouts caught carried " + stolen()
				+ " stolen from your Nation; it will be back in 3 Turns.");
		for (int turn = 1; turn <= 2; turn++) {
			game.endTurn();
			assertThat(report("Carthage")).noneMatch(line -> line.contains("came back"));
		}
		game.endTurn();

		assertThat(report("Carthage")).contains("The " + stolen() + " stolen from your Nation came back.");
	}

	@Test
	void aHaulDecisionDoesNotStopADilemmaBeingFacedTheSameTurn() {
		thiefCaughtBy("Egypt");
		int caughtOn = game.viewOf("Egypt").turn() - 1;
		Event dilemma = new Event("Bandits", Event.Subject.NATION, 1,
				new Event.Conditions(caughtOn, null, null, null, null, null, null), null, "Bandits roam the roads",
				List.of(new Event.Choice("Pay them off", Event.Effects.NONE, false, "You paid the bandits"),
						new Event.Choice("Ignore them", Event.Effects.NONE, true, "You ignored the bandits")));
		Ruleset withDilemma = new Ruleset(StealTest.RULESET.units(), StealTest.RULESET.buildings(), List.of(),
				List.of(dilemma), StealTest.RULESET.missions());

		// The Dilemma is rolled for each Nation, and happens, before Egypt's
		// Scout catches the thief.
		thiefCaughtBy("Egypt", withDilemma, 0.99, 0.99, 0.99);

		assertThat(game.viewOf("Egypt").hauls()).hasSize(1);
		assertThat(game.viewOf("Egypt").dilemmas()).extracting(Dilemma::name).containsExactly("Bandits");
	}

}
