package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.RULESET;
import static com.miniciv.engine.GameTest.Rigged;
import static org.assertj.core.api.Assertions.assertThat;

class EncountersTest {

	static final Encounter.Choice NONE = Encounter.Choice.NONE;

	static final Encounter.Choice FIGHT = Encounter.Choice.FIGHT;

	static final Encounter.Choice PASS = Encounter.Choice.PASS;

	/**
	 * Draws nothing lucky unless told to, so Warparties meet only when a draw
	 * is queued for it, and Battles are even.
	 */
	Rigged luck = new Rigged();

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage", "Egypt"), luck);

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	void armyOf(String nation, String unit, int count) {
		assertThat(game.act(nation, new Action.TrainUnits(unit, count))).isEqualTo(ActionResult.ACCEPTED);
		while (game.viewOf(nation).army().getOrDefault(unit, 0) < count) {
			game.endTurn();
		}
	}

	void send(String nation, String target, Map<String, Integer> units) {
		assertThat(game.act(nation, new Action.SendWarparty(target, units))).isEqualTo(ActionResult.ACCEPTED);
	}

	ActionResult answer(String nation, int warparty, boolean fight) {
		return game.act(nation, new Action.AnswerEncounter(warparty, fight));
	}

	static Encounter.Side side(String nation, int warparty, Map<String, Integer> units, Encounter.Choice choice) {
		return new Encounter.Side(nation, warparty, units, choice);
	}

	/**
	 * Has Rome send a Warparty of Warrior ×3 to Carthage while Carthage sends
	 * one of Warrior ×1 to Rome, and has them meet as the Turn they were sent
	 * resolves.
	 */
	void romeMeetsCarthage() {
		armyOf("Rome", "Warrior", 3);
		armyOf("Carthage", "Warrior", 1);
		send("Rome", "Carthage", Map.of("Warrior", 3));
		send("Carthage", "Rome", Map.of("Warrior", 1));
		luck.then(0);
		game.endTurn();
	}

	@Test
	void warpartiesFromDifferentNationsAbroadMayMeetAndBothPlayersSeeTheOtherWarpartysNationAndUnits() {
		romeMeetsCarthage();

		Encounter met = new Encounter(side("Rome", 1, Map.of("Warrior", 3), NONE),
				side("Carthage", 1, Map.of("Warrior", 1), NONE));
		assertThat(game.viewOf("Rome").encounters()).containsExactly(met);
		assertThat(game.viewOf("Carthage").encounters()).containsExactly(met);
		assertThat(game.viewOf("Egypt").encounters()).isEmpty();
		assertThat(report("Rome")).contains("Your Warparty of Warrior ×3 heading to Carthage met Hannibal of Carthage's Warparty"
				+ " of Warrior ×1; choose whether to fight before this Turn ends.");
		assertThat(report("Carthage")).contains("Your Warparty of Warrior ×1 heading to Rome met Caesar of Rome's Warparty of"
				+ " Warrior ×3; choose whether to fight before this Turn ends.");
		assertThat(report("Egypt")).noneMatch(line -> line.contains("met"));
	}

	@Test
	void warpartiesMeetOnlyByChance() {
		armyOf("Rome", "Warrior", 1);
		armyOf("Carthage", "Warrior", 1);
		send("Rome", "Carthage", Map.of("Warrior", 1));
		send("Carthage", "Rome", Map.of("Warrior", 1));

		game.endTurn();

		assertThat(game.viewOf("Rome").encounters()).isEmpty();
		assertThat(report("Rome")).noneMatch(line -> line.contains("met"));
	}

	@Test
	void aNationsOwnWarpartiesNeverMeet() {
		armyOf("Rome", "Warrior", 2);
		send("Rome", "Carthage", Map.of("Warrior", 1));
		send("Rome", "Egypt", Map.of("Warrior", 1));
		luck.always(0);

		game.endTurn();

		assertThat(game.viewOf("Rome").encounters()).isEmpty();
	}

	@Test
	void warpartiesHeadingHomeMeetTooButNotThoseWaitingAtANationForTheBattle() {
		armyOf("Rome", "Warrior", 2);
		armyOf("Carthage", "Warrior", 1);
		send("Rome", "Egypt", Map.of("Warrior", 1));
		game.endTurn();
		game.endTurn();
		send("Rome", "Egypt", Map.of("Warrior", 1));
		send("Carthage", "Egypt", Map.of("Warrior", 1));
		luck.always(0);
		game.endTurn();

		assertThat(game.viewOf("Rome").warparties()).extracting(Warparty::awaitingBattle).containsExactly(true, false);
		assertThat(game.viewOf("Rome").encounters()).containsExactly(new Encounter(
				side("Rome", 2, Map.of("Warrior", 1), NONE), side("Carthage", 1, Map.of("Warrior", 1), NONE)));

		game.endTurn();

		assertThat(game.viewOf("Rome").warparties()).extracting(Warparty::homeward).containsExactly(true, false);
		assertThat(game.viewOf("Rome").encounters()).containsExactly(new Encounter(
				side("Rome", 1, Map.of("Warrior", 1), NONE), side("Carthage", 1, Map.of("Warrior", 1), NONE)));
		assertThat(report("Rome")).contains("Your Warparty of Warrior ×1 heading home from Egypt met Hannibal of Carthage's"
				+ " Warparty of Warrior ×1; choose whether to fight before this Turn ends.");
	}

	@Test
	void eachWarpartyMeetsAtMostOneOtherInATurn() {
		armyOf("Rome", "Warrior", 1);
		armyOf("Carthage", "Warrior", 1);
		armyOf("Egypt", "Warrior", 1);
		send("Rome", "Carthage", Map.of("Warrior", 1));
		send("Carthage", "Egypt", Map.of("Warrior", 1));
		send("Egypt", "Rome", Map.of("Warrior", 1));
		luck.always(0);

		game.endTurn();

		assertThat(game.viewOf("Rome").encounters()).containsExactly(new Encounter(
				side("Rome", 1, Map.of("Warrior", 1), NONE), side("Carthage", 1, Map.of("Warrior", 1), NONE)));
		assertThat(game.viewOf("Egypt").encounters()).isEmpty();
	}

	@Test
	void eachPlayerChoosesWhetherToFightAndTheOtherSeesTheChoiceAtOnce() {
		romeMeetsCarthage();

		assertThat(answer("Carthage", 1, false)).isEqualTo(ActionResult.ACCEPTED);

		Encounter answered = new Encounter(side("Rome", 1, Map.of("Warrior", 3), NONE),
				side("Carthage", 1, Map.of("Warrior", 1), PASS));
		assertThat(game.viewOf("Rome").encounters()).containsExactly(answered);
		assertThat(game.viewOf("Carthage").encounters()).containsExactly(answered);
		assertThat(answer("Carthage", 2, true))
			.isEqualTo(ActionResult.rejected("No Encounter awaits your choice for that Warparty."));
		assertThat(answer("Egypt", 1, true))
			.isEqualTo(ActionResult.rejected("No Encounter awaits your choice for that Warparty."));

		assertThat(answer("Rome", 1, true)).isEqualTo(ActionResult.ACCEPTED);

		assertThat(game.viewOf("Carthage").encounters()).containsExactly(new Encounter(
				side("Rome", 1, Map.of("Warrior", 3), FIGHT), side("Carthage", 1, Map.of("Warrior", 1), PASS)));
	}

	@Test
	void aPlayerCanChangeTheirChoiceUntilTheTurnEndsAndTheOtherSeesTheChangeAtOnce() {
		romeMeetsCarthage();
		answer("Rome", 1, true);
		int carthageVersion = game.viewOf("Carthage").version();

		assertThat(answer("Rome", 1, false)).isEqualTo(ActionResult.ACCEPTED);

		assertThat(game.viewOf("Carthage").version()).isGreaterThan(carthageVersion);
		assertThat(game.viewOf("Carthage").encounters()).containsExactly(new Encounter(
				side("Rome", 1, Map.of("Warrior", 3), PASS), side("Carthage", 1, Map.of("Warrior", 1), NONE)));
	}

	@Test
	void onlyTheLatestChoiceCountsWhenTheTurnEnds() {
		romeMeetsCarthage();
		answer("Rome", 1, true);
		answer("Rome", 1, false);
		answer("Carthage", 1, false);

		game.endTurn();

		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 3), 9, false, 1, 2));
		assertThat(game.viewOf("Carthage").warparties())
			.containsExactly(new Warparty(1, "Rome", Map.of("Warrior", 1), 3, false, 1, 2));
		assertThat(report("Rome")).contains("Your Warparty of Warrior ×3 heading to Carthage and Hannibal of Carthage's"
				+ " Warparty of Warrior ×1 went their separate ways, as neither of you chose to fight.");
	}

	@Test
	void ifNeitherChoosesToFightNothingHappens() {
		romeMeetsCarthage();
		answer("Rome", 1, false);

		game.endTurn();

		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 3), 9, false, 1, 2));
		assertThat(game.viewOf("Carthage").warparties())
			.containsExactly(new Warparty(1, "Rome", Map.of("Warrior", 1), 3, false, 1, 2));
		assertThat(game.viewOf("Rome").encounters()).isEmpty();
		assertThat(report("Rome")).contains("Your Warparty of Warrior ×3 heading to Carthage and Hannibal of Carthage's"
				+ " Warparty of Warrior ×1 went their separate ways, as neither of you chose to fight.");
		assertThat(report("Carthage")).contains("Your Warparty of Warrior ×1 heading to Rome and Caesar of Rome's"
				+ " Warparty of Warrior ×3 went their separate ways, as neither of you chose to fight.");
	}

	@Test
	void ifEitherChoosesToFightTheWarpartiesFightLikeABattleAndTheSurvivorsTravelOnWithoutDelay() {
		armyOf("Rome", "Warrior", 2);
		armyOf("Carthage", "Warrior", 2);
		send("Rome", "Carthage", Map.of("Warrior", 2));
		send("Carthage", "Rome", Map.of("Warrior", 2));
		luck.then(0);
		game.endTurn();

		answer("Carthage", 1, true);
		game.endTurn();

		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 1), 3, false, 1, 2, Plunder.NONE, 6));
		assertThat(game.viewOf("Carthage").warparties())
			.containsExactly(new Warparty(1, "Rome", Map.of("Warrior", 1), 3, false, 1, 2, Plunder.NONE, 6));
		assertThat(game.viewOf("Rome").encounters()).isEmpty();
		assertThat(report("Rome")).contains("Your Warparty of Warrior ×2 heading to Carthage fought Hannibal of Carthage's"
				+ " Warparty of Warrior ×2, as Carthage chose to fight. You lost Warrior ×1 and Carthage lost Warrior ×1.");
		assertThat(report("Carthage")).contains("Your Warparty of Warrior ×2 heading to Rome fought Caesar of Rome's"
				+ " Warparty of Warrior ×2, as you chose to fight. You lost Warrior ×1 and Rome lost Warrior ×1.");

		game.endTurn();

		assertThat(report("Rome")).contains("Your Warparty of Warrior ×1 arrived at Carthage; the Battle will be"
				+ " fought as this Turn ends.");
	}

	@Test
	void aWarpartyWipedOutInTheFightIsDestroyed() {
		romeMeetsCarthage();
		answer("Rome", 1, true);
		answer("Carthage", 1, true);

		game.endTurn();

		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 3), 9, false, 1, 2));
		assertThat(game.viewOf("Carthage").warparties()).isEmpty();
		assertThat(report("Rome")).contains(
				"Your Warparty of Warrior ×3 heading to Carthage fought Hannibal of Carthage's Warparty of Warrior ×1, as you both"
						+ " chose to fight. You lost no Units and Carthage lost Warrior ×1.",
				"Hannibal of Carthage's Warparty was destroyed.");
		assertThat(report("Carthage")).contains(
				"Your Warparty of Warrior ×1 heading to Rome fought Caesar of Rome's Warparty of Warrior ×3, as you both chose to"
						+ " fight. You lost Warrior ×1 and Rome lost no Units.",
				"Your Warparty heading to Rome was destroyed.");
	}

	@Test
	void survivorsMayBeRecalledAfterTheFight() {
		romeMeetsCarthage();
		answer("Carthage", 1, true);
		game.endTurn();

		assertThat(game.act("Rome", new Action.RecallWarparty(1))).isEqualTo(ActionResult.ACCEPTED);

		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 3), 9, true, 2, 2));
	}

	@Test
	void anEncounterWithANationRemovedFromTheGameIsCancelled() {
		romeMeetsCarthage();
		answer("Rome", 1, true);

		game.remove("Carthage");

		assertThat(game.viewOf("Rome").encounters()).isEmpty();
		game.endTurn();
		assertThat(report("Rome")).noneMatch(line -> line.contains("fought"));
		assertThat(game.viewOf("Rome").army()).isEqualTo(Map.of("Warrior", 3));
	}

}
