package com.miniciv.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.BASE_INCOME_REPORT;
import static com.miniciv.engine.GameTest.incomeReport;
import static com.miniciv.engine.GameTest.RULESET;
import static com.miniciv.engine.GameTest.luck;
import static org.assertj.core.api.Assertions.assertThat;

class EventsTest {

	/**
	 * The starter Events, as the game ships them.
	 */
	static final List<Event> STARTER = RulesetFiles.load("ruleset").events();

	/**
	 * Luck that makes every Event happen whenever its conditions hold.
	 */
	static final RandomGenerator ALWAYS = luck(0);

	/**
	 * Luck that makes no Event happen.
	 */
	static final RandomGenerator NEVER = luck(-1);

	/**
	 * A cheap Unit, to send Warparties large enough for Rumours of war.
	 */
	static final UnitType RECRUIT = new UnitType("Recruit", 5, 1, 1, 1, 0.1, List.of());

	Game game;

	static Event starter(String name) {
		return STARTER.stream().filter(event -> event.name().equals(name)).findFirst().orElseThrow();
	}

	void play(RandomGenerator luck, Event... events) {
		List<UnitType> units = Stream.concat(RULESET.units().stream(), Stream.of(RECRUIT)).toList();
		game = new Game(new Ruleset(units, RULESET.buildings(), List.of(), List.of(events)),
				List.of("Rome", "Carthage"), luck);
	}

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	void armyOf(String nation, String unit, int count) {
		assertThat(game.act(nation, new Action.TrainUnits(unit, count))).isEqualTo(ActionResult.ACCEPTED);
		while (game.viewOf(nation).army().getOrDefault(unit, 0) < count) {
			game.endTurn();
		}
	}

	@Test
	void aGoodHarvestAddsPopulationAndIsAnnouncedWithItsEffect() {
		play(ALWAYS, starter("Good harvest"));

		game.endTurn();
		assertThat(report("Rome")).containsExactly(BASE_INCOME_REPORT);
		game.endTurn();

		assertThat(report("Rome")).containsExactly(incomeReport(12),
				"A good harvest filled your granaries: your Nation gained 4 Population.");
		assertThat(game.viewOf("Rome").population()).isEqualTo(18);
		assertThat(game.viewOf("Carthage").population()).isEqualTo(18);
	}

	@Test
	void anEventDoesNotHappenWhenLuckIsAgainstIt() {
		play(NEVER, STARTER.toArray(Event[]::new));

		for (int turn = 1; turn <= 6; turn++) {
			game.endTurn();
		}

		assertThat(report("Rome")).containsExactly(incomeReport(20));
		assertThat(game.viewOf("Rome").population()).isEqualTo(22);
	}

	@Test
	void aPlagueStrikesOnlyALargeEnoughPopulationOnceTheGameIsUnderwayAndTakesSomeOfIt() {
		play(ALWAYS, starter("Plague"));

		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
			assertThat(report("Rome")).containsExactly(incomeReport(10 + 2 * (turn - 1)));
		}
		game.endTurn();

		assertThat(report("Rome")).containsExactly(incomeReport(16),
				"Plague swept through your Nation: your Nation lost 6 Population.");
		assertThat(game.viewOf("Rome").population()).isEqualTo(12);
	}

	@Test
	void anEventTakesNoMoreGoldPopulationOrResearchThanTheNationHas() {
		play(ALWAYS, new Event("Famine", Event.Subject.NATION, 1, null, new Event.Effects(5, -100, -100, 0, 0),
				"Famine struck"));

		game.endTurn();

		assertThat(report("Rome")).containsExactly(BASE_INCOME_REPORT,
				"Famine struck: your Nation gained 5 Gold and lost 12 Population and 3 Research.");
		NationView rome = game.viewOf("Rome");
		assertThat(rome.gold()).isEqualTo(65);
		assertThat(rome.population()).isZero();
		assertThat(rome.research()).isZero();
	}

	@Test
	void anEventThatDamagesDefenseWearsItDownLikeABattleWould() {
		play(ALWAYS, new Event("Earthquake", Event.Subject.NATION, 1, null, new Event.Effects(0, 0, 0, 4, 0),
				"An earthquake shook your Nation"));
		game.act("Rome", new Action.StartBuilding("Walls"));
		game.endTurn();
		assertThat(report("Rome")).containsExactly(BASE_INCOME_REPORT, "An earthquake shook your Nation.");

		game.endTurn();

		assertThat(report("Rome")).containsExactly("Your Walls has been built.", incomeReport(12),
				"An earthquake shook your Nation: your Defense was worn down from 10 to 6, damaging Walls.");
		assertThat(game.viewOf("Rome").defense()).isEqualTo(6);
	}

	@Test
	void anEventCanNeedTheNationToHaveEnoughGoldOrResearch() {
		play(ALWAYS,
				new Event("Tax windfall", Event.Subject.NATION, 1, new Event.Conditions(null, null, null, null, 70, null, null),
						new Event.Effects(5, 0, 0, 0, 0), "Your tax collectors outdid themselves"),
				new Event("Debate", Event.Subject.NATION, 1, new Event.Conditions(null, null, null, null, null, 6, null),
						new Event.Effects(0, 0, 1, 0, 0), "Your scholars debated"));

		game.endTurn();
		assertThat(report("Rome")).containsExactly(BASE_INCOME_REPORT);

		game.endTurn();

		assertThat(report("Rome")).containsExactly(incomeReport(12),
				"Your tax collectors outdid themselves: your Nation gained 5 Gold.",
				"Your scholars debated: your Nation gained 1 Research.");
	}

	@Test
	void anEventCanNeedTheNationToHaveEnoughDefense() {
		play(ALWAYS, new Event("Earthquake", Event.Subject.NATION, 1,
				new Event.Conditions(null, null, null, null, null, null, 10), new Event.Effects(0, 0, 0, 4, 0),
				"An earthquake shook your Nation"));
		game.act("Rome", new Action.StartBuilding("Walls"));
		game.endTurn();
		assertThat(report("Rome")).containsExactly(BASE_INCOME_REPORT);

		game.endTurn();

		assertThat(report("Rome")).containsExactly("Your Walls has been built.", incomeReport(12),
				"An earthquake shook your Nation: your Defense was worn down from 10 to 6, damaging Walls.");
		assertThat(report("Carthage")).containsExactly(incomeReport(12));
	}

	@Test
	void aDustStormDelaysATravellingWarpartyAndItsPlayerSeesTheTurnsLeft() {
		play(ALWAYS, starter("Dust storm"));
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));

		game.endTurn();

		assertThat(report("Rome")).contains(
				"A dust storm swept the plains: your Warparty of Warrior ×2 heading to Carthage will now arrive in 3 Turns.");
		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 2), false, 3, 1));
		assertThat(report("Carthage")).noneMatch(line -> line.contains("dust storm"));
	}

	@Test
	void aDustStormAlsoDelaysAWarpartyHeadingHomeButNotOneWaitingForItsBattle() {
		// Two misses while it travels out, two draws for the Battle, then a hit.
		play(luck(-1, -1, -1, -1, 0), starter("Dust storm"));
		armyOf("Rome", "Warrior", 2);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
		game.endTurn();
		game.endTurn();
		game.endTurn();
		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 2), false, 0, 3));
		assertThat(report("Rome")).noneMatch(line -> line.contains("dust storm"));

		game.endTurn();

		assertThat(report("Rome")).contains(
				"A dust storm swept the plains: your Warparty of Warrior ×2 heading home from Carthage will now get home in 4 Turns.");
		assertThat(game.viewOf("Rome").warparties()).singleElement().extracting(Warparty::turnsLeft).isEqualTo(4);
	}

	@Test
	void anEventCannotLeaveAWarpartyLessThanOneTurnFromWhereItIsHeading() {
		play(ALWAYS, new Event("Tailwind", Event.Subject.WARPARTY, 1, null, new Event.Effects(0, 0, 0, 0, -5),
				"A tailwind sped your march"));
		armyOf("Rome", "Warrior", 1);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 1)));

		game.endTurn();

		assertThat(report("Rome")).contains(
				"A tailwind sped your march: your Warparty of Warrior ×1 heading to Carthage will now arrive in 1 Turn.");
		assertThat(game.viewOf("Rome").warparties()).singleElement().extracting(Warparty::turnsLeft).isEqualTo(1);
	}

	@Test
	void rumoursWarnANationOfALargeWarpartyApproachingButNotOfASmallOne() {
		play(ALWAYS, starter("Rumours of war"));
		// A Turn's income first, so the Recruits' upkeep stays affordable.
		game.endTurn();
		armyOf("Rome", "Recruit", 8);
		armyOf("Carthage", "Recruit", 7);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Recruit", 8)));
		game.act("Carthage", new Action.SendWarparty("Rome", Map.of("Recruit", 7)));

		game.endTurn();

		assertThat(report("Carthage")).contains("Rumours spread of a large army marching on your Nation.");
		assertThat(report("Rome")).noneMatch(line -> line.startsWith("Rumours"));
	}

	@Test
	void eventsAreRolledInTheOrderListedForEachNationAndWarpartyInTurn() {
		play(luck(-1, 0, 0, -1), starter("Good harvest"), starter("Rumours of war"));
		armyOf("Rome", "Recruit", 8);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Recruit", 8)));

		game.endTurn();

		// Good harvest misses Rome, then strikes Carthage; Rumours of war is
		// rolled only for Carthage, which a large Warparty is approaching.
		assertThat(report("Rome")).noneMatch(line -> line.startsWith("A good harvest"));
		assertThat(report("Carthage")).contains("A good harvest filled your granaries: your Nation gained 4 Population.",
				"Rumours spread of a large army marching on your Nation.");
	}

	@Test
	void aGameWithEventsReplaysIdenticallyFromTheSameSeed() {
		List<List<String>> reports = new ArrayList<>();
		for (int replay = 1; replay <= 2; replay++) {
			play(new Random(21), STARTER.toArray(Event[]::new));
			armyOf("Rome", "Recruit", 8);
			game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Recruit", 8)));
			List<String> lines = new ArrayList<>();
			for (int turn = 1; turn <= 20; turn++) {
				game.endTurn();
				lines.addAll(report("Rome"));
				lines.addAll(report("Carthage"));
			}
			reports.add(lines);
		}

		assertThat(reports.get(0)).anyMatch(line -> line.startsWith("A good harvest"));
		assertThat(reports.get(1)).isEqualTo(reports.get(0));
	}

}
