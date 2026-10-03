package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.incomeReport;
import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.RULESET;
import static com.miniciv.engine.GameTest.Rigged;
import static com.miniciv.engine.GameTest.luck;
import static com.miniciv.engine.TravelTest.soldier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

class RecallTest {

	/**
	 * Game content with only Units of Strength 1.
	 */
	static final Ruleset SOLDIERS = new Ruleset(List.of(soldier(1)), List.of(), List.of(), List.of());

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage"), EVEN_LUCK);

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	void armyOf(String nation, String unit, int count) {
		assertThat(game.act(nation, new Action.TrainUnits(unit, count))).isEqualTo(ActionResult.ACCEPTED);
		while (game.viewOf(nation).army().getOrDefault(unit, 0) < count) {
			game.endTurn();
		}
	}

	ActionResult recall(String nation, int warparty) {
		return game.act(nation, new Action.RecallWarparty(warparty));
	}

	@Test
	void aRecalledWarpartyTurnsBackAtOnceAndTakesAsLongToGetHomeAsItHasTravelled() {
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		game.endTurn();
		game.endTurn();
		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 2), 6, false, 1, 2));

		assertThat(recall("Rome", 1)).isEqualTo(ActionResult.ACCEPTED);

		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 2), 6, true, 2, 2));
		game.endTurn();
		assertThat(report("Rome")).contains("You Recalled your Warparty of Warrior ×2 heading to Carthage.");
		assertThat(report("Carthage")).containsExactly(incomeReport(16));
		assertThat(game.viewOf("Rome").warparties()).singleElement().extracting(Warparty::turnsLeft).isEqualTo(1);

		game.endTurn();

		NationView rome = game.viewOf("Rome");
		assertThat(rome.warparties()).isEmpty();
		assertThat(rome.army()).containsExactly(entry("Warrior", 2));
		assertThat(rome.turnReport().lines())
			.contains("Your Warparty came home from Carthage and Warrior ×2 rejoined your Army.");
	}

	@Test
	void aWarpartyRecalledOnTheTurnItWasSentTakesOneTurnToGetHome() {
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));

		recall("Rome", 1);

		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 2), 6, true, 1, 0));
		game.endTurn();
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Warrior", 2));
	}

	@Test
	void aWarpartyRecalledOneTurnAfterBeingSentIsHomeToDefendAgainstABattleAsTheNextTurnEnds() {
		game.act("Rome", new Action.TrainUnits("Warrior", 2));
		game.act("Carthage", new Action.TrainUnits("Warrior", 2));
		game.endTurn();
		game.act("Carthage", new Action.SendWarparty("Rome", Map.of("Warrior", 2)));
		game.endTurn();
		game.endTurn();
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		game.endTurn();
		assertThat(report("Rome"))
			.contains("Hannibal of Carthage's Warparty arrived at your Nation; the Battle will be fought as this Turn ends.");

		recall("Rome", 1);
		game.endTurn();

		assertThat(report("Rome")).containsSubsequence("You Recalled your Warparty of Warrior ×2 heading to Carthage.",
				"Your Warparty came home from Carthage and Warrior ×2 rejoined your Army.",
				"Your Army of Warrior ×2 fought Hannibal of Carthage's Warparty of Warrior ×2. You lost Warrior ×1 and Carthage lost Warrior ×1.");
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Warrior", 1));
	}

	@Test
	void aRecalledWarpartyThatHadArrivedFightsNoBattleAndItsTargetIsToldItTurnedBack() {
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		game.endTurn();
		game.endTurn();
		game.endTurn();
		assertThat(game.viewOf("Rome").warparties()).singleElement().extracting(Warparty::turnsLeft).isEqualTo(0);

		recall("Rome", 1);
		game.endTurn();

		assertThat(report("Rome")).containsExactly("You Recalled your Warparty of Warrior ×2 heading to Carthage.",
				incomeReport(12), "Your Units' upkeep cost 2 Gold.");
		assertThat(report("Carthage")).containsExactly("Caesar of Rome's Warparty turned back home before the Battle.",
				incomeReport(18));
		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 2), 6, true, 2, 3));
	}

	@Test
	void theTripHomeCountsTheTurnsAWarpartyWasDelayed() {
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(),
				List.of(EventsTest.starter("Dust storm"))), Identities.of("Rome", "Carthage"), luck(0, -1));
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		game.endTurn();
		game.endTurn();
		// Delayed by a Turn, it has as far left to go as it has travelled.
		assertThat(game.viewOf("Rome").warparties()).singleElement()
			.extracting(Warparty::turnsLeft, Warparty::turnsTravelled)
			.containsExactly(2, 2);

		recall("Rome", 1);

		assertThat(game.viewOf("Rome").warparties()).singleElement().extracting(Warparty::turnsLeft).isEqualTo(2);
	}

	@Test
	void eachWarpartyIsRecalledByItsOwnNumberAndOnlyOnce() {
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 1)));
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 1)));

		recall("Rome", 2);

		assertThat(game.viewOf("Rome").warparties()).extracting(Warparty::id, Warparty::homeward)
			.containsExactly(tuple(1, false), tuple(2, true));
		assertThat(recall("Rome", 2)).isEqualTo(ActionResult.rejected("That Warparty is already heading home."));
		assertThat(recall("Rome", 3)).isEqualTo(ActionResult.rejected("You have no such Warparty abroad."));
		assertThat(recall("Carthage", 1)).isEqualTo(ActionResult.rejected("You have no such Warparty abroad."));
	}

	@Test
	void aWarpartyHeadingHomeFromItsBattleCannotBeRecalled() {
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		for (int i = 0; i < 4; i++) {
			game.endTurn();
		}
		assertThat(game.viewOf("Rome").warparties()).singleElement().extracting(Warparty::homeward).isEqualTo(true);

		assertThat(recall("Rome", 1)).isEqualTo(ActionResult.rejected("That Warparty is already heading home."));
	}

	/**
	 * A Warparty of Units with Strength 1 each, sent with the given number and
	 * having travelled the given Turns, then left with the given number.
	 */
	static Warparty travelledAndLeftWith(int sent, int travelled, int left) {
		Warparty warparty = Warparty.sent(1, "Carthage", Map.of("Soldier", sent), SOLDIERS);
		for (int i = 0; i < travelled; i++) {
			warparty = warparty.travelled();
		}
		return warparty.with(Map.of("Soldier", left), SOLDIERS);
	}

	@Test
	void aWarpartyThatHasLostNoStrengthTakesAsLongToGetHomeAsItHasTravelled() {
		assertThat(travelledAndLeftWith(50, 4, 50).turnedBack().turnsLeft()).isEqualTo(4);
	}

	@Test
	void aWarpartyThatHasLostStrengthWithinItsBracketTakesAsLongToGetHomeAsItHasTravelled() {
		assertThat(travelledAndLeftWith(80, 4, 50).turnedBack().turnsLeft()).isEqualTo(4);
	}

	@Test
	void aWarpartyThatHasLostStrengthAcrossABracketGetsHomeFasterInProportion() {
		// 4 Turns × 3 Turns for Strength 5 ÷ 6 Turns for Strength 50
		assertThat(travelledAndLeftWith(50, 4, 5).turnedBack().turnsLeft()).isEqualTo(2);
	}

	@Test
	void theFasterTripHomeIsRoundedUp() {
		// 4 Turns × 4 Turns for Strength 20 ÷ 6 Turns for Strength 50 = 2⅔
		assertThat(travelledAndLeftWith(50, 4, 20).turnedBack().turnsLeft()).isEqualTo(3);
	}

	/**
	 * Has Rome send Soldier ×50 to Carthage and Carthage send Soldier ×67 to
	 * Rome, and has them fight as they meet, leaving Rome's Warparty weaker
	 * than when it was sent.
	 */
	void romeLosesStrengthInAnEncounter(String... nations) {
		Rigged luck = new Rigged();
		game = new Game(SOLDIERS,
				Identities.of(nations), luck);
		armyOf("Rome", "Soldier", 50);
		armyOf("Carthage", "Soldier", 67);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Soldier", 50)));
		game.act("Carthage", new Action.SendWarparty("Rome", Map.of("Soldier", 67)));
		luck.then(0);
		game.endTurn();
		game.act("Rome", new Action.AnswerEncounter(1, true));
		game.endTurn();
	}

	@Test
	void aWarpartyRecalledAfterLosingStrengthInAnEncounterGetsHomeFaster() {
		romeLosesStrengthInAnEncounter("Rome", "Carthage");
		assertThat(game.viewOf("Rome").warparties()).singleElement()
			.extracting(Warparty::strength, Warparty::turnsTravelled)
			.containsExactly(5.0, 2);

		recall("Rome", 1);

		// 2 Turns × 3 Turns for Strength 5 ÷ 6 Turns for Strength 50
		assertThat(game.viewOf("Rome").warparties()).singleElement().extracting(Warparty::turnsLeft).isEqualTo(1);
	}

	@Test
	void warpartiesHeadingToARemovedNationTurnHomeFasterIfTheyHaveLostStrength() {
		romeLosesStrengthInAnEncounter("Rome", "Carthage", "Egypt");

		game.remove("Carthage");

		assertThat(game.viewOf("Rome").warparties()).singleElement()
			.extracting(Warparty::homeward, Warparty::turnsLeft)
			.containsExactly(true, 1);
	}

}
