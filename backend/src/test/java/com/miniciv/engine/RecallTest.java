package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.incomeReport;
import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.RULESET;
import static com.miniciv.engine.GameTest.luck;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

class RecallTest {

	Game game = new Game(RULESET, List.of("Rome", "Carthage"), EVEN_LUCK);

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
			.contains("A Warparty from Carthage arrived at your Nation; the Battle will be fought as this Turn ends.");

		recall("Rome", 1);
		game.endTurn();

		assertThat(report("Rome")).containsSubsequence("You Recalled your Warparty of Warrior ×2 heading to Carthage.",
				"Your Warparty came home from Carthage and Warrior ×2 rejoined your Army.",
				"Your Army of Warrior ×2 fought Carthage's Warparty of Warrior ×2. You lost Warrior ×1 and Carthage lost Warrior ×1.");
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
		assertThat(report("Carthage")).containsExactly("Rome's Warparty turned back home before the Battle.",
				incomeReport(18));
		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 2), 6, true, 2, 3));
	}

	@Test
	void theTripHomeCountsTheTurnsAWarpartyWasDelayed() {
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(),
				List.of(EventsTest.starter("Dust storm"))), List.of("Rome", "Carthage"), luck(0, -1));
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

}
