package com.miniciv.engine;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.EventsTest.ALWAYS;
import static com.miniciv.engine.GameTest.BASE_INCOME_REPORT;
import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.RULESET;
import static com.miniciv.engine.GameTest.incomeReport;
import static com.miniciv.engine.TurnReport.Kind.COMPLETED;
import static com.miniciv.engine.TurnReport.Kind.DECISIONS_AND_ALERTS;
import static com.miniciv.engine.TurnReport.Kind.EVENTS;
import static com.miniciv.engine.TurnReport.Kind.RESOURCES;
import static com.miniciv.engine.TurnReport.Kind.SPIES_AND_SCOUTS;
import static com.miniciv.engine.TurnReport.Kind.WARFARE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class TurnReportSectionsTest {

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage"), EVEN_LUCK);

	TurnReport report(String nation) {
		return game.viewOf(nation).turnReport();
	}

	List<String> section(String nation, TurnReport.Kind kind) {
		return sections(nation).getOrDefault(kind, List.of());
	}

	/**
	 * Each section of a Nation's Turn Report, in order, with its lines as plain
	 * text.
	 */
	Map<TurnReport.Kind, List<String>> sections(String nation) {
		Map<TurnReport.Kind, List<String>> sections = new LinkedHashMap<>();
		report(nation).sections()
			.forEach(section -> sections.put(section.kind(),
					section.lines().stream().map(TurnReport.Line::text).toList()));
		return sections;
	}

	void armyOf(String nation, String unit, int count) {
		assertThat(game.act(nation, new Action.TrainUnits(unit, count))).isEqualTo(ActionResult.ACCEPTED);
		while (game.viewOf(nation).army().getOrDefault(unit, 0) < count) {
			game.endTurn();
		}
	}

	@Test
	void incomeLandsInResourcesAndATurnWithNothingElseHasNoOtherSection() {
		game.endTurn();

		assertThat(sections("Rome")).containsExactly(entry(RESOURCES, List.of(BASE_INCOME_REPORT)));
	}

	@Test
	void aCompletedBuildingLandsInCompletedAheadOfResources() {
		game.act("Rome", new Action.StartBuilding("Moat"));

		game.endTurn();

		assertThat(sections("Rome")).containsExactly(entry(COMPLETED, List.of("Your Moat has been built.")),
				entry(RESOURCES, List.of(incomeReport(10))));
	}

	@Test
	void aBattleResultLandsInWarfareAndAnArrivingEnemyInDecisionsAndAlerts() {
		game = Locating.everyone(RULESET, Identities.of("Rome", "Carthage"), EVEN_LUCK);
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		while (game.viewOf("Rome").warparties().getFirst().turnsLeft() > 0) {
			game.endTurn();
		}

		assertThat(section("Carthage", DECISIONS_AND_ALERTS)).containsExactly(
				"Caesar of Rome's Warparty arrived at your Nation; the Battle will be fought as this Turn ends.");
		game.endTurn();

		assertThat(section("Rome", WARFARE)).contains("Your Warparty of Warrior ×2 met no Army at Carthage.");
	}

	@Test
	void anEventLandsInEventsAheadOfResourcesThoughItIsRolledAfterIncome() {
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(),
				List.of(EventsTest.starter("Good harvest"))), Identities.of("Rome", "Carthage"), ALWAYS);
		game.endTurn();

		game.endTurn();

		assertThat(sections("Rome")).containsExactly(
				entry(EVENTS, List.of("A good harvest filled your granaries: your Nation gained 3 Population.")),
				entry(RESOURCES, List.of(incomeReport(12))));
		assertThat(report("Rome").lines()).containsExactly(
				"A good harvest filled your granaries: your Nation gained 3 Population.", incomeReport(12));
	}

	@Test
	void aSpyReportLandsInSpiesAndScoutsAndItsIdleAlertInDecisionsAndAlerts() {
		game = Locating.by("Rome", SpyMissionsTest.RULESET, Identities.of("Rome", "Carthage"), GameTest.EVEN_LUCK);
		armyOf("Rome", "Spy", 1);
		game.act("Rome", new Action.SendSpy("Carthage"));
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}
		game.act("Rome", new Action.SetMission(1, "Report Gold"));
		game.endTurn();

		game.endTurn();

		assertThat(section("Rome", SPIES_AND_SCOUTS))
			.contains("Your Spy in Carthage reported that Carthage has " + game.viewOf("Carthage").gold() + " Gold.");
		assertThat(section("Rome", DECISIONS_AND_ALERTS))
			.contains("Your Spy in Carthage finished Report Gold and is Idle; set it a new Mission.");
	}

	@Test
	void aWaitingDilemmaLandsInDecisionsAndAlertsAndItsOutcomeInEvents() {
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(), List.of(DilemmasTest.OUTBREAK)),
				Identities.of("Rome", "Carthage"), ALWAYS);

		game.endTurn();

		assertThat(section("Rome", DECISIONS_AND_ALERTS))
			.containsExactly("Sickness broke out in your Nation; choose what to do before this Turn ends.");
		game.act("Rome", new Action.AnswerDilemma("Outbreak", "Quarantine"));
		game.endTurn();
		assertThat(section("Rome", EVENTS)).first().asString().startsWith("You quarantined the sick");
	}

	@Test
	void sectionsAlwaysComeInTheFixedOrder() {
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(),
				List.of(DilemmasTest.OUTBREAK, EventsTest.starter("Good harvest"))), Identities.of("Rome", "Carthage"),
				ALWAYS);
		game.act("Rome", new Action.TrainUnits("Archer", 1));
		game.act("Rome", new Action.StartBuilding("Moat"));

		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
			assertThat(report("Rome").sections()).extracting(TurnReport.Section::kind)
				.isSortedAccordingTo(Comparator.naturalOrder())
				.doesNotHaveDuplicates();
			assertThat(report("Rome").sections()).allSatisfy(section -> assertThat(section.lines()).isNotEmpty());
		}
	}

}
