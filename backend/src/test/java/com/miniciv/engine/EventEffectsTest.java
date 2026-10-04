package com.miniciv.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.EventsTest.happensWith;
import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static com.miniciv.engine.GameTest.luck;
import static com.miniciv.engine.SpiesTest.RULESET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * The Event effects that gain or lose Units, stall or speed up work in
 * progress, and catch Spies.
 */
class EventEffectsTest {

	Game game;

	void play(RandomGenerator luck, Event... events) {
		game = new Game(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(), List.of(events)),
				Identities.of("Rome", "Carthage"), luck);
	}

	/**
	 * {@link #play}, once every Nation has Located every other; see
	 * {@link Locating}.
	 */
	void playLocated(RandomGenerator luck, Event... events) {
		game = Locating.everyone(new Ruleset(RULESET.units(), RULESET.buildings(), List.of(), List.of(events)),
				Identities.of("Rome", "Carthage"), luck);
	}

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	void act(String nation, Action action) {
		assertThat(game.act(nation, action)).isEqualTo(ActionResult.ACCEPTED);
	}

	static Event.Effects units(Map<String, Integer> units) {
		return new Event.Effects(0, 0, 0, 0, 0, units, 0, null);
	}

	static Event.Effects workTurns(int turns) {
		return new Event.Effects(0, 0, 0, 0, 0, null, turns, null);
	}

	static Event.Effects spyCaught(Event.SpyCaught whose) {
		return new Event.Effects(0, 0, 0, 0, 0, null, 0, whose);
	}

	static Event nationEvent(String name, Event.Effects effects, String text) {
		return new Event(name, Event.Subject.NATION, 1, null, effects, text);
	}

	static Event warpartyEvent(String name, Event.Effects effects, String text) {
		return new Event(name, Event.Subject.WARPARTY, 1, null, effects, text);
	}

	static final Event VOLUNTEERS = nationEvent("Volunteers", units(Map.of("Warrior", 2)), "Volunteers answered your call");

	static final Event DESERTERS = nationEvent("Deserters", units(Map.of("Warrior", -5)),
			"Deserters slipped away in the night");

	@Test
	void unitsCanJoinANationsArmy() {
		play(EVEN_LUCK, VOLUNTEERS);

		game.endTurn();

		assertThat(report("Rome")).contains("Volunteers answered your call: 2 Warriors joined your Army.");
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Warrior", 2));
		assertThat(game.viewOf("Carthage").army()).containsExactly(entry("Warrior", 2));
	}

	@Test
	void luckVariesHowManyUnitsJoin() {
		play(happensWith(0.99), VOLUNTEERS);

		game.endTurn();

		assertThat(report("Rome")).contains("Volunteers answered your call: 3 Warriors joined your Army.");
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Warrior", 3));
	}

	@Test
	void unitsCanLeaveANationsArmyButNoMoreThanItHas() {
		play(EVEN_LUCK, DESERTERS);
		act("Rome", new Action.TrainUnits("Warrior", 2));

		game.endTurn();

		assertThat(report("Rome")).contains("Deserters slipped away in the night: 2 Warriors left your Army.");
		assertThat(game.viewOf("Rome").army()).isEmpty();
	}

	@Test
	void anEventLosingUnitsANationDoesNotHaveCannotHappenToIt() {
		// Were it rolled for Rome, which has no Warriors, it would take Rome's
		// draw and Carthage's would miss.
		play(luck(0, -1), DESERTERS);
		act("Carthage", new Action.TrainUnits("Warrior", 1));

		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.startsWith("Deserters"));
		assertThat(report("Carthage")).contains("Deserters slipped away in the night: 1 Warrior left your Army.");
	}

	/**
	 * Has Rome send a Warparty of two Warriors to Carthage, as Turn 2 begins.
	 */
	void romeSendsTwoWarriors() {
		act("Rome", new Action.TrainUnits("Warrior", 2));
		game.endTurn();
		act("Rome", new Action.SendWarparty("Carthage", Map.of("Warrior", 2)));
	}

	@Test
	void unitsCanJoinAWarpartyWithoutChangingItsTravelTime() {
		playLocated(EVEN_LUCK, warpartyEvent("Stragglers", units(Map.of("Warrior", 1)), "Stragglers caught up with the march"));
		romeSendsTwoWarriors();

		game.endTurn();

		assertThat(report("Rome")).contains(
				"Stragglers caught up with the march: 1 Warrior joined your Warparty heading to Carthage.");
		assertThat(game.viewOf("Rome").warparties())
			.containsExactly(new Warparty(1, "Carthage", Map.of("Warrior", 3), 9, false, 2, 1, Plunder.NONE, 6));
		assertThat(game.viewOf("Rome").army()).isEmpty();
	}

	@Test
	void unitsCanLeaveAWarpartyAndOneLeftWithNoneDisappears() {
		playLocated(EVEN_LUCK, warpartyEvent("Desertion", units(Map.of("Warrior", -5)), "Your soldiers lost heart"));
		romeSendsTwoWarriors();

		game.endTurn();

		assertThat(report("Rome"))
			.contains("Your soldiers lost heart: 2 Warriors left your Warparty heading to Carthage and your Warparty disbanded.");
		assertThat(game.viewOf("Rome").warparties()).isEmpty();
		assertThat(game.viewOf("Rome").army()).isEmpty();
	}

	@Test
	void anEventLosingUnitsAWarpartyDoesNotHoldCannotHappenToIt() {
		playLocated(EVEN_LUCK, warpartyEvent("Archers flee", units(Map.of("Archer", -1)), "Your archers fled"));
		romeSendsTwoWarriors();

		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.startsWith("Your archers fled"));
		assertThat(game.viewOf("Rome").warparties()).singleElement()
			.extracting(Warparty::units)
			.isEqualTo(Map.of("Warrior", 2));
	}

	@Test
	void workInProgressCanBeStalled() {
		play(EVEN_LUCK, nationEvent("Strike", workTurns(2), "Your workers went on strike"));
		act("Rome", new Action.StartBuilding("Walls"));

		game.endTurn();

		assertThat(report("Rome")).contains("Your workers went on strike: your Walls will now be built in 3 Turns.");
		assertThat(game.viewOf("Rome").construction()).containsExactly(new Construction("Walls", 3));
	}

	@Test
	void anEventStallingWorkCannotHappenToANationWithNothingInProgress() {
		play(EVEN_LUCK, nationEvent("Strike", workTurns(2), "Your workers went on strike"));

		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.startsWith("Your workers"));
	}

	@Test
	void workSpedUpNeverFinishesEarlierThanNextTurn() {
		play(EVEN_LUCK, nationEvent("Skilled hands", workTurns(-5), "Skilled hands joined your workshops"));
		act("Rome", new Action.TrainUnits("Archer", 1));

		game.endTurn();

		assertThat(report("Rome"))
			.contains("Skilled hands joined your workshops: your Archer ×1 will now finish training in 1 Turn.");
		assertThat(game.viewOf("Rome").army()).isEmpty();
		game.endTurn();
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Archer", 1));
	}

	@Test
	void luckPicksWhichWorkInProgressIsStalled() {
		// Rome's Event happens, leaves its stall as authored, and picks the
		// last of its work in progress: the Archer in training.
		play(luck(0, Long.MIN_VALUE, -1), nationEvent("Strike", workTurns(2), "Your workers went on strike"));
		act("Rome", new Action.StartBuilding("Walls"));
		act("Rome", new Action.TrainUnits("Archer", 1));

		game.endTurn();

		assertThat(report("Rome"))
			.contains("Your workers went on strike: your Archer ×1 will now finish training in 3 Turns.");
		assertThat(game.viewOf("Rome").construction()).containsExactly(new Construction("Walls", 1));
		assertThat(game.viewOf("Rome").training()).containsExactly(new Training("Archer", 1, 5));
	}

	/**
	 * Has Rome train Spies and send them to Carthage, ending Turns until they
	 * have arrived.
	 */
	void romeSpiesArriveAtCarthage(int spies) {
		act("Rome", new Action.TrainUnits("Spy", spies));
		game.endTurn();
		for (int spy = 1; spy <= spies; spy++) {
			act("Rome", new Action.SendSpy("Carthage"));
		}
		for (int turn = 1; turn <= Spy.TRAVEL_TURNS; turn++) {
			game.endTurn();
		}
	}

	static final Event INFORMANTS = nationEvent("Informants", spyCaught(Event.SpyCaught.ENEMY),
			"Informants betrayed a foreign agent");

	@Test
	void anEventCanCatchAnEnemySpyInTheNationFollowingTheCaughtSpyRules() {
		playLocated(EVEN_LUCK, INFORMANTS);

		romeSpiesArriveAtCarthage(1);

		assertThat(report("Carthage")).contains("Informants betrayed a foreign agent.",
				"Your people caught a Spy in your Nation, and it would not say who sent it.");
		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(report("Rome")).noneMatch(line -> line.contains("caught") || line.startsWith("Informants"));
	}

	@Test
	void aSenderIsToldOfAnEventCatchingItsSpyOnlyByAnotherOfItsSpiesThere() {
		playLocated(EVEN_LUCK, INFORMANTS);

		romeSpiesArriveAtCarthage(2);

		assertThat(game.viewOf("Rome").spies()).singleElement().extracting(Spy::id).isEqualTo(1);
		assertThat(report("Rome")).contains(
				"Your Spy in Carthage reported that Carthage caught your other Spy there, and it stayed silent.");
	}

	@Test
	void anEventCanCatchOneOfTheNationsOwnSpiesAbroadTellingItOnlyTheEventsText() {
		playLocated(EVEN_LUCK, nationEvent("Intercepted", spyCaught(Event.SpyCaught.OWN), "A courier of yours was intercepted"));
		act("Rome", new Action.TrainUnits("Spy", 1));
		game.endTurn();
		assertThat(report("Rome")).noneMatch(line -> line.startsWith("A courier"));
		act("Rome", new Action.SendSpy("Carthage"));

		game.endTurn();

		assertThat(report("Rome")).contains("A courier of yours was intercepted.")
			.noneMatch(line -> line.contains("caught"));
		assertThat(game.viewOf("Rome").spies()).isEmpty();
		assertThat(report("Carthage"))
			.contains("Your people caught a Spy heading to your Nation, and it would not say who sent it.")
			.noneMatch(line -> line.startsWith("A courier"));
	}

	@Test
	void anEventCatchingSpiesCannotHappenWhenThereIsNoSpyToCatch() {
		play(EVEN_LUCK, INFORMANTS, nationEvent("Intercepted", spyCaught(Event.SpyCaught.OWN), "A courier was intercepted"));

		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.startsWith("Informants") || line.startsWith("A courier"));
	}

	@Test
	void aDilemmaOutcomeCanGainUnitsAndStallWork() {
		Event mercenaries = new Event("Mercenaries", Event.Subject.NATION, 1, null, null, "Mercenaries offer their swords",
				List.of(new Event.Choice("Hire them",
						new Event.Effects(-10, 0, 0, 0, 0, Map.of("Warrior", 2), 1, null), false,
						"You hired the mercenaries, who idled about your building sites"),
						new Event.Choice("Send them away", null, true, "You sent the mercenaries away")));
		play(EVEN_LUCK, mercenaries);
		game.endTurn();
		act("Rome", new Action.AnswerDilemma("Mercenaries", "Hire them"));
		act("Rome", new Action.StartBuilding("Walls"));

		game.endTurn();

		assertThat(report("Rome")).contains("You hired the mercenaries, who idled about your building sites: "
				+ "your Nation lost 10 Gold and 2 Warriors joined your Army and your Walls will now be built in 2 Turns.");
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Warrior", 2));
		assertThat(game.viewOf("Rome").construction()).containsExactly(new Construction("Walls", 2));
	}

	@Test
	void aDilemmaOutcomeCanLoseUnits() {
		Event mutiny = new Event("Mutiny", Event.Subject.NATION, 1, null, null, "Your soldiers grumble",
				List.of(new Event.Choice("Pay them", new Event.Effects(-5, 0, 0, 0, 0), false, "You paid your soldiers"),
						new Event.Choice("Ignore them", units(Map.of("Warrior", -1)), true, "Your soldiers mutinied")));
		play(EVEN_LUCK, mutiny);
		act("Rome", new Action.TrainUnits("Warrior", 2));
		game.endTurn();

		game.endTurn();

		assertThat(report("Rome")).contains("You did not choose in time what to do about Mutiny. "
				+ "Your soldiers mutinied: 1 Warrior left your Army.");
		assertThat(game.viewOf("Rome").army()).containsExactly(entry("Warrior", 1));
	}

	@Test
	void aDilemmaOutcomeCanCatchASpy() {
		Event informants = new Event("Informants", Event.Subject.NATION, 1, null, null, "Informants come to your court",
				List.of(new Event.Choice("Hear them", spyCaught(Event.SpyCaught.ENEMY), false,
						"You heard the informants out"),
						new Event.Choice("Send them away", null, true, "You sent the informants away")));
		playLocated(EVEN_LUCK, informants);
		romeSpiesArriveAtCarthage(1);
		act("Carthage", new Action.AnswerDilemma("Informants", "Hear them"));

		game.endTurn();

		assertThat(report("Carthage")).contains("You heard the informants out.",
				"Your people caught a Spy in your Nation, and it would not say who sent it.");
		assertThat(game.viewOf("Rome").spies()).isEmpty();
	}

	@Test
	void aGameWithTheNewEffectsReplaysIdenticallyFromTheSameSeed() {
		Event[] events = { new Event("Volunteers", Event.Subject.NATION, 0.3, null, units(Map.of("Warrior", 2)), "Volunteers"),
				new Event("Deserters", Event.Subject.NATION, 0.3, null, units(Map.of("Warrior", -2)), "Deserters"),
				new Event("Strike", Event.Subject.NATION, 0.3, null, workTurns(2), "Strike"),
				new Event("Informants", Event.Subject.NATION, 0.3, null, spyCaught(Event.SpyCaught.ENEMY), "Informants"),
				new Event("Stragglers", Event.Subject.WARPARTY, 0.3, null, units(Map.of("Warrior", 1)), "Stragglers") };
		List<List<String>> reports = new ArrayList<>();
		for (int replay = 1; replay <= 2; replay++) {
			playLocated(new Random(7), events);
			act("Rome", new Action.TrainUnits("Spy", 2));
			act("Rome", new Action.StartBuilding("Walls"));
			game.endTurn();
			act("Rome", new Action.SendSpy("Carthage"));
			act("Rome", new Action.SendSpy("Carthage"));
			List<String> lines = new ArrayList<>();
			for (int turn = 1; turn <= 15; turn++) {
				if (game.viewOf("Rome").army().getOrDefault("Warrior", 0) > 0 && game.viewOf("Rome").warparties().isEmpty()) {
					act("Rome", new Action.SendWarparty("Carthage",
							Map.of("Warrior", game.viewOf("Rome").army().get("Warrior"))));
				}
				game.endTurn();
				lines.addAll(report("Rome"));
				lines.addAll(report("Carthage"));
			}
			reports.add(lines);
		}

		assertThat(reports.get(0)).anyMatch(line -> line.startsWith("Volunteers"));
		assertThat(reports.get(1)).isEqualTo(reports.get(0));
	}

}
