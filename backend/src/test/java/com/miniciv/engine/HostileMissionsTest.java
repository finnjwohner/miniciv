package com.miniciv.engine;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.FARM;
import static com.miniciv.engine.GameTest.Rigged;
import static com.miniciv.engine.GameTest.WALLS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class HostileMissionsTest {

	static final List<Mission> MISSIONS = hostileAndIntel();

	static List<Mission> hostileAndIntel() {
		List<Mission> missions = new ArrayList<>(SpyMissionsTest.MISSIONS);
		missions.add(new Mission("Sabotage Defenses", 4, 0.6, new Mission.Harm(8, 0, 0, 0),
				"Saboteurs undermined your fortifications"));
		missions.add(new Mission("Sabotage Buildings", 4, 0.6, new Mission.Harm(0, 1, 0, 0), "Saboteurs struck in the night"));
		missions.add(new Mission("Poison Army", 4, 0.5, new Mission.Harm(0, 0, 0.2, 0), "Poison was found in your soldiers' wells"));
		missions.add(new Mission("Incite Unrest", 3, 0.6, new Mission.Harm(0, 0, 0, 4), "Agitators stirred up unrest"));
		return List.copyOf(missions);
	}

	static final Ruleset RULESET = new Ruleset(SpiesTest.RULESET.units(), List.of(FARM, WALLS), List.of(), List.of(),
			MISSIONS);

	/**
	 * Luck that draws nothing lucky, 0.99, unless told otherwise: a hostile
	 * Mission fails and no Spy is caught.
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

	void build(String nation, String building) {
		assertThat(game.act(nation, new Action.StartBuilding(building))).isEqualTo(ActionResult.ACCEPTED);
		while (!game.viewOf(nation).construction().isEmpty()) {
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

	Spy spy() {
		return game.viewOf("Rome").spies().getFirst();
	}

	/**
	 * Sets Rome's Spy in Carthage to a Mission and ends Turns until the last
	 * one before it ends.
	 */
	void startOnLastTurn(String mission) {
		assertThat(game.act("Rome", new Action.SetMission(1, mission))).isEqualTo(ActionResult.ACCEPTED);
		while (spy().missionTurnsLeft() > 1) {
			game.endTurn();
			assertThat(report("Rome")).noneMatch(line -> line.contains(mission));
			assertThat(report("Carthage")).noneMatch(
					line -> MISSIONS.stream().anyMatch(hostile -> hostile.text() != null && line.startsWith(hostile.text())));
		}
	}

	@Test
	void aHostileMissionResolvesOnceWhenItsTurnsRunOut() {
		spyArrivesAtCarthage();
		assertThat(game.act("Rome", new Action.SetMission(1, "Incite Unrest"))).isEqualTo(ActionResult.ACCEPTED);
		assertThat(spy().missionTurnsLeft()).isEqualTo(3);

		for (int turn = 1; turn <= 2; turn++) {
			game.endTurn();
			assertThat(report("Rome")).noneMatch(line -> line.contains("Incite Unrest"));
		}
		game.endTurn();

		assertThat(report("Rome")).contains("Your Spy in Carthage failed at Incite Unrest.",
				"Your Spy in Carthage finished Incite Unrest and is Idle; set it a new Mission.");
		assertThat(spy().mission()).isNull();
		game.endTurn();
		assertThat(report("Rome")).noneMatch(line -> line.contains("Incite Unrest"));
	}

	@Test
	void sabotageDefensesThatSucceedsWearsDownDefenseAndTellsTheVictimWithoutNamingTheSender() {
		build("Carthage", "Walls");
		spyArrivesAtCarthage();
		startOnLastTurn("Sabotage Defenses");

		luck.then(0, 0.5);
		game.endTurn();

		assertThat(game.viewOf("Carthage").defense()).isEqualTo(2);
		assertThat(report("Carthage"))
			.contains("Saboteurs undermined your fortifications: your Defense was worn down from 10 to 2, damaging Walls.")
			.noneMatch(line -> line.contains("Rome") || line.contains("Caesar"));
		assertThat(report("Rome")).contains("Your Spy in Carthage succeeded at Sabotage Defenses.",
				"Your Spy in Carthage finished Sabotage Defenses and is Idle; set it a new Mission.");
		assertThat(spy().mission()).isNull();
	}

	@Test
	void aHostileMissionThatFailsHarmsNothingAndTheVictimIsNotTold() {
		build("Carthage", "Walls");
		spyArrivesAtCarthage();
		startOnLastTurn("Sabotage Defenses");

		game.endTurn();

		assertThat(game.viewOf("Carthage").defense()).isEqualTo(10);
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Saboteurs"));
		assertThat(report("Rome")).contains("Your Spy in Carthage failed at Sabotage Defenses.",
				"Your Spy in Carthage finished Sabotage Defenses and is Idle; set it a new Mission.");
		assertThat(spy().mission()).isNull();
	}

	@Test
	void sabotageBuildingsThatSucceedsDamagesOneNonDefensiveBuilding() {
		build("Carthage", "Walls");
		build("Carthage", "Farm");
		spyArrivesAtCarthage();
		startOnLastTurn("Sabotage Buildings");

		luck.then(0, 0);
		game.endTurn();

		assertThat(game.viewOf("Carthage").damage())
			.anySatisfy(damage -> {
				assertThat(damage.building()).isEqualTo("Farm");
				assertThat(damage.damaged()).isEqualTo(1);
			});
		assertThat(game.viewOf("Carthage").defense()).isEqualTo(10);
		assertThat(report("Carthage")).contains("Saboteurs struck in the night: your Farm ×1 was damaged.");
		assertThat(report("Rome")).contains("Your Spy in Carthage succeeded at Sabotage Buildings.");
	}

	@Test
	void poisonArmyThatSucceedsKillsAboutAFifthOfTheArmy() {
		armyOf("Carthage", "Warrior", 4);
		spyArrivesAtCarthage();
		startOnLastTurn("Poison Army");

		luck.then(0, 0.5, 0, 0);
		game.endTurn();

		assertThat(game.viewOf("Carthage").army()).containsExactly(entry("Warrior", 3));
		assertThat(report("Carthage")).contains("Poison was found in your soldiers' wells: 1 Warrior died.");
		assertThat(report("Rome")).contains("Your Spy in Carthage succeeded at Poison Army.");
	}

	@Test
	void poisonArmyIsVariedByLuck() {
		armyOf("Carthage", "Warrior", 4);
		spyArrivesAtCarthage();
		startOnLastTurn("Poison Army");

		luck.then(0, 0.99, 0, 0);
		game.endTurn();

		assertThat(game.viewOf("Carthage").army()).containsExactly(entry("Warrior", 2));
		assertThat(report("Carthage")).contains("Poison was found in your soldiers' wells: 2 Warriors died.");
	}

	@Test
	void incitingUnrestThatSucceedsCostsPopulation() {
		spyArrivesAtCarthage();
		startOnLastTurn("Incite Unrest");

		luck.then(0, 0.5);
		game.endTurn();

		assertThat(game.viewOf("Carthage").population()).isEqualTo(game.viewOf("Egypt").population() - 4);
		assertThat(report("Carthage")).contains("Agitators stirred up unrest: your Nation lost 4 Population.");
		assertThat(report("Rome")).contains("Your Spy in Carthage succeeded at Incite Unrest.");
	}

	@Test
	void aSucceedingMissionWithNothingToHarmTellsTheVictimNothing() {
		spyArrivesAtCarthage();
		startOnLastTurn("Poison Army");

		luck.then(0);
		game.endTurn();

		assertThat(report("Rome")).contains("Your Spy in Carthage succeeded at Poison Army.");
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Poison"));
	}

	/**
	 * Has Carthage keep a Spy at home and Rome's Spy arrive there, with luck
	 * that draws 0.3 from then on: above the chance one Spy at home gives of
	 * catching it, but below double that chance.
	 */
	void spyArrivesWhereOneSpyIsAtHome() {
		armyOf("Carthage", "Spy", 1);
		luck.always(0.3);
		spyArrivesAtCarthage();
		assertThat(game.viewOf("Rome").spies()).hasSize(1);
	}

	@Test
	void counterIntelligenceIsTwiceAsLikelyToCatchASpyOnAHostileMission() {
		spyArrivesWhereOneSpyIsAtHome();

		game.act("Rome", new Action.SetMission(1, "Sabotage Defenses"));
		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(report("Carthage")).anyMatch(line -> line.startsWith("Your Spies caught a Spy in your Nation"));
	}

	@Test
	void counterIntelligenceHasTheUsualChanceAgainstASpyOnAnIntelMission() {
		spyArrivesWhereOneSpyIsAtHome();

		game.act("Rome", new Action.SetMission(1, "Report Gold"));
		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).hasSize(1);
	}

	@Test
	void counterIntelligenceHasTheUsualChanceAgainstAnIdleSpy() {
		spyArrivesWhereOneSpyIsAtHome();

		game.endTurn();

		assertThat(game.viewOf("Rome").spies()).hasSize(1);
	}

}
