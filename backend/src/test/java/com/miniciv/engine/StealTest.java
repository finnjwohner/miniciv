package com.miniciv.engine;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.FARM;
import static com.miniciv.engine.GameTest.Rigged;
import static com.miniciv.engine.GameTest.WALLS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class StealTest {

	/**
	 * Mercenaries, free to train, whose upkeep is more than a Nation's
	 * Population earns early in the Game.
	 */
	static final UnitType MERCENARY = new UnitType("Mercenary", 0, 0, 1, 60, 1, List.of());

	static final Ruleset RULESET = ruleset();

	static Ruleset ruleset() {
		List<Mission> missions = new ArrayList<>(HostileMissionsTest.MISSIONS);
		missions.add(new Mission("Steal Gold", 3, 0.6, Resource.GOLD, "Thieves robbed your treasury"));
		missions.add(new Mission("Steal Research", 3, 0.6, Resource.RESEARCH, "Thieves stole your scholars' work"));
		List<UnitType> units = new ArrayList<>(SpiesTest.RULESET.units());
		units.add(MERCENARY);
		return new Ruleset(units, List.of(FARM, WALLS), List.of(), List.of(), missions);
	}

	/**
	 * Luck that draws nothing lucky, 0.99, unless told otherwise: a hostile
	 * Mission fails, no Spy is Unmasked and no Spy is caught.
	 */
	Rigged luck = new Rigged();

	Game game = Locating.by("Rome", RULESET, Identities.of("Rome", "Carthage", "Egypt"), luck);

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
	 * Trains a Spy in Rome, sends it to Carthage and ends Turns until it has
	 * arrived.
	 */
	void spyArrivesAtCarthage() {
		armyOf("Rome", "Spy", 1);
		assertThat(game.act("Rome", new Action.SendSpy("Carthage"))).isEqualTo(ActionResult.ACCEPTED);
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}
	}

	List<Spy> spies() {
		return game.viewOf("Rome").spies();
	}

	/**
	 * Sets Rome's Spy in Carthage to a Steal Mission and ends Turns until it
	 * succeeds, after any draws given.
	 */
	void succeedAt(String mission, double... before) {
		assertThat(game.act("Rome", new Action.SetMission(1, mission))).isEqualTo(ActionResult.ACCEPTED);
		while (spies().getFirst().missionTurnsLeft() > 1) {
			game.endTurn();
		}
		luck.then(before);
		luck.then(0);
		game.endTurn();
	}

	/**
	 * How much Gold Egypt, which started out as Carthage did and is left
	 * alone, gained as the last Turn resolved.
	 */
	int egyptGained;

	/**
	 * Ends the Turn, returning how much less Gold Carthage has than Egypt.
	 */
	int goldBehindEgyptAfterTurn() {
		int before = game.viewOf("Egypt").gold();
		game.endTurn();
		egyptGained = game.viewOf("Egypt").gold() - before;
		return game.viewOf("Egypt").gold() - game.viewOf("Carthage").gold();
	}

	@Test
	void aSuccessfulStealMakesItsSpyEscaping() {
		spyArrivesAtCarthage();

		succeedAt("Steal Gold");

		assertThat(spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 0, 3, "Steal Gold", 0, true, null));
		assertThat(report("Rome"))
			.contains("Your Spy in Carthage succeeded at Steal Gold and is Escaping; it will take its Haul from what Carthage gains next Turn.")
			.noneMatch(line -> line.contains("Idle"));
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Thieves"));
	}

	@Test
	void anEscapingSpyCannotBeSetAMission() {
		spyArrivesAtCarthage();
		succeedAt("Steal Gold");

		assertThat(game.act("Rome", new Action.SetMission(1, "Report Gold")))
			.isEqualTo(ActionResult.rejected("Your Spy is Escaping from Carthage."));
	}

	@Test
	void theNextTurnTheThiefTakesAShareOfWhatItsTargetGainsAndHeadsHomeWithIt() {
		spyArrivesAtCarthage();
		succeedAt("Steal Gold");

		// The least share, 20%, and the Spy is not Unmasked.
		luck.then(0, 0.99);
		int stolen = goldBehindEgyptAfterTurn();

		assertThat(stolen).isEqualTo(Math.round(egyptGained * 0.2f)).isPositive();
		assertThat(spies()).containsExactly(new Spy(1, "Spy", "Carthage", true, 3, 3, null, 0, false, new Haul(Resource.GOLD, stolen)));
		assertThat(report("Carthage"))
			.contains("Thieves robbed your treasury: " + stolen + " Gold was taken from what your Nation gained.");
		assertThat(report("Rome")).contains("Your Spy in Carthage stole " + stolen + " Gold and is heading home with it.");
	}

	@Test
	void theShareCanBeAllOfWhatTheTargetGains() {
		spyArrivesAtCarthage();
		succeedAt("Steal Gold");

		luck.then(0.9999999, 0.99);

		assertThat(goldBehindEgyptAfterTurn()).isEqualTo(egyptGained);
	}

	@Test
	void stealResearchTakesFromTheResearchItsTargetGains() {
		spyArrivesAtCarthage();
		succeedAt("Steal Research");

		// 60% of the 3 Research Carthage gains.
		luck.then(0.5, 0.99);
		game.endTurn();

		assertThat(game.viewOf("Egypt").research() - game.viewOf("Carthage").research()).isEqualTo(2);
		assertThat(spies()).singleElement().satisfies(spy -> assertThat(spy.haul()).isEqualTo(new Haul(Resource.RESEARCH, 2)));
		assertThat(report("Carthage"))
			.contains("Thieves stole your scholars' work: 2 Research was taken from what your Nation gained.");
	}

	@Test
	void anEscapingSpyCannotBeCaughtByCounterIntelligence() {
		armyOf("Carthage", "Spy", 3);
		spyArrivesAtCarthage();
		// Carthage's Spies do not catch it first.
		succeedAt("Steal Gold", 0.99);

		// The share; then, were the Spy tried, it would not be caught, and it
		// would be Unmasked and caught on the spot.
		luck.then(0.5, 0.99);
		luck.always(0);
		game.endTurn();

		assertThat(spies()).singleElement().satisfies(spy -> assertThat(spy.homeward()).isTrue());
	}

	@Test
	void anEscapingSpyCannotBeCaughtByScouts() {
		armyOf("Carthage", "Scout", 3);
		assertThat(game.act("Carthage", new Action.SetTask("HUNT", null, 3))).isEqualTo(ActionResult.ACCEPTED);
		spyArrivesAtCarthage();
		succeedAt("Steal Gold");

		luck.then(0.5, 0.99);
		luck.always(0);
		game.endTurn();

		assertThat(spies()).singleElement().satisfies(spy -> assertThat(spy.homeward()).isTrue());
		assertThat(report("Carthage")).noneMatch(line -> line.contains("caught"));
	}

	@Test
	void aSecondThiefFindsNothingLeftOnceTheFirstHasTakenAll() {
		armyOf("Rome", "Spy", 2);
		assertThat(game.act("Rome", new Action.SendSpy("Carthage"))).isEqualTo(ActionResult.ACCEPTED);
		assertThat(game.act("Rome", new Action.SendSpy("Carthage"))).isEqualTo(ActionResult.ACCEPTED);
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}
		assertThat(game.act("Rome", new Action.SetMission(2, "Steal Gold"))).isEqualTo(ActionResult.ACCEPTED);
		succeedAt("Steal Gold", 0);

		// The first takes all of it and is not Unmasked; the second draws
		// nothing.
		luck.then(0.9999999, 0.99);
		luck.always(0);
		int stolen = goldBehindEgyptAfterTurn();

		assertThat(stolen).isEqualTo(egyptGained);
		assertThat(spies()).containsExactly(
				new Spy(1, "Spy", "Carthage", true, 3, 3, null, 0, false, new Haul(Resource.GOLD, stolen)),
				new Spy(2, "Spy", "Carthage", false, 0, 3, null, 0, false, null));
		assertThat(report("Rome")).contains("Your Spy in Carthage found nothing to steal there.");
	}

	@Test
	void aThiefWhoseTargetGainedNothingStaysInPlaceIdle() {
		spyArrivesAtCarthage();
		succeedAt("Steal Gold");
		// They join as the Turn resolves, before their upkeep is paid.
		assertThat(game.act("Carthage", new Action.TrainUnits("Mercenary", 1))).isEqualTo(ActionResult.ACCEPTED);
		int held = game.viewOf("Carthage").gold();

		game.endTurn();

		assertThat(game.viewOf("Carthage").gold()).isLessThan(held);
		assertThat(spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 0, 3, null, 0, false, null));
		assertThat(report("Rome")).contains("Your Spy in Carthage found nothing to steal there.",
				"Your Spy in Carthage is Idle; set it a new Mission.");
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Thieves"));
	}

	@Test
	void aThiefArrivingHomeHandsOverItsHaulAndJoinsTheSpiesAtHome() {
		spyArrivesAtCarthage();
		succeedAt("Steal Gold");
		luck.then(0.5, 0.99);
		int stolen = goldBehindEgyptAfterTurn();
		game.endTurn();
		game.endTurn();
		NationView before = game.viewOf("Rome");
		int earned = before.gold() + before.goldForecast().income() - before.goldForecast().upkeep();

		game.endTurn();

		assertThat(spies()).isEmpty();
		assertThat(game.viewOf("Rome").army()).contains(entry("Spy", 1));
		assertThat(report("Rome")).contains("Your Spy came home from Carthage with " + stolen + " Gold and joined your Spies at home.");
		assertThat(game.viewOf("Rome").gold()).isEqualTo(earned + stolen);
		assertThat(game.act("Rome", new Action.SendSpy("Egypt"))).isEqualTo(ActionResult.ACCEPTED);
	}

	@Test
	void aThiefCaughtOnTheWayHomeLosesItsHaul() {
		armyOf("Carthage", "Scout", 1);
		assertThat(game.act("Carthage", new Action.SetTask("HUNT", null, 1))).isEqualTo(ActionResult.ACCEPTED);
		spyArrivesAtCarthage();
		succeedAt("Steal Gold");
		luck.then(0.5, 0.99);
		game.endTurn();

		// Carthage's Scout catches it, and it dies.
		luck.then(0, 0);
		game.endTurn();

		assertThat(spies()).isEmpty();
		assertThat(report("Carthage")).anyMatch(line -> line.startsWith("Your Scouts caught a Spy heading home from your Nation"));
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
			assertThat(report("Rome")).noneMatch(line -> line.contains("came home"));
		}
		assertThat(game.viewOf("Rome").army()).doesNotContainKey("Spy");
	}

	@Test
	void anUnmaskedThiefFleesWithItsHaul() {
		spyArrivesAtCarthage();
		succeedAt("Steal Gold");

		// The share, Unmasked, not caught on the spot.
		luck.then(0.5, 0.24, 0.5);
		int stolen = goldBehindEgyptAfterTurn();

		assertThat(stolen).isPositive();
		assertThat(spies()).containsExactly(new Spy(1, "Spy", "Carthage", true, 3, 3, null, 0, false, new Haul(Resource.GOLD, stolen)));
		assertThat(report("Rome")).contains("Your Spy in Carthage stole " + stolen + " Gold, was Unmasked and is fleeing home with it.");
		assertThat(report("Carthage")).contains("A Spy was Unmasked in your Nation and fled home; Caesar of Rome sent it.");
	}

	@Test
	void aHaulSeizedFromAThiefCaughtOnTheSpotGoesStraightBackToItsVictim() {
		spyArrivesAtCarthage();
		succeedAt("Steal Gold");

		// The share, Unmasked, caught on the spot, dies.
		luck.then(0.5, 0, 0, 0);
		int behind = goldBehindEgyptAfterTurn();

		assertThat(behind).isZero();
		assertThat(spies()).isEmpty();
		assertThat(report("Carthage")).anyMatch(line -> line.startsWith("Your people unmasked and caught a Spy in your Nation"))
			.anyMatch(line -> line.matches("You took back the \\d+ Gold it stole\\."));
	}

	@Test
	void aFailedStealLeavesItsSpyIdleInPlace() {
		spyArrivesAtCarthage();
		assertThat(game.act("Rome", new Action.SetMission(1, "Steal Gold"))).isEqualTo(ActionResult.ACCEPTED);
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}

		assertThat(spies()).containsExactly(new Spy(1, "Spy", "Carthage", false, 0, 3, null, 0, false, null));
		assertThat(report("Rome")).contains("Your Spy in Carthage failed at Steal Gold.");
	}

}
