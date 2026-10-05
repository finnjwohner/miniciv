package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.Rigged;
import static com.miniciv.engine.GameTest.luck;
import static com.miniciv.engine.ScoutsTest.draw;
import static com.miniciv.engine.SpiesTest.RULESET;
import static org.assertj.core.api.Assertions.assertThat;

class LocatedTest {

	Rigged luck = new Rigged();

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage", "Egypt"), luck);

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	/**
	 * The lines of the Spies and Scouts section of a Nation's Turn Report.
	 */
	List<String> spiesAndScouts(String nation) {
		return game.viewOf(nation)
			.turnReport()
			.sections()
			.stream()
			.filter(section -> section.kind() == TurnReport.Kind.SPIES_AND_SCOUTS)
			.flatMap(section -> section.lines().stream().map(TurnReport.Line::text))
			.toList();
	}

	void armyOf(String nation, String unit, int count) {
		assertThat(game.act(nation, new Action.TrainUnits(unit, count))).isEqualTo(ActionResult.ACCEPTED);
		while (!game.viewOf(nation).training().isEmpty()) {
			game.endTurn();
		}
	}

	ActionResult locate(String nation, String target, int scouts) {
		return game.act(nation, new Action.SetTask("LOCATE", target, scouts));
	}

	@Test
	void noNationHasLocatedAnyOtherAtTheStartOfTheGame() {
		for (String nation : List.of("Rome", "Carthage", "Egypt")) {
			assertThat(game.viewOf(nation).located()).isEmpty();
			assertThat(game.viewOf(nation).tasks()).isEmpty();
		}
	}

	@Test
	void spiesAndWarpartiesCannotBeSentToANationNotYetLocated() {
		armyOf("Rome", "Warrior", 1);
		armyOf("Rome", "Spy", 1);
		ActionResult notLocated = ActionResult.rejected("You have not Located Carthage yet; set your Scouts to Locate it.");

		assertThat(game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 1)))).isEqualTo(notLocated);
		assertThat(game.act("Rome", new Action.SendSpy("Carthage"))).isEqualTo(notLocated);

		assertThat(game.viewOf("Rome").warparties()).isEmpty();
		assertThat(game.viewOf("Rome").spies()).isEmpty();
	}

	@Test
	void aLocateTaskTakesIdleScouts() {
		armyOf("Rome", "Scout", 5);

		assertThat(locate("Rome", "Carthage", 3)).isEqualTo(ActionResult.ACCEPTED);

		NationView rome = game.viewOf("Rome");
		assertThat(rome.scouts()).isEqualTo(5);
		assertThat(rome.idleScouts()).isEqualTo(2);
		assertThat(rome.tasks()).containsExactly(new Task(1, Task.Kind.LOCATE, "Carthage", 3));
	}

	@Test
	void aLocateTaskThatSucceedsLocatesItsNationEndsAndLeavesItsScoutsIdle() {
		armyOf("Rome", "Scout", 2);
		locate("Rome", "Carthage", 2);
		luck.then(0.1);

		game.endTurn();

		NationView rome = game.viewOf("Rome");
		assertThat(rome.located()).containsExactly("Carthage");
		assertThat(rome.tasks()).isEmpty();
		assertThat(rome.idleScouts()).isEqualTo(2);
		assertThat(report("Rome")).contains("Your Scouts Located Carthage, so you can now send Spies and Warparties there.");
		assertThat(game.viewOf("Carthage").located()).isEmpty();
	}

	@Test
	void aLocatedNationCanBeSentSpiesAndWarparties() {
		armyOf("Rome", "Scout", 1);
		armyOf("Rome", "Warrior", 1);
		armyOf("Rome", "Spy", 1);
		locate("Rome", "Carthage", 1);
		luck.then(0);
		game.endTurn();

		assertThat(game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 1))))
			.isEqualTo(ActionResult.ACCEPTED);
		assertThat(game.act("Rome", new Action.SendSpy("Carthage"))).isEqualTo(ActionResult.ACCEPTED);
	}

	@Test
	void eachScoutOnALocateTaskAddsAFifteenPercentChanceEachTurn() {
		// 0.15 for one Scout, and 1 - 0.85 × 0.85 = 0.2775 for two.
		armyOf("Rome", "Scout", 3);
		locate("Rome", "Carthage", 1);
		locate("Rome", "Egypt", 2);
		luck.then(0.2, 0.2);

		game.endTurn();

		NationView rome = game.viewOf("Rome");
		assertThat(rome.located()).containsExactly("Egypt");
		assertThat(rome.tasks()).containsExactly(new Task(1, Task.Kind.LOCATE, "Carthage", 1));
		assertThat(rome.idleScouts()).isEqualTo(2);
	}

	@Test
	void aLocateTaskRunsTurnAfterTurnUntilItSucceeds() {
		armyOf("Rome", "Scout", 1);
		locate("Rome", "Carthage", 1);
		game.endTurn();
		game.endTurn();
		assertThat(game.viewOf("Rome").located()).isEmpty();
		assertThat(report("Rome")).noneMatch(line -> line.contains("Located"));
		luck.then(0.1);

		game.endTurn();

		assertThat(game.viewOf("Rome").located()).containsExactly("Carthage");
	}

	@Test
	void locatingIsPermanent() {
		armyOf("Rome", "Scout", 1);
		locate("Rome", "Carthage", 1);
		luck.then(0);
		game.endTurn();

		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}

		assertThat(game.viewOf("Rome").located()).containsExactly("Carthage");
	}

	@Test
	void aLocateTaskNeedsEnoughIdleScouts() {
		armyOf("Rome", "Scout", 3);
		locate("Rome", "Carthage", 2);

		assertThat(locate("Rome", "Egypt", 2)).isEqualTo(ActionResult.rejected("You have only 1 Idle Scout."));
		assertThat(locate("Rome", "Egypt", 0)).isEqualTo(ActionResult.rejected("Set at least one Scout to the Task."));
		assertThat(game.viewOf("Rome").tasks()).hasSize(1);
	}

	@Test
	void locateIsRejectedForANationAlreadyLocatedOrBeingLocated() {
		armyOf("Rome", "Scout", 3);
		locate("Rome", "Carthage", 1);
		luck.then(0);
		game.endTurn();
		locate("Rome", "Egypt", 1);

		assertThat(locate("Rome", "Carthage", 1)).isEqualTo(ActionResult.rejected("You have already Located Carthage."));
		assertThat(locate("Rome", "Egypt", 1)).isEqualTo(ActionResult.rejected("Your Scouts are already Locating Egypt."));
	}

	@Test
	void locateIsRejectedForANationOutOfTheGameOrNoOtherNation() {
		armyOf("Rome", "Scout", 3);
		game.remove("Egypt");

		assertThat(locate("Rome", "Egypt", 1)).isEqualTo(ActionResult.rejected("Egypt has been removed from the Game."));
		assertThat(locate("Rome", "Rome", 1)).isEqualTo(ActionResult.rejected("Your Scouts cannot Locate your own Nation."));
		assertThat(locate("Rome", "Atlantis", 1)).isEqualTo(ActionResult.rejected("There is no Nation called Atlantis."));
		assertThat(game.act("Rome", new Action.SetTask("DANCE", "Carthage", 1)))
			.isEqualTo(ActionResult.rejected("There is no Task called DANCE."));
		assertThat(game.viewOf("Rome").tasks()).isEmpty();
	}

	@Test
	void aLocateTaskWhoseNationLeavesTheGameEndsWithItsScoutsIdle() {
		armyOf("Rome", "Scout", 2);
		locate("Rome", "Egypt", 2);

		game.remove("Egypt");

		assertThat(game.viewOf("Rome").tasks()).isEmpty();
		assertThat(game.viewOf("Rome").idleScouts()).isEqualTo(2);
		game.endTurn();
		assertThat(report("Rome")).contains("Your Scouts searching for Egypt are Idle again, as Egypt was removed from the Game.");
	}

	@Test
	void aLocateTaskWhoseNationIsConqueredEndsWithItsScoutsIdle() {
		UnitType giant = new UnitType("Giant", 10, 1, 1, 1, 100, List.of());
		game = Locating.in(new Ruleset(List.of(giant, GameTest.SCOUT), List.of(), List.of()),
				Identities.of("Rome", "Carthage", "Egypt"), GameTest.EVEN_LUCK)
			.locate("Carthage", "Egypt")
			.game();
		armyOf("Carthage", "Giant", 1);
		armyOf("Rome", "Scout", 1);
		game.act("Rome", new Action.SetTask("LOCATE", "Egypt", 1));
		game.act("Carthage", new Action.SendWarparty("Egypt", Map.of("Giant", 1)));
		for (int turn = 1; turn <= 8 && game.inGame("Egypt"); turn++) {
			game.endTurn();
		}

		assertThat(game.inGame("Egypt")).isFalse();
		assertThat(game.viewOf("Rome").tasks()).isEmpty();
		assertThat(game.viewOf("Rome").idleScouts()).isEqualTo(1);
		assertThat(report("Rome")).contains("Your Scouts searching for Egypt are Idle again, as Egypt was conquered.");
	}

	@Test
	void aTaskCanBeCancelledReturningItsScoutsToIdle() {
		armyOf("Rome", "Scout", 3);
		locate("Rome", "Carthage", 2);
		locate("Rome", "Egypt", 1);

		assertThat(game.act("Rome", new Action.CancelTask(1))).isEqualTo(ActionResult.ACCEPTED);

		assertThat(game.viewOf("Rome").tasks()).containsExactly(new Task(2, Task.Kind.LOCATE, "Egypt", 1));
		assertThat(game.viewOf("Rome").idleScouts()).isEqualTo(2);
		assertThat(game.act("Rome", new Action.CancelTask(1))).isEqualTo(ActionResult.rejected("You have no such Task."));
	}

	@Test
	void theDefenderLocatesEveryAttackerInABattle() {
		game = Locating.in(RULESET, Identities.of("Rome", "Carthage", "Egypt"), GameTest.EVEN_LUCK)
			.locate("Rome", "Carthage")
			.locate("Egypt", "Carthage")
			.game();
		armyOf("Rome", "Warrior", 1);
		armyOf("Egypt", "Warrior", 1);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 1)));
		game.act("Egypt", new Action.SendWarparty("Carthage", Map.of("Warrior", 1)));
		for (int turn = 1; turn <= 5 && report("Carthage").stream().noneMatch(line -> line.contains("met no Army")); turn++) {
			assertThat(game.viewOf("Carthage").located()).isEmpty();
			game.endTurn();
		}

		assertThat(game.viewOf("Carthage").located()).containsExactly("Rome", "Egypt");
		assertThat(report("Carthage")).contains("The Battle revealed where Rome lies; you have Located it.",
				"The Battle revealed where Egypt lies; you have Located it.");
		assertThat(spiesAndScouts("Carthage")).contains("The Battle revealed where Rome lies; you have Located it.");
		assertThat(game.viewOf("Rome").located()).containsExactly("Carthage");
	}

	@Test
	void aLocateTaskEndsWithItsScoutsIdleWhenItsNationIsLocatedAnotherWay() {
		game = Locating.in(RULESET, Identities.of("Rome", "Carthage"), luck).locate("Rome", "Carthage").game();
		armyOf("Carthage", "Scout", 2);
		armyOf("Rome", "Warrior", 1);
		locate("Carthage", "Rome", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 1)));
		while (game.viewOf("Carthage").located().isEmpty()) {
			game.endTurn();
		}

		assertThat(game.viewOf("Carthage").tasks()).isEmpty();
		assertThat(game.viewOf("Carthage").idleScouts()).isEqualTo(2);
		game.endTurn();
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Located"));
	}

	@Test
	void aCaughtSpyThatConfessesLocatesItsSenderForTheNationThatCaughtIt() {
		game = Locating.in(RULESET, Identities.of("Rome", "Carthage"), CatchingSpiesTest.CAUGHT_AND_CONFESSES)
			.locate("Rome", "Carthage")
			.game();
		armyOf("Carthage", "Spy", 1);
		armyOf("Rome", "Spy", 1);
		game.act("Rome", new Action.SendSpy("Carthage"));
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}

		assertThat(report("Carthage")).contains("Your Spies caught a Spy in your Nation, and it confessed that Caesar of Rome sent it.",
				"From its confession, you have Located Rome.");
		assertThat(game.viewOf("Carthage").located()).containsExactly("Rome");
	}

	@Test
	void aCaughtSpyThatDoesNotConfessLocatesNoOne() {
		game = Locating.in(RULESET, Identities.of("Rome", "Carthage"), CatchingSpiesTest.CAUGHT_AND_SILENT)
			.locate("Rome", "Carthage")
			.game();
		armyOf("Carthage", "Spy", 1);
		armyOf("Rome", "Spy", 1);
		game.act("Rome", new Action.SendSpy("Carthage"));
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}

		assertThat(report("Carthage")).contains("Your Spies caught a Spy in your Nation, and it would not say who sent it.");
		assertThat(game.viewOf("Carthage").located()).isEmpty();
	}

	@Test
	void tributeAndTheMapStillWorkTowardANationNotLocated() {
		game = Locating.in(RULESET, Identities.of("Rome", "Carthage"), luck(draw(0.99)))
			.locate("Rome", "Carthage")
			.game();
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		while (game.viewOf("Carthage").arrivals().isEmpty()) {
			game.endTurn();
		}

		assertThat(game.viewOf("Carthage").located()).isEmpty();
		assertThat(game.viewOf("Carthage").otherNations()).containsExactly("Rome");
		assertThat(game.act("Carthage", new Action.OfferTribute("Rome", 1, 5, 0))).isEqualTo(ActionResult.ACCEPTED);
	}

}
