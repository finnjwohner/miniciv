package com.miniciv.engine;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.FARM;
import static com.miniciv.engine.GameTest.WALLS;
import static org.assertj.core.api.Assertions.assertThat;

class SpyMissionsTest {

	static final List<Mission> MISSIONS = List.of(new Mission("Report Gold", 2, Mission.FactKind.GOLD),
			new Mission("Report Population", 2, Mission.FactKind.POPULATION),
			new Mission("Report Research", 2, Mission.FactKind.RESEARCH), new Mission("Report Army", 2, Mission.FactKind.ARMY),
			new Mission("Report Buildings", 3, Mission.FactKind.BUILDINGS));

	static final Ruleset RULESET = new Ruleset(SpiesTest.RULESET.units(), List.of(FARM, WALLS), List.of(), List.of(),
			MISSIONS);

	Game game = Locating.by("Rome", RULESET, Identities.of("Rome", "Carthage", "Egypt"), EVEN_LUCK);

	void play(RandomGenerator luck) {
		game = Locating.by("Rome", RULESET, Identities.of("Rome", "Carthage", "Egypt"), luck);
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

	ActionResult setMission(int spy, String mission) {
		return game.act("Rome", new Action.SetMission(spy, mission));
	}

	Spy spy() {
		return game.viewOf("Rome").spies().getFirst();
	}

	Facts factsOfCarthage() {
		return game.viewOf("Rome")
			.map()
			.stream()
			.filter(entry -> entry.name().equals("Carthage"))
			.findFirst()
			.orElseThrow()
			.facts();
	}

	@Test
	void theViewOffersTheRulesetsMissions() {
		assertThat(game.viewOf("Rome").missions()).isEqualTo(MISSIONS);
	}

	@Test
	void aSpyArrivesIdleAndReportsNothingWhileIdle() {
		spyArrivesAtCarthage();

		assertThat(spy().mission()).isNull();
		assertThat(spy().missionTurnsLeft()).isZero();
		for (int turn = 1; turn <= 10; turn++) {
			game.endTurn();
			assertThat(report("Rome")).noneMatch(line -> line.contains("reported"));
		}
		assertThat(factsOfCarthage()).isEqualTo(Facts.NONE);
	}

	@Test
	void aMissionIsSetForASpyInPlaceShowingItsTurnsLeft() {
		spyArrivesAtCarthage();

		assertThat(setMission(1, "Report Gold")).isEqualTo(ActionResult.ACCEPTED);

		assertThat(spy().mission()).isEqualTo("Report Gold");
		assertThat(spy().missionTurnsLeft()).isEqualTo(2);
		game.endTurn();
		assertThat(spy().mission()).isEqualTo("Report Gold");
		assertThat(spy().missionTurnsLeft()).isEqualTo(1);
		assertThat(report("Rome")).noneMatch(line -> line.contains("reported"));
	}

	@Test
	void aMissionIsRejectedForASpyNotInPlace() {
		armyOf("Rome", "Spy", 1);
		game.act("Rome", new Action.SendSpy("Carthage"));

		assertThat(setMission(1, "Report Gold"))
			.isEqualTo(ActionResult.rejected("Your Spy has not yet arrived at Carthage."));
		assertThat(setMission(2, "Report Gold")).isEqualTo(ActionResult.rejected("You have no such Spy."));
		assertThat(spy().mission()).isNull();
	}

	@Test
	void aMissionIsRejectedForASpyHeadingHome() {
		spyArrivesAtCarthage();
		game.remove("Carthage");

		assertThat(setMission(1, "Report Gold")).isEqualTo(ActionResult.rejected("Your Spy is heading home."));
	}

	@Test
	void anUnknownMissionIsRejected() {
		spyArrivesAtCarthage();

		assertThat(setMission(1, "Dance")).isEqualTo(ActionResult.rejected("There is no Mission called Dance."));
		assertThat(spy().mission()).isNull();
	}

	@Test
	void anIntelMissionReportsItsFactWhenItEndsThenTheSpyGoesIdleAndThePlayerIsAlerted() {
		spyArrivesAtCarthage();
		setMission(1, "Report Gold");
		game.endTurn();

		game.endTurn();

		int gold = game.viewOf("Carthage").gold();
		int learnedOn = game.turn() - 1;
		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage has " + gold + " Gold.",
				"Your Spy in Carthage finished Report Gold and is Idle; set it a new Mission.");
		assertThat(game.viewOf("Rome").turnReport().sections().getFirst().kind())
			.isEqualTo(TurnReport.Kind.DECISIONS_AND_ALERTS);
		assertThat(factsOfCarthage()).isEqualTo(new Facts(new Fact<>(gold, learnedOn), null, null, null, null, null, null));
		assertThat(spy().mission()).isNull();
		assertThat(spy().missionTurnsLeft()).isZero();
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Spy"));

		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.contains("Spy"));
	}

	@Test
	void reportPopulationReportsThePopulation() {
		spyArrivesAtCarthage();
		setMission(1, "Report Population");
		game.endTurn();
		game.endTurn();

		int population = game.viewOf("Carthage").population();
		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage has " + population + " Population.");
		assertThat(factsOfCarthage().population()).isEqualTo(new Fact<>(population, game.turn() - 1));
	}

	@Test
	void reportResearchReportsTheResearch() {
		spyArrivesAtCarthage();
		setMission(1, "Report Research");
		game.endTurn();
		game.endTurn();

		int research = game.viewOf("Carthage").research();
		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage has " + research + " Research.");
		assertThat(factsOfCarthage().research()).isEqualTo(new Fact<>(research, game.turn() - 1));
	}

	@Test
	void reportArmyReportsTheArmy() {
		armyOf("Carthage", "Warrior", 2);
		spyArrivesAtCarthage();
		setMission(1, "Report Army");
		game.endTurn();
		game.endTurn();

		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage's Army is Warrior ×2.");
		assertThat(factsOfCarthage().army()).isEqualTo(new Fact<>(Map.of("Warrior", 2), game.turn() - 1));
	}

	@Test
	void reportBuildingsReportsTheBuildingsAndDefenseAfterItsTurns() {
		game.act("Carthage", new Action.StartBuilding("Walls"));
		spyArrivesAtCarthage();
		setMission(1, "Report Buildings");
		game.endTurn();
		game.endTurn();
		assertThat(report("Rome")).noneMatch(line -> line.contains("reported"));

		game.endTurn();

		assertThat(report("Rome"))
			.contains("Your Spy in Carthage reported that Carthage has built Walls ×1, and its Defense is 10.");
		Facts facts = factsOfCarthage();
		assertThat(facts.buildings()).isEqualTo(new Fact<>(Map.of("Walls", 1), game.turn() - 1));
		assertThat(facts.defense()).isEqualTo(new Fact<>(10, game.turn() - 1));
	}

	/**
	 * Sets Rome's Spy in Carthage to a Mission and ends Turns until it ends.
	 */
	void carryOut(String mission) {
		assertThat(setMission(1, mission)).isEqualTo(ActionResult.ACCEPTED);
		while (spy().mission() != null) {
			game.endTurn();
		}
	}

	@Test
	void reportArmyMayReportThatTheTargetHasNoArmy() {
		spyArrivesAtCarthage();

		carryOut("Report Army");

		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage has no Army.");
		assertThat(factsOfCarthage().army()).isEqualTo(new Fact<>(Map.of(), game.turn() - 1));
	}

	@Test
	void reportArmyNeverIncludesTheTargetsScouts() {
		armyOf("Carthage", "Warrior", 1);
		armyOf("Carthage", "Scout", 1);
		spyArrivesAtCarthage();

		carryOut("Report Army");

		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage's Army is Warrior ×1.");
	}

	@Test
	void reportBuildingsMayReportThatTheTargetHasBuiltNothing() {
		spyArrivesAtCarthage();

		carryOut("Report Buildings");

		assertThat(report("Rome"))
			.contains("Your Spy in Carthage reported that Carthage has built nothing, and its Defense is 0.");
	}

	@Test
	void onlyTheNationThatSentTheSpyLearnsWhatItReports() {
		spyArrivesAtCarthage();

		carryOut("Report Gold");

		for (String nation : List.of("Carthage", "Egypt")) {
			assertThat(game.viewOf(nation).map()).extracting(MapNation::facts).containsOnly(Facts.NONE);
		}
		assertThat(game.viewOf("Rome").map())
			.filteredOn(entry -> !entry.name().equals("Carthage"))
			.extracting(MapNation::facts)
			.containsOnly(Facts.NONE);
	}

	@Test
	void aLaterReportReplacesTheSameFactAndKeepsTheOthers() {
		spyArrivesAtCarthage();
		carryOut("Report Population");
		Fact<Integer> population = factsOfCarthage().population();
		carryOut("Report Gold");
		game.act("Carthage", new Action.StartBuilding("Farm"));

		carryOut("Report Gold");

		int gold = game.viewOf("Carthage").gold();
		assertThat(factsOfCarthage().gold()).isEqualTo(new Fact<>(gold, game.turn() - 1));
		assertThat(factsOfCarthage().population()).isEqualTo(population);
	}

	@Test
	void switchingASpysMissionRestartsItFromScratch() {
		spyArrivesAtCarthage();
		setMission(1, "Report Gold");
		game.endTurn();

		assertThat(setMission(1, "Report Research")).isEqualTo(ActionResult.ACCEPTED);
		assertThat(spy().missionTurnsLeft()).isEqualTo(2);
		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.contains("reported"));
		assertThat(factsOfCarthage()).isEqualTo(Facts.NONE);
		game.endTurn();
		assertThat(report("Rome")).anyMatch(line -> line.startsWith("Your Spy in Carthage reported that Carthage has")
				&& line.endsWith("Research."));
	}

	@Test
	void settingTheSameMissionAgainRestartsIt() {
		spyArrivesAtCarthage();
		setMission(1, "Report Gold");
		game.endTurn();

		setMission(1, "Report Gold");

		assertThat(spy().missionTurnsLeft()).isEqualTo(2);
	}

	@Test
	void eachSpyHasItsOwnMission() {
		armyOf("Rome", "Spy", 2);
		game.act("Rome", new Action.SendSpy("Carthage"));
		game.act("Rome", new Action.SendSpy("Carthage"));
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}

		setMission(1, "Report Gold");
		setMission(2, "Report Buildings");
		game.endTurn();
		game.endTurn();

		assertThat(report("Rome")).anyMatch(line -> line.endsWith("Gold."))
			.noneMatch(line -> line.contains("Defense"));
		assertThat(game.viewOf("Rome").spies()).extracting(Spy::mission).containsExactly(null, "Report Buildings");
	}

	@Test
	void aSpyTurningBackHomeGivesUpItsMission() {
		spyArrivesAtCarthage();
		setMission(1, "Report Gold");

		game.remove("Carthage");

		assertThat(spy().homeward()).isTrue();
		assertThat(spy().mission()).isNull();
	}

}
