package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.miniciv.engine.EventsTest.ALWAYS;
import static com.miniciv.engine.EventsTest.starter;
import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.RULESET;
import static com.miniciv.engine.SpiesTest.SPY;
import static org.assertj.core.api.Assertions.assertThat;

class TravelTest {

	/**
	 * A Building whose Defense soaks up any Warparty's Strength, so that its
	 * Nation keeps its Population and is never conquered.
	 */
	static final BuildingType FORTRESS = new BuildingType("Fortress", 0, 1, 0, 0, 0, 0, 1000);

	Game game;

	/**
	 * A free Unit type with the given Strength.
	 */
	static UnitType soldier(double strength) {
		return new UnitType("Soldier", 0, 0, 1, 0, strength, List.of());
	}

	void play(Ruleset ruleset) {
		game = new Game(ruleset, List.of("Rome", "Carthage", "Egypt"), EVEN_LUCK);
	}

	void armyOf(String nation, String unit, int count) {
		assertThat(game.act(nation, new Action.TrainUnits(unit, count))).isEqualTo(ActionResult.ACCEPTED);
		while (game.viewOf(nation).army().getOrDefault(unit, 0) < count) {
			game.endTurn();
		}
	}

	Warparty romesWarparty() {
		assertThat(game.viewOf("Rome").warparties()).hasSize(1);
		return game.viewOf("Rome").warparties().get(0);
	}

	/**
	 * Sends Rome's Warparty to Carthage and ends Turns until it has fought
	 * there and is heading home.
	 */
	Warparty foughtAtCarthage(Map<String, Integer> units) {
		assertThat(game.act("Rome", new Action.SendWarparty("Carthage", units))).isEqualTo(ActionResult.ACCEPTED);
		do {
			game.endTurn();
		}
		while (!romesWarparty().homeward());
		return romesWarparty();
	}

	@ParameterizedTest
	@CsvSource({ "0, 3", "9, 3", "9.9, 3", "10, 4", "24, 4", "25, 5", "49, 5", "50, 6", "99, 6", "100, 7", "250, 7" })
	void aWarpartysTripOutTakesTheTurnsOfTheBracketItsStrengthFallsIn(double strength, int turns) {
		play(new Ruleset(List.of(soldier(strength)), List.of(), List.of()));
		armyOf("Rome", "Soldier", 1);

		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Soldier", 1)));

		assertThat(romesWarparty().strength()).isEqualTo(strength);
		assertThat(romesWarparty().turnsLeft()).isEqualTo(turns);
	}

	@Test
	void aWarpartysStrengthIsItsUnitsBaseStrengthWithoutCounters() {
		play(RULESET);
		armyOf("Rome", "Warrior", 2);
		armyOf("Rome", "Horseman", 1);
		armyOf("Carthage", "Archer", 2);

		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2, "Horseman", 1)));

		assertThat(romesWarparty().strength()).isEqualTo(10);
		assertThat(romesWarparty().turnsLeft()).isEqualTo(4);
	}

	@ParameterizedTest
	@CsvSource({ "7.6, 29, 3", "7.7, 30, 4", "19.9, 78, 5", "20, 80, 6", "60, 240, 7", "100, 400, 7" })
	void aWarpartysTripHomeTakesTheBracketForItsStrengthPlusMoreForItsHaulUpToSevenTurns(double strength, int haul,
			int turns) {
		play(new Ruleset(List.of(soldier(strength)), List.of(FORTRESS), List.of()));
		game.act("Carthage", new Action.StartBuilding("Fortress"));
		for (int turn = 1; turn <= 30; turn++) {
			game.endTurn();
		}
		armyOf("Rome", "Soldier", 1);

		Warparty home = foughtAtCarthage(Map.of("Soldier", 1));

		assertThat(home.plunder().gold() + home.plunder().research()).isEqualTo(haul);
		assertThat(home.turnsLeft()).isEqualTo(turns);
		assertThat(home.strength()).isEqualTo(strength);
	}

	@Test
	void aWarpartysTripHomeTakesTheBracketForTheStrengthThatSurvivedTheBattle() {
		play(RULESET);
		game.endTurn();
		armyOf("Rome", "Warrior", 4);
		armyOf("Carthage", "Warrior", 4);

		Warparty home = foughtAtCarthage(Map.of("Warrior", 4));

		assertThat(home.units()).containsExactly(Map.entry("Warrior", 2));
		assertThat(home.strength()).isEqualTo(6);
		assertThat(home.plunder()).isEqualTo(new Plunder(18, 6));
		assertThat(home.turnsLeft()).isEqualTo(3);
	}

	@Test
	void whatAWarpartySeizesInAConquestCountsAsHaulForItsTripHome() {
		UnitType recruit = new UnitType("Recruit", 0, 1, 20, 0, 1, List.of());
		play(new Ruleset(List.of(soldier(3), recruit), List.of(), List.of()));
		armyOf("Rome", "Soldier", 1);
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Soldier", 1)));
		while (romesWarparty().turnsLeft() > 0) {
			game.endTurn();
		}
		NationView carthage = game.viewOf("Carthage");
		game.act("Carthage", new Action.TrainUnits("Recruit", carthage.population()));
		assertThat(carthage.gold() * 3 / 4 + carthage.research() * 3 / 4).isGreaterThanOrEqualTo(80);

		game.endTurn();

		assertThat(game.viewOf("Carthage").conqueredBy()).isEqualTo("Rome");
		assertThat(romesWarparty().homeward()).isTrue();
		assertThat(romesWarparty().turnsLeft()).isEqualTo(3 + 2);
	}

	@Test
	void travelTurnEventsChangeATripBeyondTheBrackets() {
		game = new Game(new Ruleset(List.of(soldier(100)), List.of(), List.of(), List.of(starter("Swollen river"))),
				List.of("Rome", "Carthage"), ALWAYS);
		armyOf("Rome", "Soldier", 1);
		game.endTurn();
		game.act("Rome", new Action.SendWarparty("Carthage", Map.of("Soldier", 1)));

		game.endTurn();

		assertThat(romesWarparty().turnsLeft()).isEqualTo(7 - 1 + 2);
	}

	@Test
	void spiesTravelAFlatThreeTurnsAndTheViewShowsTheBrackets() {
		play(new Ruleset(List.of(soldier(100), SPY), List.of(), List.of()));
		armyOf("Rome", "Spy", 1);

		game.act("Rome", new Action.SendSpy("Carthage"));

		NationView rome = game.viewOf("Rome");
		assertThat(rome.spies()).singleElement().extracting(Spy::turnsLeft).isEqualTo(3);
		assertThat(rome.spyTravelTurns()).isEqualTo(3);
		assertThat(rome.travelBrackets()).containsExactly(new TravelBracket(0, 3), new TravelBracket(10, 4),
				new TravelBracket(25, 5), new TravelBracket(50, 6), new TravelBracket(100, 7));
	}

}
