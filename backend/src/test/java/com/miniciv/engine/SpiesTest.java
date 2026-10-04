package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.ARCHER;
import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.FARM;
import static com.miniciv.engine.GameTest.HORSEMAN;
import static com.miniciv.engine.GameTest.SCOUT;
import static com.miniciv.engine.GameTest.WALLS;
import static com.miniciv.engine.GameTest.WARRIOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class SpiesTest {

	static final UnitType SPY = new UnitType("Spy", 10, 1, 1, 1, 0.1, List.of(), false, true);

	static final Ruleset RULESET = new Ruleset(List.of(WARRIOR, ARCHER, HORSEMAN, SCOUT, SPY), List.of(FARM, WALLS),
			List.of());

	Game game = Locating.by("Rome", RULESET, Identities.of("Rome", "Carthage", "Egypt"), EVEN_LUCK);

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

	@Test
	void spiesAreTrainedLikeOtherUnitsAndJoinTheArmy() {
		game.act("Rome", new Action.TrainUnits("Spy", 1));
		game.endTurn();

		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Spy", 1));
		assertThat(report("Rome")).contains("1 Spy finished training and joined your Army.");
	}

	@Test
	void spiesAreSentOnTheirOwnAndNotInAWarparty() {
		armyOf("Rome", "Spy", 1);
		armyOf("Rome", "Warrior", 1);

		assertThat(game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 1, "Spy", 1))))
			.isEqualTo(ActionResult.rejected("Spies are sent on their own, not in a Warparty."));
		assertThat(game.viewOf("Rome").warparties()).isEmpty();
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Spy", 1), entry("Warrior", 1));
	}

	@Test
	void aSpySentLeavesTheArmyAndTravelsForTheBaseTravelTime() {
		armyOf("Rome", "Spy", 2);

		assertThat(sendSpy("Rome", "Carthage")).isEqualTo(ActionResult.ACCEPTED);

		NationView rome = game.viewOf("Rome");
		assertThat(rome.army()).containsExactly(entry("Spy", 1));
		assertThat(rome.spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 3, 0, null, 0));
		game.endTurn();
		game.endTurn();
		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 1, 2, null, 0));
		assertThat(report("Rome")).noneMatch(line -> line.contains("Spy"));

		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 0, 3, null, 0));
		assertThat(report("Rome")).contains("Your Spy arrived at Carthage.");
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Spy") || line.contains("Rome"));
	}

	@Test
	void aSpyStaysInPlaceOnceArrived() {
		armyOf("Rome", "Spy", 1);
		spyArrivesAtCarthage();

		for (int turn = 1; turn <= 5; turn++) {
			game.endTurn();
		}

		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 0, 3, null, 0));
		assertThat(game.viewOf("Rome").army()).isEmpty();
	}

	@Test
	void aSpyAbroadCostsItsUpkeep() {
		armyOf("Rome", "Spy", 1);
		sendSpy("Rome", "Carthage");

		assertThat(game.viewOf("Rome").upkeep()).isEqualTo(1);
		game.endTurn();
		assertThat(report("Rome")).contains("Your Units' upkeep cost 1 Gold.");
	}

	@Test
	void aSpyAbroadDesertsWhenItsUpkeepCannotBePaid() {
		UnitType costly = new UnitType("Spy", 0, 1, 1, 60, 0, List.of(), false, true);
		game = Locating.by("Rome", new Ruleset(List.of(costly), List.of(FARM), List.of()), Identities.of("Rome", "Carthage"), EVEN_LUCK);
		game.act("Rome", new Action.TrainUnits("Spy", 1));
		game.endTurn();
		sendSpy("Rome", "Carthage");
		// Next Turn's Gold is what Rome has plus what its Population earns.
		NationView rome = game.viewOf("Rome");
		assertThat(rome.gold() + rome.population()).isLessThan(60);

		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(report("Rome")).contains("You could not pay your Units' upkeep, so your Spy sent to Carthage deserted.");
	}

	@Test
	void aSpyIsSentOnlyToAnotherNationInTheGameFromTheArmy() {
		assertThat(sendSpy("Rome", "Carthage")).isEqualTo(ActionResult.rejected("Your Army has no Spy."));
		armyOf("Rome", "Spy", 1);

		assertThat(sendSpy("Rome", "Rome")).isEqualTo(ActionResult.rejected("A Spy must be sent to another Nation."));
		assertThat(sendSpy("Rome", "Atlantis")).isEqualTo(ActionResult.rejected("There is no Nation called Atlantis."));
		game.remove("Egypt");
		assertThat(sendSpy("Rome", "Egypt")).isEqualTo(ActionResult.rejected("Egypt has been removed from the Game."));
		assertThat(game.viewOf("Rome").spies()).isEmpty();
	}

	@Test
	void aSpyInANationThatIsConqueredHeadsHomeAndRejoinsTheArmy() {
		UnitType giant = new UnitType("Giant", 10, 1, 1, 1, 100, List.of());
		game = Locating.everyone(new Ruleset(List.of(giant, SPY), List.of(), List.of()), Identities.of("Rome", "Carthage", "Egypt"),
				EVEN_LUCK);
		armyOf("Rome", "Spy", 1);
		armyOf("Egypt", "Giant", 1);
		spyArrivesAtCarthage();
		game.act("Egypt", new Action.SendWarparty("Carthage", Map.of("Giant", 1)));
		for (int turn = 1; turn <= 8; turn++) {
			game.endTurn();
		}
		assertThat(game.viewOf("Carthage").conqueredBy()).isEqualTo("Egypt");

		assertThat(report("Rome")).contains("Your Spy in Carthage is heading home, as Carthage was conquered.");
		assertThat(game.viewOf("Rome").spies()).containsExactly(new Spy(1, "Spy", "Carthage", true, 3, 3, null, 0));
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}

		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Spy", 1));
		assertThat(report("Rome")).contains("Your Spy came home from Carthage and rejoined your Army.");
	}

	@Test
	void aSpyHeadingToANationThatIsRemovedTurnsBackHome() {
		armyOf("Rome", "Spy", 1);
		sendSpy("Rome", "Carthage");
		game.endTurn();

		game.remove("Carthage");
		game.endTurn();

		assertThat(report("Rome")).contains("Your Spy heading to Carthage turned back home, as Carthage was removed from the Game.");
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Spy", 1));
	}

	@Test
	void aNationRemovedFromTheGameLosesItsSpiesAbroad() {
		armyOf("Rome", "Spy", 1);
		spyArrivesAtCarthage();

		game.remove("Rome");

		assertThat(game.viewOf("Rome").spies()).isEmpty();
	}

}
