package com.miniciv.engine;

import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.EventsTest.happensWith;
import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.SpiesTest.RULESET;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Events that happen to a Nation's Scouts in the wilds.
 */
class ScoutEventsTest {

	Game game;

	void play(RandomGenerator luck, Event... events) {
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(), List.of(events)),
				Identities.of("Rome", "Carthage"), luck);
	}

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	void act(String nation, Action action) {
		assertThat(game.act(nation, action)).isEqualTo(ActionResult.ACCEPTED);
	}

	static Event.Effects scoutsLost(int scouts) {
		return new Event.Effects(0, 0, 0, 0, 0, null, 0, null, scouts);
	}

	static Event scoutEvent(String name, Event.Effects effects, String text) {
		return new Event(name, Event.Subject.SCOUTS, 1, null, effects, text);
	}

	static final Event BLIZZARD = scoutEvent("Blizzard", scoutsLost(1), "A blizzard swept the mountain passes");

	@Test
	void aNationWithNoScoutsNeverGetsAScoutEvent() {
		play(EVEN_LUCK, scoutEvent("Hidden hoard", new Event.Effects(10, 0, 0, 0, 0),
				"Your Scouts stumbled on a hidden hoard"));
		act("Rome", new Action.TrainUnits("Scout", 1));

		game.endTurn();

		assertThat(report("Rome")).contains("Your Scouts stumbled on a hidden hoard: your Nation gained 10 Gold.");
		assertThat(report("Carthage")).noneMatch(line -> line.startsWith("Your Scouts stumbled"));
	}

	@Test
	void aScoutLossEventLosesTheAuthoredNumberOfScoutsWhateverTheLuck() {
		play(happensWith(0.99), BLIZZARD);
		act("Rome", new Action.TrainUnits("Scout", 3));

		game.endTurn();

		assertThat(report("Rome")).contains("A blizzard swept the mountain passes: 1 Scout was lost in the wilds.");
		assertThat(game.viewOf("Rome").scouts()).isEqualTo(2);
	}

	@Test
	void aScoutLossEventNeverLosesMoreScoutsThanTheNationHas() {
		play(EVEN_LUCK, scoutEvent("Avalanche", scoutsLost(5), "An avalanche buried the high trail"));
		act("Rome", new Action.TrainUnits("Scout", 2));

		game.endTurn();

		assertThat(report("Rome")).contains("An avalanche buried the high trail: 2 Scouts were lost in the wilds.");
		assertThat(game.viewOf("Rome").scouts()).isZero();

		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.startsWith("An avalanche"));
		assertThat(game.viewOf("Rome").scouts()).isZero();
	}

	@Test
	void aScoutEventIsToldInTheEventsSection() {
		play(EVEN_LUCK, BLIZZARD);
		act("Rome", new Action.TrainUnits("Scout", 1));

		game.endTurn();

		assertThat(game.viewOf("Rome").turnReport().sections())
			.filteredOn(section -> section.kind() == TurnReport.Kind.EVENTS)
			.flatExtracting(section -> section.lines().stream().map(TurnReport.Line::text).toList())
			.contains("A blizzard swept the mountain passes: 1 Scout was lost in the wilds.");
	}

	@Test
	void aDilemmaCanHappenToScoutsAndItsChoiceLoseThem() {
		Event lostTrail = new Event("Lost trail", Event.Subject.SCOUTS, 1, null, null, "Your Scouts lost the trail",
				List.of(new Event.Choice("Wait for spring", new Event.Effects(-5, 0, 0, 0, 0), false,
						"Your Scouts waited out the winter"),
						new Event.Choice("Press on", scoutsLost(1), true, "Your Scouts pressed on into the snow")));
		play(EVEN_LUCK, lostTrail);
		game.endTurn();
		assertThat(game.viewOf("Rome").dilemmas()).isEmpty();
		act("Rome", new Action.TrainUnits("Scout", 2));

		game.endTurn();
		game.endTurn();

		assertThat(report("Rome")).contains("You did not choose in time what to do about Lost trail. "
				+ "Your Scouts pressed on into the snow: 1 Scout was lost in the wilds.");
		assertThat(game.viewOf("Rome").scouts()).isEqualTo(1);
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Lost trail"));
	}

}
