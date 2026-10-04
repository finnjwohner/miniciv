package com.miniciv.engine;

import java.util.List;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.Rigged;
import static org.assertj.core.api.Assertions.assertThat;

class UnmaskingTest {

	/**
	 * Luck that draws nothing lucky, 0.99, unless told otherwise: a hostile
	 * Mission fails, no Spy is Unmasked and no Spy is caught.
	 */
	Rigged luck = new Rigged();

	Game game = Locating.by("Rome", HostileMissionsTest.RULESET, Identities.of("Rome", "Carthage", "Egypt"), luck);

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	void armyOf(String nation, String unit, int count) {
		assertThat(game.act(nation, new Action.TrainUnits(unit, count))).isEqualTo(ActionResult.ACCEPTED);
		while (!game.viewOf(nation).training().isEmpty()) {
			game.endTurn();
		}
	}

	/**
	 * Trains the given number of Spies in Rome, sends them all to Carthage and
	 * ends Turns until they have arrived.
	 */
	void spiesArriveAtCarthage(int spies) {
		armyOf("Rome", "Spy", spies);
		for (int spy = 1; spy <= spies; spy++) {
			assertThat(game.act("Rome", new Action.SendSpy("Carthage"))).isEqualTo(ActionResult.ACCEPTED);
		}
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}
	}

	List<Spy> spies() {
		return game.viewOf("Rome").spies();
	}

	/**
	 * Sets Rome's first Spy in Carthage to Incite Unrest and ends Turns until
	 * the last one before it ends.
	 */
	void inciteUnrestUntilItsLastTurn() {
		assertThat(game.act("Rome", new Action.SetMission(1, "Incite Unrest"))).isEqualTo(ActionResult.ACCEPTED);
		while (spies().getFirst().missionTurnsLeft() > 1) {
			game.endTurn();
		}
	}

	@Test
	void aFailedHostileMissionUnmasksItsSpyHalfTheTime() {
		spiesArriveAtCarthage(1);
		inciteUnrestUntilItsLastTurn();

		// It fails, is Unmasked, is not caught on the spot.
		luck.then(0.99, 0.49, 0.5);
		game.endTurn();

		assertThat(spies()).singleElement().satisfies(spy -> assertThat(spy.homeward()).isTrue());
	}

	@Test
	void aFailedHostileMissionDoesNotUnmaskItsSpyTheOtherHalf() {
		spiesArriveAtCarthage(1);
		inciteUnrestUntilItsLastTurn();

		luck.then(0.99, 0.5);
		game.endTurn();

		assertThat(spies()).singleElement().satisfies(spy -> assertThat(spy.inPlace()).isTrue());
		assertThat(report("Rome")).noneMatch(line -> line.contains("Unmasked"));
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Spy"));
	}

	@Test
	void aSucceedingHostileMissionUnmasksItsSpyAQuarterOfTheTime() {
		spiesArriveAtCarthage(1);
		inciteUnrestUntilItsLastTurn();

		// It succeeds, its harm is varied by luck, it is Unmasked, and it is not
		// caught on the spot.
		luck.then(0, 0.5, 0.24, 0.5);
		game.endTurn();

		assertThat(spies()).singleElement().satisfies(spy -> assertThat(spy.homeward()).isTrue());
		assertThat(report("Rome")).contains("Your Spy in Carthage succeeded at Incite Unrest.");
	}

	@Test
	void aSucceedingHostileMissionDoesNotUnmaskItsSpyTheOtherThreeQuarters() {
		spiesArriveAtCarthage(1);
		inciteUnrestUntilItsLastTurn();

		luck.then(0, 0.5, 0.25);
		game.endTurn();

		assertThat(spies()).singleElement().satisfies(spy -> assertThat(spy.inPlace()).isTrue());
		assertThat(report("Rome")).noneMatch(line -> line.contains("Unmasked"));
	}

	@Test
	void anIntelMissionNeverUnmasksItsSpy() {
		spiesArriveAtCarthage(1);
		assertThat(game.act("Rome", new Action.SetMission(1, "Report Gold"))).isEqualTo(ActionResult.ACCEPTED);
		while (spies().getFirst().missionTurnsLeft() > 1) {
			game.endTurn();
		}

		luck.always(0);
		game.endTurn();

		assertThat(spies()).singleElement().satisfies(spy -> assertThat(spy.inPlace()).isTrue());
		assertThat(report("Rome")).anyMatch(line -> line.startsWith("Your Spy in Carthage reported that Carthage has"));
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Spy"));
	}

	@Test
	void anUnmaskedSpyThatFleesHeadsHomeAndBothSidesAreTold() {
		spiesArriveAtCarthage(1);
		inciteUnrestUntilItsLastTurn();

		luck.then(0.99, 0, 0.5);
		game.endTurn();

		assertThat(spies()).containsExactly(new Spy(1, "Spy", "Carthage", true, 3, 3, null, 0));
		assertThat(report("Rome")).contains("Your Spy in Carthage failed at Incite Unrest.",
				"Your Spy in Carthage was Unmasked and is fleeing home.")
			.noneMatch(line -> line.contains("is Idle"));
		assertThat(report("Carthage")).contains("A Spy was Unmasked in your Nation and fled home; Caesar of Rome sent it.");
		assertThat(game.viewOf("Carthage").located()).doesNotContain("Rome");
	}

	@Test
	void aFleeingSpyComesHome() {
		spiesArriveAtCarthage(1);
		inciteUnrestUntilItsLastTurn();
		luck.then(0.99, 0, 0.5);
		game.endTurn();

		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}

		assertThat(spies()).isEmpty();
		assertThat(report("Rome")).contains("Your Spy came home from Carthage and rejoined your Army.");
	}

	@Test
	void aFleeingSpyCanBeCaughtWhileTravelling() {
		armyOf("Carthage", "Scout", 1);
		spiesArriveAtCarthage(1);
		inciteUnrestUntilItsLastTurn();
		luck.then(0.99, 0, 0.5);
		game.endTurn();

		// Carthage's Scouts catch it, and it stays silent.
		luck.then(0, 0.5);
		game.endTurn();

		assertThat(spies()).isEmpty();
		assertThat(report("Carthage"))
			.contains("Your Scouts caught a Spy heading home from your Nation, and it would not say who sent it.");
	}

	@Test
	void anUnmaskedSpyCaughtOnTheSpotThatConfessesLocatesItsSender() {
		spiesArriveAtCarthage(1);
		inciteUnrestUntilItsLastTurn();

		// It fails, is Unmasked, is caught on the spot, and confesses.
		luck.then(0.99, 0, 0.49, 0.9);
		game.endTurn();

		assertThat(spies()).isEmpty();
		assertThat(report("Carthage"))
			.contains("Your people unmasked and caught a Spy in your Nation, and it confessed that Caesar of Rome sent it.",
					"From its confession, you have Located Rome.")
			.anyMatch(line -> line.startsWith("From its confession, Rome has "));
		assertThat(game.viewOf("Carthage").located()).containsExactly("Rome");
	}

	@Test
	void anUnmaskedSpyCaughtOnTheSpotMayDieOrStaySilent() {
		spiesArriveAtCarthage(1);
		inciteUnrestUntilItsLastTurn();

		luck.then(0.99, 0, 0, 0.1);
		game.endTurn();

		assertThat(spies()).isEmpty();
		assertThat(report("Carthage"))
			.contains("Your people unmasked and caught a Spy in your Nation, and it died before saying who sent it.")
			.noneMatch(line -> line.contains("Rome") || line.contains("Caesar"));
		assertThat(game.viewOf("Carthage").located()).isEmpty();
	}

	@Test
	void theSenderOfAnUnmaskedSpyCaughtOnTheSpotLearnsNothing() {
		spiesArriveAtCarthage(1);
		inciteUnrestUntilItsLastTurn();

		luck.then(0, 0.5, 0, 0, 0.5);
		game.endTurn();

		assertThat(spies()).isEmpty();
		assertThat(report("Rome")).noneMatch(line -> line.contains("Spy"));
		assertThat(report("Carthage")).contains("Agitators stirred up unrest: your Nation lost 4 Population.",
				"Your people unmasked and caught a Spy in your Nation, and it would not say who sent it.");
	}

	@Test
	void anotherSpyReportsAnUnmaskedSpyCaughtOnTheSpot() {
		spiesArriveAtCarthage(2);
		inciteUnrestUntilItsLastTurn();

		luck.then(0.99, 0, 0, 0.5);
		game.endTurn();

		assertThat(spies()).extracting(Spy::id).containsExactly(2);
		assertThat(report("Rome"))
			.contains("Your Spy in Carthage reported that Carthage caught your other Spy there, and it stayed silent.")
			.noneMatch(line -> line.contains("Incite Unrest"));
	}

}
