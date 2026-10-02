package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.RULESET;
import static org.assertj.core.api.Assertions.assertThat;

class TributeTest {

	Game game = new Game(RULESET, List.of("Rome", "Carthage"), EVEN_LUCK);

	/**
	 * Has a Warparty of Warrior ×2 from Rome arrive at Carthage, to be fought
	 * as this Turn ends.
	 */
	@BeforeEach
	void romeArrivesAtCarthage() {
		game.act("Rome", new Action.TrainUnits("Warrior", 2));
		game.endTurn();
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		game.endTurn();
		game.endTurn();
		game.endTurn();
	}

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	ActionResult offer(String nation, String attacker, int warparty, int gold, int population) {
		return game.act(nation, new Action.OfferTribute(attacker, warparty, gold, population));
	}

	ActionResult answer(String nation, int warparty, boolean accept) {
		return game.act(nation, new Action.AnswerTribute(warparty, accept));
	}

	@Test
	void aDefenderSeesTheWarpartiesThatArrivedAtItsNationAndCanOfferTributeWhichTheAttackerSeesAtOnce() {
		assertThat(game.viewOf("Carthage").arrivals()).containsExactly(new ArrivedWarparty("Rome", 1));

		assertThat(offer("Carthage", "Rome", 1, 20, 3)).isEqualTo(ActionResult.ACCEPTED);

		Tribute offered = new Tribute("Carthage", "Rome", 1, 20, 3, Tribute.Answer.NONE);
		assertThat(game.viewOf("Rome").tributes()).containsExactly(offered);
		assertThat(game.viewOf("Carthage").tributes()).containsExactly(offered);
	}

	@Test
	void tributeCanOnlyBeOfferedOnceForAWarpartyThatArrivedAtTheNationAndMustBeAffordable() {
		int gold = game.viewOf("Carthage").gold();
		int population = game.viewOf("Carthage").population();

		assertThat(offer("Rome", "Rome", 1, 10, 0))
			.isEqualTo(ActionResult.rejected("No such Warparty from Rome has arrived at your Nation."));
		assertThat(offer("Carthage", "Rome", 2, 10, 0))
			.isEqualTo(ActionResult.rejected("No such Warparty from Rome has arrived at your Nation."));
		assertThat(offer("Carthage", "Egypt", 1, 10, 0))
			.isEqualTo(ActionResult.rejected("No such Warparty from Egypt has arrived at your Nation."));
		assertThat(offer("Carthage", "Rome", 1, 0, 0))
			.isEqualTo(ActionResult.rejected("Offer some Gold or Population as Tribute."));
		assertThat(offer("Carthage", "Rome", 1, -5, 3))
			.isEqualTo(ActionResult.rejected("Offer some Gold or Population as Tribute."));
		assertThat(offer("Carthage", "Rome", 1, gold + 1, 0)).isEqualTo(
				ActionResult.rejected("You have only " + gold + " Gold and " + population + " Population to offer."));
		assertThat(offer("Carthage", "Rome", 1, 0, population + 1)).isEqualTo(
				ActionResult.rejected("You have only " + gold + " Gold and " + population + " Population to offer."));
		assertThat(game.viewOf("Rome").tributes()).isEmpty();

		assertThat(offer("Carthage", "Rome", 1, 10, 0)).isEqualTo(ActionResult.ACCEPTED);

		assertThat(offer("Carthage", "Rome", 1, 20, 0))
			.isEqualTo(ActionResult.rejected("You have already offered Rome Tribute for that Warparty."));
	}

	@Test
	void noTributeCanBeOfferedForAWarpartyStillTravelling() {
		game.act("Rome", new Action.RecallWarparty(1));
		game.endTurn();
		game.endTurn();
		game.endTurn();
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		game.endTurn();

		assertThat(game.viewOf("Carthage").arrivals()).isEmpty();
		assertThat(offer("Carthage", "Rome", 2, 10, 0))
			.isEqualTo(ActionResult.rejected("No such Warparty from Rome has arrived at your Nation."));
	}

	@Test
	void anOfferForAWarpartyItsAttackerRecallsIsCancelled() {
		offer("Carthage", "Rome", 1, 10, 0);

		game.act("Rome", new Action.RecallWarparty(1));

		assertThat(game.viewOf("Carthage").tributes()).isEmpty();
		assertThat(game.viewOf("Rome").tributes()).isEmpty();
		game.endTurn();
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Tribute"));
	}

	@Test
	void tributeOfferedToANationRemovedFromTheGameIsCancelled() {
		game = new Game(RULESET, List.of("Rome", "Carthage", "Egypt"), EVEN_LUCK);
		romeArrivesAtCarthage();
		offer("Carthage", "Rome", 1, 10, 0);

		game.remove("Rome");

		assertThat(game.viewOf("Carthage").tributes()).isEmpty();
		assertThat(game.viewOf("Carthage").arrivals()).isEmpty();
	}

	@Test
	void acceptingTributeTransfersItAtOnceAndRecallsTheWarpartySoNoBattleIsFought() {
		NationView romeBefore = game.viewOf("Rome");
		NationView carthageBefore = game.viewOf("Carthage");
		offer("Carthage", "Rome", 1, 20, 3);

		assertThat(answer("Rome", 1, true)).isEqualTo(ActionResult.ACCEPTED);

		NationView rome = game.viewOf("Rome");
		NationView carthage = game.viewOf("Carthage");
		assertThat(rome.gold()).isEqualTo(romeBefore.gold() + 20);
		assertThat(rome.population()).isEqualTo(romeBefore.population() + 3);
		assertThat(carthage.gold()).isEqualTo(carthageBefore.gold() - 20);
		assertThat(carthage.population()).isEqualTo(carthageBefore.population() - 3);
		assertThat(rome.warparties()).containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 2), true, 3, 3));
		assertThat(carthage.arrivals()).isEmpty();
		assertThat(carthage.tributes())
			.containsExactly(new Tribute("Carthage", "Rome", 1, 20, 3, Tribute.Answer.ACCEPTED));

		game.endTurn();

		assertThat(report("Rome")).contains(
				"You accepted Carthage's Tribute of 20 Gold and 3 Population, and your Warparty of Warrior ×2 turned back home.")
			.noneMatch(line -> line.contains("fought"));
		assertThat(report("Carthage")).contains(
				"Rome accepted your Tribute of 20 Gold and 3 Population, and its Warparty turned back home before the Battle.")
			.noneMatch(line -> line.contains("fought") || line.contains("met no Army"));
		assertThat(game.viewOf("Rome").tributes()).isEmpty();
	}

	@Test
	void acceptingFailsIfTheDefenderCanNoLongerAffordTheTributeAndTheOfferStillStands() {
		int gold = game.viewOf("Carthage").gold();
		offer("Carthage", "Rome", 1, gold, 0);
		game.act("Carthage", new Action.TrainUnits("Warrior", 1));

		assertThat(answer("Rome", 1, true)).isEqualTo(ActionResult.rejected("Carthage can no longer afford its Tribute of "
				+ gold + " Gold."));

		assertThat(game.viewOf("Rome").warparties()).singleElement().extracting(Warparty::awaitingBattle).isEqualTo(true);
		assertThat(game.viewOf("Rome").tributes()).singleElement()
			.extracting(Tribute::answer)
			.isEqualTo(Tribute.Answer.NONE);
	}

	@Test
	void refusingTributeLeavesTheBattleToGoAheadAndTheDefenderIsToldAtOnce() {
		int gold = game.viewOf("Carthage").gold();
		offer("Carthage", "Rome", 1, 20, 0);

		assertThat(answer("Rome", 1, false)).isEqualTo(ActionResult.ACCEPTED);

		assertThat(game.viewOf("Carthage").tributes())
			.containsExactly(new Tribute("Carthage", "Rome", 1, 20, 0, Tribute.Answer.REFUSED));
		assertThat(answer("Rome", 1, true))
			.isEqualTo(ActionResult.rejected("No Tribute awaits your answer for that Warparty."));
		game.endTurn();
		assertThat(report("Rome")).contains("You refused Carthage's Tribute of 20 Gold.",
				"Your Warparty of Warrior ×2 met no Army at Carthage.");
		assertThat(report("Carthage")).contains("Rome refused your Tribute of 20 Gold.",
				"Rome's Warparty of Warrior ×2 met no Army at your Nation.");
		assertThat(game.viewOf("Carthage").gold()).isLessThan(gold);
	}

	@Test
	void tributeLeftUnansweredLapsesAsTheTurnEndsAndTheBattleGoesAhead() {
		offer("Carthage", "Rome", 1, 0, 2);

		game.endTurn();

		assertThat(report("Rome")).contains("You did not answer Carthage's Tribute of 2 Population in time.",
				"Your Warparty of Warrior ×2 met no Army at Carthage.");
		assertThat(report("Carthage")).contains(
				"Rome did not answer your Tribute of 2 Population in time, so the Battle went ahead.",
				"Rome's Warparty of Warrior ×2 met no Army at your Nation.");
		assertThat(game.viewOf("Carthage").tributes()).isEmpty();
	}

}
