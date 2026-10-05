package com.miniciv.engine;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.Rigged;
import static com.miniciv.engine.GameTest.luck;
import static com.miniciv.engine.ScoutsTest.ALWAYS;
import static com.miniciv.engine.ScoutsTest.draw;
import static com.miniciv.engine.SpiesTest.RULESET;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scouts set to Watch for Warparties and Hunt Travelling Spies, and Scouts
 * left Idle.
 */
class ScoutTasksTest {

	Game game;

	void play(RandomGenerator luck) {
		game = Locating.by("Carthage", RULESET, Identities.of("Rome", "Carthage", "Egypt"), luck);
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

	ActionResult watch(String nation) {
		return game.act(nation, new Action.SetTask("WATCH", null, 1));
	}

	ActionResult hunt(String nation, int scouts) {
		return game.act(nation, new Action.SetTask("HUNT", null, scouts));
	}

	void sendWarparty(String nation, String target) {
		assertThat(game.act(nation, new Action.SendWarparty(target, Map.of("Warrior", 1))))
			.isEqualTo(ActionResult.ACCEPTED);
	}

	void sendSpy(String nation, String target) {
		assertThat(game.act(nation, new Action.SendSpy(target))).isEqualTo(ActionResult.ACCEPTED);
	}

	@Test
	void newlyTrainedScoutsAreIdle() {
		play(GameTest.EVEN_LUCK);
		armyOf("Rome", "Scout", 3);

		assertThat(game.viewOf("Rome").scouts()).isEqualTo(3);
		assertThat(game.viewOf("Rome").idleScouts()).isEqualTo(3);
		assertThat(game.viewOf("Rome").tasks()).isEmpty();
	}

	@Test
	void idleScoutsDiscoverNoWarparties() {
		play(ALWAYS);
		armyOf("Rome", "Scout", 3);
		armyOf("Carthage", "Warrior", 1);

		sendWarparty("Carthage", "Egypt");
		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.startsWith("Your Scouts"));
	}

	@Test
	void idleScoutsCatchNoSpies() {
		play(ALWAYS);
		armyOf("Rome", "Scout", 3);
		armyOf("Carthage", "Spy", 1);

		sendSpy("Carthage", "Egypt");
		game.endTurn();

		assertThat(game.viewOf("Carthage").spies()).hasSize(1);
		assertThat(report("Rome")).noneMatch(line -> line.contains("caught"));
	}

	@Test
	void aWatchTaskTakesExactlyOneScout() {
		play(GameTest.EVEN_LUCK);
		armyOf("Rome", "Scout", 3);

		assertThat(game.act("Rome", new Action.SetTask("WATCH", null, 2)))
			.isEqualTo(ActionResult.rejected("Watching for Warparties takes exactly 1 Scout."));
		assertThat(watch("Rome")).isEqualTo(ActionResult.ACCEPTED);
		assertThat(watch("Rome")).isEqualTo(ActionResult.ACCEPTED);

		assertThat(game.viewOf("Rome").tasks()).containsExactly(new Task(1, Task.Kind.WATCH, null, 1),
				new Task(2, Task.Kind.WATCH, null, 1));
		assertThat(game.viewOf("Rome").idleScouts()).isEqualTo(1);
	}

	@Test
	void aWatchTaskDiscoversAnotherNationsWarpartyAbroad() {
		play(ALWAYS);
		armyOf("Rome", "Scout", 1);
		armyOf("Carthage", "Warrior", 1);
		watch("Rome");

		sendWarparty("Carthage", "Egypt");
		game.endTurn();

		assertThat(report("Rome")).contains("Your Scouts discovered Hannibal of Carthage's Warparty of Warrior ×1 heading to Egypt.");
	}

	@Test
	void eachWatchTaskGivesItsOwnChanceOfDiscoveringAWarparty() {
		// A fifth of a chance for one Watch Task, and 1 - 0.8 × 0.8 = 0.36 for
		// two.
		for (int watching = 1; watching <= 2; watching++) {
			play(luck(draw(0.3)));
			armyOf("Rome", "Scout", 2);
			armyOf("Carthage", "Warrior", 1);
			for (int task = 1; task <= watching; task++) {
				watch("Rome");
			}

			sendWarparty("Carthage", "Egypt");
			game.endTurn();

			if (watching == 1) {
				assertThat(report("Rome")).noneMatch(line -> line.startsWith("Your Scouts"));
			}
			else {
				assertThat(report("Rome")).contains("Your Scouts discovered Hannibal of Carthage's Warparty of Warrior ×1 heading to Egypt.");
			}
		}
	}

	@Test
	void aHuntTaskTakesAnyNumberOfScoutsAndCatchesAnotherNationsTravellingSpy() {
		play(CatchingSpiesTest.CAUGHT_AND_DIES);
		armyOf("Rome", "Scout", 3);
		armyOf("Carthage", "Spy", 1);
		assertThat(hunt("Rome", 3)).isEqualTo(ActionResult.ACCEPTED);
		assertThat(game.viewOf("Rome").tasks()).containsExactly(new Task(1, Task.Kind.HUNT, null, 3));

		sendSpy("Carthage", "Egypt");
		game.endTurn();

		assertThat(game.viewOf("Carthage").spies()).isEmpty();
		assertThat(report("Rome")).contains("Your Scouts caught a Spy heading to Egypt, and it died before saying who sent it.");
	}

	@Test
	void eachHuntingScoutGivesItsOwnChanceOfCatchingASpy() {
		// A tenth of a chance for one Scout, and 1 - 0.9 × 0.9 = 0.19 for two.
		for (int hunting = 1; hunting <= 2; hunting++) {
			Rigged luck = new Rigged();
			play(luck);
			armyOf("Rome", "Scout", 2);
			armyOf("Carthage", "Spy", 1);
			hunt("Rome", hunting);

			sendSpy("Carthage", "Egypt");
			luck.then(0.15, 0.1);
			game.endTurn();

			assertThat(game.viewOf("Carthage").spies()).hasSize(hunting == 1 ? 1 : 0);
		}
	}

	@Test
	void watchAndHuntTasksRunUntilCancelled() {
		play(GameTest.EVEN_LUCK);
		armyOf("Rome", "Scout", 3);
		watch("Rome");
		hunt("Rome", 2);

		for (int turn = 1; turn <= 5; turn++) {
			game.endTurn();
		}

		assertThat(game.viewOf("Rome").tasks()).containsExactly(new Task(1, Task.Kind.WATCH, null, 1),
				new Task(2, Task.Kind.HUNT, null, 2));
		assertThat(game.act("Rome", new Action.CancelTask(1))).isEqualTo(ActionResult.ACCEPTED);
		assertThat(game.viewOf("Rome").tasks()).containsExactly(new Task(2, Task.Kind.HUNT, null, 2));
		assertThat(game.viewOf("Rome").idleScouts()).isEqualTo(1);
	}

	@Test
	void scoutsDesertingArePickedAtRandomFromAllScoutsAndATaskLeftWithNoneEnds() {
		UnitType costly = new UnitType("Scout", 0, 0, 1, 13, 0, List.of(), true);
		Rigged luck = new Rigged();
		game = new Game(new Ruleset(List.of(costly), List.of(), List.of()), Identities.of("Rome", "Carthage"), luck);
		armyOf("Rome", "Scout", 4);
		// The 8 Gold left after the first upkeep, and 12 more earned, pay for
		// one Scout's upkeep; the other three desert.
		hunt("Rome", 2);
		watch("Rome");
		// Of the Idle Scout, then the Hunt Task's two, then the Watch Task's
		// one: a Hunting Scout, then the other, then the Watching one.
		luck.then(0.3, 0.4, 0.9);

		game.endTurn();

		assertThat(game.viewOf("Rome").scouts()).isEqualTo(1);
		assertThat(game.viewOf("Rome").idleScouts()).isEqualTo(1);
		assertThat(game.viewOf("Rome").tasks()).isEmpty();
	}

	@Test
	void scoutsLostToAnEventArePickedAtRandomFromAllScouts() {
		Event blizzard = new Event("Blizzard", Event.Subject.SCOUTS, 0.5, null, ScoutEventsTest.scoutsLost(1),
				"A blizzard swept the mountain passes");
		Rigged luck = new Rigged();
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(), List.of(blizzard)),
				Identities.of("Rome", "Carthage"), luck);
		armyOf("Rome", "Scout", 3);
		watch("Rome");
		hunt("Rome", 1);
		// The Blizzard befalls Rome, and the Scout lost is the Watch Task's.
		luck.then(0, 0.5);

		game.endTurn();

		assertThat(report("Rome")).contains("A blizzard swept the mountain passes: 1 Scout was lost in the wilds.");
		assertThat(game.viewOf("Rome").idleScouts()).isEqualTo(1);
		assertThat(game.viewOf("Rome").tasks()).containsExactly(new Task(2, Task.Kind.HUNT, null, 1));
	}

	@Test
	void unknownTasksAreRejected() {
		play(GameTest.EVEN_LUCK);
		armyOf("Rome", "Scout", 1);

		assertThat(game.act("Rome", new Action.SetTask("DIG", null, 1)))
			.isEqualTo(ActionResult.rejected("There is no Task called DIG."));
	}

}
