package com.miniciv.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import static com.miniciv.engine.Resource.GOLD;
import static com.miniciv.engine.Resource.POPULATION;
import static com.miniciv.engine.Resource.RESEARCH;
import static com.miniciv.engine.TurnReport.Amount.gain;
import static com.miniciv.engine.TurnReport.Amount.loss;
import static com.miniciv.engine.TurnReport.Amount.neutral;
import static com.miniciv.engine.TurnReport.Kind.COMPLETED;
import static com.miniciv.engine.TurnReport.Kind.DECISIONS_AND_ALERTS;
import static com.miniciv.engine.TurnReport.Kind.EVENTS;
import static com.miniciv.engine.TurnReport.Kind.RESOURCES;
import static com.miniciv.engine.TurnReport.Kind.SPIES_AND_SCOUTS;
import static com.miniciv.engine.TurnReport.Kind.WARFARE;

import com.miniciv.engine.TurnReport.Amount.Effect;
import com.miniciv.engine.TurnReport.Line;

/**
 * One match of miniciv. The engine knows only Nations and Turns: no time,
 * sockets or Players. Everything random draws from the seeded random source,
 * so a Game replays identically from the same seed.
 */
public class Game {

	private static final int BASE_GOLD = 50;

	private static final int BASE_POPULATION = 10;

	private static final int BASE_RESEARCH = 0;

	private static final int POPULATION_PER_TURN = 2;

	private static final int RESEARCH_PER_TURN = 3;

	/**
	 * How much Gold a Warparty's surviving Strength lets it plunder.
	 */
	private static final int PLUNDER_GOLD_PER_STRENGTH = 3;

	/**
	 * How much Research a Warparty's surviving Strength lets it plunder.
	 */
	private static final int PLUNDER_RESEARCH_PER_STRENGTH = 1;

	/**
	 * How much Population a Warparty's Strength left once Defense is down
	 * reduces its target's by.
	 */
	private static final int POPULATION_PER_STRENGTH = 1;

	/**
	 * How much more Population a Warparty reduces its target's by when the
	 * target's Army was wiped out, or it had neither Army nor Defense.
	 */
	private static final int EXPOSED_POPULATION_MULTIPLIER = 2;

	/**
	 * How much of the Strength a Warparty has left once Defense is down it
	 * takes to Damage one of a Breached target's non-defensive Buildings.
	 */
	private static final int STRENGTH_PER_BUILDING_DAMAGED = 5;

	/**
	 * The share of a conquered Nation's Gold and Research its conqueror seizes.
	 */
	private static final double CONQUEST_SHARE = 0.75;

	/**
	 * The chance each Watch Task gives its Nation, each Turn, of
	 * discovering each other Nation's Warparty abroad.
	 */
	private static final double DISCOVERY_CHANCE_PER_WATCH = 0.2;

	/**
	 * Luck for forecasting a Nation's Gold, where nothing drawn changes it.
	 */
	private static final RandomGenerator FORECAST_LUCK = () -> 0;

	/**
	 * The chance each Scout on a Locate Task gives it, each Turn, of Locating
	 * its Nation.
	 */
	private static final double LOCATE_CHANCE_PER_SCOUT = 0.15;

	/**
	 * The chance each Spy at home gives its Nation, each Turn, of catching each
	 * other Nation's Spy in place there.
	 */
	private static final double CATCH_CHANCE_PER_SPY_AT_HOME = 0.2;

	/**
	 * How many times the usual chance each Spy at home gives of catching a Spy
	 * on a hostile Mission.
	 */
	private static final int HOSTILE_CATCH_MULTIPLIER = 2;

	/**
	 * The chance a hostile Mission that succeeds Unmasks its Spy as it ends.
	 */
	private static final double UNMASK_CHANCE_ON_SUCCESS = 0.25;

	/**
	 * The chance a hostile Mission that fails Unmasks its Spy as it ends.
	 */
	private static final double UNMASK_CHANCE_ON_FAILURE = 0.5;

	/**
	 * The chance an Unmasked Spy is caught on the spot rather than fleeing
	 * home.
	 */
	private static final double CAUGHT_WHEN_UNMASKED_CHANCE = 0.5;

	/**
	 * The least share of what its target gains that an Escaping Spy takes as
	 * its Haul; it may take all of it.
	 */
	private static final double MIN_HAUL_SHARE = 0.2;

	/**
	 * The chance each Scout Hunting Travelling Spies gives its Nation, each
	 * Turn, of catching each other Nation's Spy still travelling.
	 */
	private static final double CATCH_CHANCE_PER_SCOUT = 0.1;

	/**
	 * How many Turns a Haul seized from a caught Spy takes to reach the Nation
	 * keeping it or getting it back.
	 */
	static final int HAUL_DELIVERY_TURNS = 3;

	/**
	 * The chance each travelling Warparty has, each Turn, of catching each
	 * other Nation's Spy still travelling.
	 */
	private static final double CATCH_CHANCE_PER_WARPARTY = 0.1;

	/**
	 * The chance, each Turn, of each pair of Warparties from different Nations
	 * abroad meeting.
	 */
	private static final double ENCOUNTER_CHANCE = 0.15;

	/**
	 * The first Turn as which a Barbarian Camp may appear as it resolves.
	 */
	static final int FIRST_CAMP_TURN = 8;

	/**
	 * The chance, as each Turn resolves on which one may, of a Barbarian Camp
	 * appearing.
	 */
	static final double CAMP_CHANCE = 0.15;

	/**
	 * How many Turns must resolve after a Barbarian Camp appears before
	 * another may.
	 */
	static final int TURNS_BETWEEN_CAMPS = 4;

	/**
	 * How much Population a Barbarian Camp's Warparty's surviving Strength
	 * lets it carry off as Captives.
	 */
	private static final double CAPTIVES_PER_STRENGTH = 0.5;

	/**
	 * The least Gold destroying a Barbarian Camp pays as its Bounty.
	 */
	static final int LEAST_BOUNTY = 32;

	/**
	 * The most Gold destroying a Barbarian Camp pays as its Bounty.
	 */
	static final int MOST_BOUNTY = 48;

	/**
	 * What becomes of a caught Spy, each as likely as the others.
	 */
	private enum Captured {
		DIES(", and it died before saying who sent it", ", and it died"),
		SILENT(", and it would not say who sent it", ", and it stayed silent"),
		CONFESSES(null, ", and it confessed");

		/**
		 * How the Nation that caught it is told, or null if it is told the
		 * sender.
		 */
		private final String toCatcher;

		/**
		 * How the sender is told, when another of its Spies reports it.
		 */
		private final String toSender;

		Captured(String toCatcher, String toSender) {
			this.toCatcher = toCatcher;
			this.toSender = toSender;
		}

		/**
		 * The outcome a draw between 0 and 1 picks.
		 */
		static Captured picked(double draw) {
			return values()[Math.min(values().length - 1, (int) (draw * values().length))];
		}
	}

	private final Ruleset ruleset;

	private final Map<String, Nation> nations = new LinkedHashMap<>();

	private final RandomGenerator random;

	private int turn = 1;

	/**
	 * The Nations conquered so far, in the order they were conquered.
	 */
	private final List<String> conquered = new ArrayList<>();

	/**
	 * The Nations removed so far, in the order they were removed.
	 */
	private final List<String> removed = new ArrayList<>();

	/**
	 * What each Nation still in the Game is to be told in its next Turn Report
	 * of what happened during the Turn: removals, Recalls and Tribute answered.
	 */
	private final Map<String, TurnReport.Draft> pendingReports = new LinkedHashMap<>();

	/**
	 * The Tribute offered this Turn, in the order offered.
	 */
	private final List<Tribute> tributes = new ArrayList<>();

	/**
	 * The Hauls on their way to a Nation, kept, sent back or taken back from a
	 * caught Spy, in the order sent.
	 */
	private final List<Delivery> deliveries = new ArrayList<>();

	/**
	 * The Encounters Warparties had as the last Turn resolved, in the order
	 * they met, to be fought or not as this Turn ends.
	 */
	private final List<Encounter> encounters = new ArrayList<>();

	/**
	 * The Barbarian Camps that have appeared, destroyed or not, in the order
	 * they appeared.
	 */
	private final List<Camp> camps = new ArrayList<>();

	/**
	 * The Turn the last Barbarian Camp appeared as, or 0 if none has.
	 */
	private int lastCampOn;

	/**
	 * The last Nation standing, or null while the Game goes on.
	 */
	private String winner;

	/**
	 * @param nations who each Nation taking part is, in the order they joined
	 */
	public Game(Ruleset ruleset, List<NationIdentity> nations, RandomGenerator random) {
		this.ruleset = ruleset;
		for (NationIdentity identity : nations) {
			this.nations.put(identity.name(), new Nation(identity, BASE_GOLD, BASE_POPULATION, BASE_RESEARCH));
		}
		this.random = random;
	}

	public int turn() {
		return turn;
	}

	/**
	 * Every Nation that took part in the Game, conquered or not.
	 */
	public List<String> nations() {
		return List.copyOf(nations.keySet());
	}

	/**
	 * The Nations conquered so far, in the order they were conquered.
	 */
	public List<String> conquered() {
		return List.copyOf(conquered);
	}

	/**
	 * The Nations removed so far, in the order they were removed.
	 */
	public List<String> removed() {
		return List.copyOf(removed);
	}

	/**
	 * The Barbarian Camps that have appeared so far, destroyed or not, in the
	 * order they appeared; no Nation knows of one until it hears of it.
	 */
	public List<String> camps() {
		return camps.stream().map(Camp::name).toList();
	}

	/**
	 * Whether a Nation is still in the Game, neither conquered nor removed.
	 */
	public boolean inGame(String nation) {
		return nation(nation).inGame();
	}

	/**
	 * Takes a Nation out of the Game at once, its Player having been
	 * disconnected for too long. Unlike Conquest, nobody seizes anything: its
	 * Warparties and Spies abroad are gone and every Warparty heading to it,
	 * and Spy at it or heading to it, turns back home, and every Task about it
	 * ends. The other Nations are told in their next Turn Report, and if only
	 * one is left it wins the Game. Has no effect on a Nation no longer in the
	 * Game, or once the Game is won.
	 */
	public void remove(String name) {
		takeOut(name, "was removed from the Game, as its Player was disconnected for too long.");
	}

	/**
	 * Takes a Nation out of the Game at once, its Player having left it, as
	 * {@link #remove} does.
	 */
	public void leave(String name) {
		takeOut(name, "left the Game, so it was removed.");
	}

	private void takeOut(String name, String told) {
		Nation nation = nation(name);
		if (winner != null || !nation.inGame()) {
			return;
		}
		nation.remove();
		removed.add(name);
		camps.forEach(camp -> camp.forget(name));
		tributes.removeIf(tribute -> tribute.involves(name));
		encounters.removeIf(encounter -> encounter.involves(name));
		List<Nation> left = remaining();
		for (Nation other : left) {
			TurnReport.Draft report = pendingReport(other);
			report.add(DECISIONS_AND_ALERTS, name + " " + told);
			for (Warparty back : other.turnBackFrom(name)) {
				report.add(WARFARE, "Your Warparty of " + listed(back.units()) + " heading to " + name
						+ " turned back home, as " + name + " was removed from the Game.");
			}
			for (Spy back : other.turnSpiesBackFrom(name)) {
				report.add(SPIES_AND_SCOUTS, spyTurnedBack(back, name + " was removed from the Game"));
			}
			for (Task ended : other.endTasksFor(name)) {
				report.add(SPIES_AND_SCOUTS, taskEnded(ended, name + " was removed from the Game"));
			}
			other.changed();
		}
		if (left.size() == 1) {
			winner = left.getFirst().name();
			pendingReports.clear();
		}
	}

	/**
	 * What a Nation is to be told in its next Turn Report of what happened
	 * during the Turn.
	 */
	private TurnReport.Draft pendingReport(Nation nation) {
		return pendingReports.computeIfAbsent(nation.name(), n -> new TurnReport.Draft());
	}

	/**
	 * The Nation that won the Game, once it is the only one left.
	 */
	public Optional<String> winner() {
		return Optional.ofNullable(winner);
	}

	/**
	 * The Nations still in the Game.
	 */
	private List<Nation> remaining() {
		return nations.values().stream().filter(Nation::inGame).toList();
	}

	/**
	 * The Nations still in the Game, then every Barbarian Camp, each in order:
	 * every one whose Warparties may still be abroad.
	 */
	private List<Sender> senders() {
		List<Sender> senders = new ArrayList<>(remaining());
		senders.addAll(camps);
		return senders;
	}

	/**
	 * The Barbarian Camp of the given name, if one has appeared.
	 */
	private Optional<Camp> camp(String name) {
		return camps.stream().filter(camp -> camp.name().equals(name)).findFirst();
	}

	/**
	 * The Nation or Barbarian Camp of the given name.
	 */
	private Sender sender(String name) {
		return camp(name).<Sender>map(camp -> camp).orElseGet(() -> nation(name));
	}

	/**
	 * Resolves the Turn for every Nation still in the Game at once, then starts
	 * the next Turn. Once the Game is won, nothing happens any more.
	 */
	public void endTurn() {
		if (winner != null) {
			return;
		}
		List<Nation> inPlay = remaining();
		Map<String, TurnReport.Draft> reports = new LinkedHashMap<>();
		inPlay.forEach(nation -> reports.put(nation.name(),
				pendingReports.getOrDefault(nation.name(), new TurnReport.Draft())));
		pendingReports.clear();
		inPlay.forEach(nation -> reports.get(nation.name()).startResolving(nation));
		for (Tribute lapsed : tributes) {
			if (lapsed.answer() == Tribute.Answer.NONE && nation(lapsed.to()).warparty(lapsed.warparty())
				.filter(Warparty::awaitingBattle)
				.isPresent()) {
				reports.get(lapsed.to())
					.add(WARFARE, Line.of("You did not answer " + ruled(lapsed.from()) + "'s Tribute of ",
							lapsed.described(Effect.NEUTRAL), " in time."));
				reports.get(lapsed.from())
					.add(WARFARE, Line.of(ruled(lapsed.to()) + " did not answer your Tribute of ",
							lapsed.described(Effect.NEUTRAL), " in time, so the Battle went ahead."));
			}
		}
		tributes.clear();
		searchForNations(reports);
		List<Runnable> plunderTaken = settleEncounters(reports);
		List<Witnessed> witnessed = new ArrayList<>();
		for (Nation nation : inPlay) {
			TurnReport.Draft report = reports.get(nation.name());
			Nation.Progress progress = nation.progressWork(turn + 1, ruleset);
			tallied(progress.built())
				.forEach((building, count) -> report.add(COMPLETED, yourBuildings(building, count, "built")));
			List<Repair> repairs = progress.repaired();
			tallied(repairs.stream().filter(repair -> !repair.rebuild()).map(Repair::building).toList())
				.forEach((building, count) -> report.add(COMPLETED, yourBuildings(building, count, "repaired")));
			tallied(repairs.stream().filter(Repair::rebuild).map(Repair::building).toList())
				.forEach((building, count) -> report.add(COMPLETED, yourBuildings(building, count, "rebuilt")));
			Nation.Trained trained = progress.trained();
			trained.joined()
				.forEach((unit, count) -> report.add(COMPLETED,
						counted(unit, count) + " finished training and joined your Army."));
			trained.setOut()
				.forEach((unit, count) -> report.add(COMPLETED, counted(unit, count) + " set out into the wilds."));
			// The choice that applies draws luck for its outcome, then for that
			// outcome's effects, Nation by Nation and Dilemma by Dilemma. It
			// applies once work has progressed, so work it stalls or speeds up
			// finishes no earlier than as the next Turn resolves.
			for (Nation.FacedDilemma faced : nation.settleDilemmas()) {
				Event.Outcome outcome = faced.applying().outcome(random);
				Line line = announced(outcome.text(),
						befall(faced.dilemma().name(), outcome.effects(), nation, reports, witnessed));
				report.add(EVENTS, faced.chosen() == null
						? Line.of("You did not choose in time what to do about " + faced.dilemma().name() + ". ", line)
						: line);
			}
		}
		for (Nation nation : inPlay) {
			for (Warparty returned : nation.travelHome()) {
				Line plunder = returned.plunder().isNone() ? Line.of()
						: Line.of(" with ", returned.plunder().described(Effect.GAIN), ",");
				reports.get(nation.name())
					.add(WARFARE, Line.of("Your Warparty came home from " + returned.target(), plunder,
							" and " + listed(returned.units()) + " rejoined your Army."));
			}
			for (Spy returned : nation.spiesTravelHome()) {
				reports.get(nation.name())
					.add(SPIES_AND_SCOUTS, returned.haul() == null
							? Line.of("Your Spy came home from " + returned.target() + " and rejoined your Army.")
							: Line.of("Your Spy came home from " + returned.target() + " with ",
									gain(returned.haul().resource(), returned.haul().amount()),
									" and joined your Spies at home."));
			}
		}
		camps.forEach(Camp::travelHome);
		deliverHauls(reports);
		settleHauls(reports);
		plunderTaken.forEach(Runnable::run);
		// A Nation conquered earlier in this Turn no longer fights, and its
		// Warparties are gone.
		for (Optional<String> target = nextBattle(); target.isPresent(); target = nextBattle()) {
			Optional<Camp> camp = camp(target.get());
			if (camp.isPresent()) {
				reportBattle(fightAt(camp.get()), reports);
			}
			else {
				reportBattle(fight(nation(target.get())), reports);
			}
		}
		for (Camp camp : camps) {
			for (Warparty arrived : camp.travelOut()) {
				nation(arrived.target()).hearOf(camp.name());
				reports.get(arrived.target())
					.add(DECISIONS_AND_ALERTS, camp.ruled()
							+ "'s Warparty arrived at your Nation; the Battle will be fought as this Turn ends.");
			}
		}
		for (Nation nation : remaining()) {
			for (Warparty arrived : nation.travelOut()) {
				reports.get(nation.name())
					.add(WARFARE, "Your Warparty of " + listed(arrived.units()) + " arrived at " + arrived.target()
							+ "; the Battle will be fought as this Turn ends.");
				if (camp(arrived.target()).isEmpty()) {
					reports.get(arrived.target())
						.add(DECISIONS_AND_ALERTS, nation.ruled()
								+ "'s Warparty arrived at your Nation; the Battle will be fought as this Turn ends.");
				}
			}
			for (Spy arrived : nation.spiesTravelOut()) {
				reports.get(nation.name()).add(SPIES_AND_SCOUTS, "Your Spy arrived at " + arrived.target() + ".");
			}
		}
		Map<String, Nation.Change> gains = new LinkedHashMap<>();
		for (Nation nation : remaining()) {
			TurnReport.Draft report = reports.get(nation.name());
			int goldBefore = nation.gold();
			int researchBefore = nation.research();
			IncomeAndUpkeep earnings = earnAndPayUpkeep(nation, random);
			gains.put(nation.name(),
					new Nation.Change(nation.gold() - goldBefore, 0, nation.research() - researchBefore));
			List<Line> grown = new ArrayList<>();
			if (earnings.populationGrowth() > 0) {
				grown.add(Line.of(gain(POPULATION, earnings.populationGrowth())));
			}
			grown.add(Line.of(gain(RESEARCH, RESEARCH_PER_TURN)));
			report.add(RESOURCES, Line.of("Your Population earned ", gain(GOLD, earnings.populationGold()),
					", and your Nation gained ", Line.joined(grown), "."));
			buildingsAddedLine(earnings.buildingPopulation(), earnings.buildingGold(), earnings.buildingResearch())
				.ifPresent(line -> report.add(RESOURCES, line));
			for (Nation.Desertion desertion : earnings.desertions()) {
				if (desertion.scouts()) {
					report.add(RESOURCES, "You could not pay your Units' upkeep, so " + scoutsCounted(desertion.units())
							+ " roaming the wilds deserted.");
					continue;
				}
				if (desertion.spy() != null) {
					report.add(RESOURCES, "You could not pay your Units' upkeep, so your Spy sent to " + desertion.spy().target()
							+ " deserted.");
					continue;
				}
				String from = desertion.warparty() == null ? "your Army"
						: "your Warparty sent to " + desertion.warparty().target();
				report.add(RESOURCES, "You could not pay your Units' upkeep, so " + listed(desertion.units()) + " deserted from "
						+ from + ".");
			}
			if (earnings.upkeep() > 0) {
				report.add(RESOURCES, Line.of("Your Units' upkeep cost ", loss(GOLD, earnings.upkeep()), "."));
			}
			if (earnings.lasting() != null) {
				LastingEvent lasting = earnings.lasting();
				List<Line> changes = new ArrayList<>();
				if (lasting.noGrowth()) {
					changes.add(Line.of("no Population growth"));
				}
				resourcesChanged(earnings.lastingChange()).ifPresent(changes::add);
				if (!changes.isEmpty()) {
					report.add(RESOURCES, Line.of(lasting.name() + ": ", Line.joined(changes), "."));
				}
				if (lasting.turnsLeft() == 1) {
					report.add(EVENTS, lasting.name() + " has ended.");
				}
			}
		}
		takeHauls(gains, reports);
		rollEvents(reports, witnessed);
		watchForWarparties(reports);
		catchSpies(reports, witnessed);
		escape(reports, witnessed);
		workOnMissions(reports, witnessed);
		tellWitnesses(reports, witnessed);
		barbarians();
		meet(reports);
		List<Nation> left = remaining();
		if (left.size() == 1) {
			winner = left.getFirst().name();
			nations.values().forEach(Nation::changed);
			reports.forEach((nation, report) -> report
				.add(DECISIONS_AND_ALERTS, nation.equals(winner) ? "You have won the Game." : winner + " has won the Game."));
		}
		inPlay.forEach(nation -> nation.turnReport(reports.get(nation.name()).of(turn, nation)));
		turn++;
	}

	/**
	 * What a Nation earned and paid as a Turn resolved.
	 *
	 * @param populationGold the Gold its Population earned
	 * @param populationGrowth the flat amount its Population grew by
	 * @param buildingGold the Gold its Buildings added
	 * @param buildingPopulation the Population its Buildings added
	 * @param buildingResearch the Research its Buildings added
	 * @param lasting its Lasting Event as it applied, or null if none did
	 * @param lastingChange how much its Lasting Event changed its resources by
	 * @param desertions the Units that deserted because their upkeep could not
	 *        be paid
	 * @param upkeep the Gold its remaining Units' upkeep cost
	 */
	private record IncomeAndUpkeep(int populationGold, int populationGrowth, int buildingGold, int buildingPopulation,
			int buildingResearch, LastingEvent lasting, Nation.Change lastingChange, List<Nation.Desertion> desertions,
			int upkeep) {
	}

	/**
	 * Gives a Nation its income for the Turn, applies its Lasting Event, lets
	 * the Units it cannot pay for desert, then pays the rest's upkeep.
	 *
	 * @param luck the random source that picks which of the Nation's Scouts
	 *        desert (see {@link Nation#desertUntilUpkeepIsAffordable})
	 */
	private IncomeAndUpkeep earnAndPayUpkeep(Nation nation, RandomGenerator luck) {
		Optional<LastingEvent> lasting = nation.passLastingTurn(turn);
		boolean grows = lasting.map(applying -> !applying.noGrowth()).orElse(true);
		int populationGrowth = grows ? POPULATION_PER_TURN : 0;
		int buildingPopulation = grows ? nation.populationFromBuildings(ruleset) : 0;
		int buildingGold = nation.goldFromBuildings(ruleset);
		int buildingResearch = nation.researchFromBuildings(ruleset);
		int populationGold = nation.goldFromPopulation();
		nation.gain(populationGold + buildingGold, populationGrowth + buildingPopulation,
				RESEARCH_PER_TURN + buildingResearch);
		Nation.Change lastingChange = lasting
			.map(applying -> nation.change(applying.gold(), applying.population(), applying.research()))
			.orElse(new Nation.Change(0, 0, 0));
		List<Nation.Desertion> desertions = nation.desertUntilUpkeepIsAffordable(ruleset, luck);
		return new IncomeAndUpkeep(populationGold, populationGrowth, buildingGold, buildingPopulation, buildingResearch,
				lasting.orElse(null), lastingChange, desertions, nation.payUpkeep(ruleset));
	}

	/**
	 * The Gold a Nation will gain or lose as this Turn resolves, worked out by
	 * resolving a copy of it the way {@link #endTurn} does, leaving out what
	 * Dilemmas, Battles, Plunder, Tribute and Events would change, though not
	 * what its active Lasting Event will. Warparties and Spies coming home
	 * change no Gold but Plunder, and leave its Units' upkeep the same, so
	 * they are left out too. It never draws from the seeded random source:
	 * which of its Scouts would desert changes no Gold.
	 */
	private GoldForecast goldForecast(Nation nation) {
		Nation next = nation.copy();
		next.progressWork(turn + 1, ruleset);
		IncomeAndUpkeep earnings = earnAndPayUpkeep(next, FORECAST_LUCK);
		return new GoldForecast(earnings.populationGold() + earnings.buildingGold(), earnings.upkeep(),
				earnings.lastingChange().gold());
	}

	/**
	 * The Nation or Barbarian Camp the next Battle is fought at: the target of
	 * the first Warparty awaiting a Battle, taking the Nations in order, then
	 * the Camps in order, and each one's Warparties in the order sent.
	 */
	private Optional<String> nextBattle() {
		return senders().stream()
			.flatMap(sender -> sender.awaitingBattle().stream())
			.map(Warparty::target)
			.findFirst();
	}

	/**
	 * A Warparty that arrived at a Nation or Barbarian Camp, and the Nation or
	 * Camp that sent it.
	 */
	private record Arrival(Sender attacker, Warparty warparty) {

		String name() {
			return attacker.name();
		}

		boolean camp() {
			return attacker.camp();
		}

	}

	/**
	 * A Battle fought at a Nation, what each attacking Warparty took, and the
	 * Defense and Population the Nation lost.
	 *
	 * @param arrivals the attacking Warparties, as they were before the Battle,
	 *        in the order they fought in the Battle
	 * @param located the attacking Nations the Nation Located by the Battle,
	 *        not having Located them before, in the order they fought
	 * @param shares the plunder each attacking Warparty took or, if the
	 *        Nation was conquered, its share of what was seized
	 * @param buildingsDamaged how many of each of the Nation's non-defensive
	 *        Building types were Damaged, in the order first Damaged
	 * @param exposure how exposed the Nation's Population was
	 * @param conquest how the Nation was conquered, or null if it was not
	 */
	private record BattleFought(Nation defender, List<Arrival> arrivals, Battle battle, List<String> located,
			List<Plunder> shares, Nation.DefenseWorn defenseWorn, Map<String, Integer> buildingsDamaged, int populationLost, Exposure exposure,
			Nation.Conquest conquest) {

		/**
		 * Whether some of the attacking Warparty's Units at the given index
		 * survived.
		 */
		boolean survived(int arrival) {
			return !battle.warpartySurvivors(arrival).isEmpty();
		}

		/**
		 * The attacking Warparties some of whose Units survived.
		 */
		List<Arrival> survivors() {
			return IntStream.range(0, arrivals.size()).filter(this::survived).mapToObj(arrivals::get).toList();
		}

	}

	/**
	 * The Warparties awaiting a Battle at a Nation or Barbarian Camp, from the
	 * Nations in order, then the Camps in order, and each one's in the order
	 * sent.
	 */
	private List<Arrival> arrivalsAt(String target) {
		List<Arrival> arrivals = new ArrayList<>();
		for (Sender attacker : senders()) {
			attacker.awaitingBattle()
				.stream()
				.filter(warparty -> warparty.target().equals(target))
				.forEach(warparty -> arrivals.add(new Arrival(attacker, warparty)));
		}
		return arrivals;
	}

	/**
	 * Fights the Battle at a Nation: every Warparty awaiting a Battle there,
	 * from the Nations in order, then the Barbarian Camps in order, and each
	 * one's in the order sent, fights its Army as one. The Warparties'
	 * surviving Strength, rounded down, wears the Nation's Defense down, and
	 * what is left once Defense is 0 reduces its Population, more so if its
	 * Army was wiped out or it had neither Army nor Defense. If the Nation is
	 * then Breached, half of what is left, rounded down, Damages one of its
	 * undamaged non-defensive Buildings for every
	 * {@value #STRENGTH_PER_BUILDING_DAMAGED} Strength (see
	 * {@link #damageBuildings}), and whatever of that half cannot be used
	 * reduces its Population with the rest. A Nation left with neither
	 * Population nor Army is conquered, unless the Nations' Warparties were
	 * all wiped out: Camps never conquer, and unless some of the Nations'
	 * Warparties survive, the Battle always leaves it some Population. Otherwise the survivors take plunder scaled by their
	 * remaining Strength, rounded down: Gold for all of them, Research for
	 * the Nations' and Captives for the Camps', which never take the last of
	 * its Population. What is plundered or seized is split between the
	 * Warparties taking it in proportion to their surviving Strength, so one
	 * wiped out gets none of it. The Nation Locates every attacking Nation and
	 * hears of every attacking Camp.
	 */
	private BattleFought fight(Nation defender) {
		List<Arrival> arrivals = arrivalsAt(defender.name());
		Battle battle = Battle.fight(arrivals.stream().map(arrival -> arrival.warparty().units()).toList(),
				defender.army(), ruleset, random);
		List<String> located = arrivals.stream()
			.filter(arrival -> !arrival.camp())
			.map(Arrival::name)
			.distinct()
			.filter(defender::locate)
			.toList();
		arrivals.stream().filter(Arrival::camp).forEach(arrival -> defender.hearOf(arrival.name()));
		defender.lose(battle.armyLost());
		List<Double> strengths = IntStream.range(0, arrivals.size())
			.mapToObj(arrival -> battle.warpartySurvivingStrength(arrival, ruleset))
			.toList();
		double strength = strengths.stream().mapToDouble(Double::doubleValue).sum();
		int wholeStrength = (int) Math.floor(strength);
		Nation.DefenseWorn defenseWorn = defender.wearDefense(wholeStrength, ruleset);
		int strengthLeft = wholeStrength - defenseWorn.worn();
		Map<String, Integer> buildingsDamaged = Map.of();
		if (defender.breached(ruleset)) {
			int againstBuildings = strengthLeft / 2;
			buildingsDamaged = damageBuildings(defender, againstBuildings / STRENGTH_PER_BUILDING_DAMAGED);
			strengthLeft -= buildingsDamaged.values().stream().mapToInt(Integer::intValue).sum()
					* STRENGTH_PER_BUILDING_DAMAGED;
		}
		Exposure exposure = Exposure.of(battle, defenseWorn);
		int multiplier = exposure == Exposure.DEFENDED ? 1 : EXPOSED_POPULATION_MULTIPLIER;
		int killing = strengthLeft * POPULATION_PER_STRENGTH * multiplier;
		IntPredicate nationSurvived = arrival -> !arrivals.get(arrival).camp()
				&& !battle.warpartySurvivors(arrival).isEmpty();
		boolean mayConquer = IntStream.range(0, arrivals.size()).anyMatch(nationSurvived);
		int populationLost = defender.losePopulation(mayConquer ? killing : Math.min(killing, spared(defender)));
		boolean conquered = mayConquer && defender.hasNeitherPopulationNorArmy();
		List<Plunder> shares;
		Plunder taken;
		if (conquered) {
			List<String> conquerors = IntStream.range(0, arrivals.size())
				.filter(nationSurvived)
				.mapToObj(arrival -> arrivals.get(arrival).name())
				.distinct()
				.toList();
			taken = defender.conquer(joined(conquerors), CONQUEST_SHARE);
			shares = shared(taken, weights(battle, strengths, arrival -> !arrivals.get(arrival).camp()));
		}
		else {
			double campStrength = IntStream.range(0, arrivals.size())
				.filter(arrival -> arrivals.get(arrival).camp())
				.mapToDouble(strengths::get)
				.sum();
			Plunder plundered = defender.plundered((int) Math.floor(strength * PLUNDER_GOLD_PER_STRENGTH),
					(int) Math.floor((strength - campStrength) * PLUNDER_RESEARCH_PER_STRENGTH));
			int captives = defender.losePopulation(
					Math.min((int) Math.floor(campStrength * CAPTIVES_PER_STRENGTH), spared(defender)));
			taken = new Plunder(plundered.gold(), plundered.research(), captives);
			List<Integer> gold = apportioned(taken.gold(), weights(battle, strengths, arrival -> true));
			List<Integer> research = apportioned(taken.research(),
					weights(battle, strengths, arrival -> !arrivals.get(arrival).camp()));
			List<Integer> carried = apportioned(taken.captives(),
					weights(battle, strengths, arrival -> arrivals.get(arrival).camp()));
			shares = IntStream.range(0, arrivals.size())
				.mapToObj(arrival -> new Plunder(gold.get(arrival), research.get(arrival), carried.get(arrival)))
				.toList();
		}
		for (int i = 0; i < arrivals.size(); i++) {
			Sender attacker = arrivals.get(i).attacker();
			if (conquered && attacker instanceof Nation conqueror) {
				conqueror.seize(shares.get(i));
			}
			attacker.foughtBattle(arrivals.get(i).warparty(), battle.warpartySurvivors(i),
					conquered ? Plunder.NONE : shares.get(i), shares.get(i), ruleset);
		}
		return new BattleFought(defender, arrivals, battle, located, shares, defenseWorn, buildingsDamaged, populationLost,
				exposure, conquered ? turnBackFrom(defender, taken) : null);
	}

	/**
	 * How much of a Nation's Population a Barbarian Camp may take: all but
	 * the last of it.
	 */
	private static int spared(Nation nation) {
		return Math.max(0, nation.population() - 1);
	}

	/**
	 * The weights the attacking Warparties taking part in a share of a
	 * Battle's plunder, seizure or Bounty take it in: each one's surviving
	 * Strength, or, if those taking part have none at all, an equal share for
	 * each with survivors. Those not taking part get none.
	 *
	 * @param taking which of the attacking Warparties, by index, take part
	 */
	private static List<Double> weights(Battle battle, List<Double> strengths, IntPredicate taking) {
		double strength = IntStream.range(0, strengths.size()).filter(taking).mapToDouble(strengths::get).sum();
		return IntStream.range(0, strengths.size())
			.mapToObj(arrival -> !taking.test(arrival) ? 0.0
					: strength > 0 ? strengths.get(arrival) : battle.warpartySurvivors(arrival).isEmpty() ? 0.0 : 1.0)
			.toList();
	}

	/**
	 * A Battle fought at a Barbarian Camp.
	 *
	 * @param arrivals the attacking Warparties, as they were before the Battle,
	 *        in the order they fought in the Battle
	 * @param bounties each attacking Warparty's share of the Bounty, none if
	 *        the Camp was not destroyed
	 * @param destroyed whether the Battle destroyed the Camp
	 * @param turnedBack the other Warparties heading to the Camp that turned
	 *        back home as it was destroyed, as they were before, by Nation
	 * @param tasksEnded the Tasks Locating the Camp that ended as it was
	 *        destroyed, by Nation
	 */
	private record CampBattle(Camp camp, List<Arrival> arrivals, Battle battle, Nation.DefenseWorn defenseWorn,
			List<Plunder> bounties, boolean destroyed, Map<String, List<Warparty>> turnedBack,
			Map<String, List<Task>> tasksEnded) {

		boolean survived(int arrival) {
			return !battle.warpartySurvivors(arrival).isEmpty();
		}

	}

	/**
	 * Fights the Battle at a Barbarian Camp: every Nation's Warparty awaiting a
	 * Battle there, from the Nations in order and each one's in the order
	 * sent, fights its Army as one, and the Warparties' surviving Strength,
	 * rounded down, wears its Defense down. Every attacking Nation becomes one
	 * of its Targets. A Battle that leaves it with no Army and its Defense
	 * worn down to 0, some of the Warparties surviving, destroys it: one draw from the seeded random
	 * source sets the Bounty, from {@value #LEAST_BOUNTY} to
	 * {@value #MOST_BOUNTY} Gold, split between the survivors as plunder is;
	 * every other Warparty heading to it turns back home, and every Task
	 * Locating it ends. The survivors take nothing else, and head home.
	 */
	private CampBattle fightAt(Camp camp) {
		List<Arrival> arrivals = arrivalsAt(camp.name());
		Battle battle = Battle.fight(arrivals.stream().map(arrival -> arrival.warparty().units()).toList(),
				camp.army(), ruleset, random);
		camp.lose(battle.armyLost());
		arrivals.forEach(arrival -> camp.target(arrival.name()));
		List<Double> strengths = IntStream.range(0, arrivals.size())
			.mapToObj(arrival -> battle.warpartySurvivingStrength(arrival, ruleset))
			.toList();
		int wholeStrength = (int) Math.floor(strengths.stream().mapToDouble(Double::doubleValue).sum());
		Nation.DefenseWorn defenseWorn = camp.wearDefense(wholeStrength, ruleset.barbarians());
		boolean destroyed = battle.anyWarpartySurvived() && camp.army().isEmpty() && defenseWorn.after() == 0;
		Plunder bounty = destroyed
				? new Plunder(LEAST_BOUNTY + (int) (random.nextDouble() * (MOST_BOUNTY - LEAST_BOUNTY + 1)), 0)
				: Plunder.NONE;
		List<Plunder> bounties = shared(bounty, weights(battle, strengths, arrival -> true));
		for (int i = 0; i < arrivals.size(); i++) {
			arrivals.get(i)
				.attacker()
				.foughtBattle(arrivals.get(i).warparty(), battle.warpartySurvivors(i), bounties.get(i), bounties.get(i),
						ruleset);
		}
		Map<String, List<Warparty>> turnedBack = new LinkedHashMap<>();
		Map<String, List<Task>> tasksEnded = new LinkedHashMap<>();
		if (destroyed) {
			camp.destroy();
			for (Nation nation : remaining()) {
				List<Warparty> turned = nation.turnBackFrom(camp.name());
				if (!turned.isEmpty()) {
					turnedBack.put(nation.name(), turned);
				}
				List<Task> ended = nation.endTasksFor(camp.name());
				if (!ended.isEmpty()) {
					tasksEnded.put(nation.name(), ended);
				}
			}
		}
		return new CampBattle(camp, arrivals, battle, defenseWorn, bounties, destroyed, turnedBack, tasksEnded);
	}

	/**
	 * Tells each attacking Nation how its Battle at a Barbarian Camp went, and
	 * every other Nation that has heard of the Camp that it was destroyed.
	 */
	private void reportBattle(CampBattle fought, Map<String, TurnReport.Draft> reports) {
		Battle battle = fought.battle();
		List<Arrival> arrivals = fought.arrivals();
		String camp = fought.camp().name();
		String army = battle.army().isEmpty() ? null : listed(battle.army());
		for (int i = 0; i < arrivals.size(); i++) {
			String attacker = arrivals.get(i).name();
			String warparty = listed(battle.warparties().get(i));
			List<String> alongside = new ArrayList<>();
			for (int j = 0; j < arrivals.size(); j++) {
				if (j != i) {
					String other = arrivals.get(j).name();
					alongside.add((other.equals(attacker) ? "your" : other + "'s") + " Warparty of "
							+ listed(battle.warparties().get(j)));
				}
			}
			String allies = alongside.isEmpty() ? "" : " alongside " + joined(alongside);
			TurnReport.Draft report = reports.get(attacker);
			report.add(WARFARE, army == null
					? "Your Warparty of " + warparty + " met no Army at " + camp + (allies.isEmpty() ? "" : "," + allies)
							+ "."
					: "Your Warparty of " + warparty + " fought " + camp + "'s Army of " + army + allies + ". You lost "
							+ lost(battle.warpartiesLost().get(i)) + " and " + camp + " lost " + lost(battle.armyLost())
							+ ".");
			if (!fought.survived(i)) {
				report.add(WARFARE, "Your Warparty sent to " + camp + " was destroyed.");
			}
		}
		List<Integer> surviving = IntStream.range(0, arrivals.size()).filter(fought::survived).boxed().toList();
		List<String> attackers = surviving.stream().map(arrival -> arrivals.get(arrival).name()).distinct().toList();
		Nation.DefenseWorn defenseWorn = fought.defenseWorn();
		if (defenseWorn.worn() > 0) {
			attackers.forEach(attacker -> reports.get(attacker)
				.add(WARFARE, warpartiesFrom(attacker, surviving.stream().map(arrivals::get).toList()) + " wore " + camp
						+ "'s Defense down by " + defenseWorn.worn() + ", " + buildingsHarmed(defenseWorn) + "."));
		}
		String share = surviving.size() > 1 ? " as its share" : "";
		for (int i : surviving) {
			TurnReport.Draft report = reports.get(arrivals.get(i).name());
			if (fought.destroyed()) {
				report.add(WARFARE, Line.of("Your Warparty destroyed " + camp + ", taking ",
						gain(GOLD, fought.bounties().get(i).gold()), " as a Bounty" + share + ", and is heading home."));
			}
			else if (fought.camp().army().isEmpty()) {
				report.add(WARFARE, "Your Warparty is heading home from " + camp + ", whose Defense of "
						+ defenseWorn.after() + " still stands, so it was not destroyed.");
			}
			else {
				report.add(WARFARE, "Your Warparty is heading home from " + camp + ".");
			}
		}
		if (!fought.destroyed()) {
			return;
		}
		for (Nation nation : remaining()) {
			if (nation.hasHeardOf(camp) && !attackers.contains(nation.name())) {
				reports.get(nation.name())
					.add(WARFARE, joined(attackers.stream().map(this::ruled).toList()) + " destroyed " + camp + ".");
			}
		}
		fought.turnedBack()
			.forEach((nation, turned) -> turned.forEach(back -> reports.get(nation)
				.add(WARFARE, "Your Warparty of " + listed(back.units()) + " heading to " + camp
						+ " turned back home, as " + camp + " was destroyed.")));
		fought.tasksEnded()
			.forEach((nation, ended) -> ended
				.forEach(task -> reports.get(nation).add(SPIES_AND_SCOUTS, taskEnded(task, camp + " was destroyed"))));
	}

	/**
	 * Gold and Research split in proportion to the weights given; see
	 * {@link #apportioned(int, List)}.
	 */
	private static List<Plunder> shared(Plunder plunder, List<Double> weights) {
		List<Integer> gold = apportioned(plunder.gold(), weights);
		List<Integer> research = apportioned(plunder.research(), weights);
		return IntStream.range(0, weights.size()).mapToObj(i -> new Plunder(gold.get(i), research.get(i))).toList();
	}

	/**
	 * An amount split in proportion to the weights given, each share rounded
	 * down, the leftover going one at a time to the shares with the largest
	 * remainders, and between equal remainders to the one listed first.
	 */
	private static List<Integer> apportioned(int amount, List<Double> weights) {
		double total = weights.stream().mapToDouble(Double::doubleValue).sum();
		int[] shares = new int[weights.size()];
		if (total == 0) {
			return Arrays.stream(shares).boxed().toList();
		}
		double[] exact = weights.stream().mapToDouble(weight -> amount * weight / total).toArray();
		int given = 0;
		for (int i = 0; i < shares.length; i++) {
			shares[i] = (int) Math.floor(exact[i]);
			given += shares[i];
		}
		List<Integer> byRemainder = new ArrayList<>(IntStream.range(0, shares.length).boxed().toList());
		byRemainder.sort(Comparator.comparingDouble((Integer i) -> exact[i] - Math.floor(exact[i])).reversed());
		for (int i = 0; given < amount; i++, given++) {
			shares[byRemainder.get(i)]++;
		}
		return Arrays.stream(shares).boxed().toList();
	}

	/**
	 * Records a Nation as conquered, turns every other Warparty heading to it,
	 * and Spy at it or heading to it, back home, and ends every Task about it.
	 */
	private Nation.Conquest turnBackFrom(Nation conquered, Plunder seized) {
		this.conquered.add(conquered.name());
		camps.forEach(camp -> camp.forget(conquered.name()));
		Map<String, List<Warparty>> turnedBack = new LinkedHashMap<>();
		Map<String, List<Spy>> spiesTurnedBack = new LinkedHashMap<>();
		Map<String, List<Task>> tasksEnded = new LinkedHashMap<>();
		for (Nation nation : remaining()) {
			List<Warparty> turned = nation.turnBackFrom(conquered.name());
			if (!turned.isEmpty()) {
				turnedBack.put(nation.name(), turned);
			}
			List<Spy> spies = nation.turnSpiesBackFrom(conquered.name());
			if (!spies.isEmpty()) {
				spiesTurnedBack.put(nation.name(), spies);
			}
			List<Task> ended = nation.endTasksFor(conquered.name());
			if (!ended.isEmpty()) {
				tasksEnded.put(nation.name(), ended);
			}
		}
		return new Nation.Conquest(seized, turnedBack, spiesTurnedBack, tasksEnded);
	}

	private void reportBattle(BattleFought fought, Map<String, TurnReport.Draft> reports) {
		Battle battle = fought.battle();
		List<Arrival> arrivals = fought.arrivals();
		String defender = fought.defender().name();
		TurnReport.Draft defenderReport = reports.get(defender);
		String army = battle.army().isEmpty() ? null : listed(battle.army());
		List<String> sides = new ArrayList<>();
		List<String> losses = new ArrayList<>(List.of("You lost " + lost(battle.armyLost())));
		for (int i = 0; i < arrivals.size(); i++) {
			String attacker = arrivals.get(i).name();
			String warparty = listed(battle.warparties().get(i));
			sides.add(ruled(attacker) + "'s Warparty of " + warparty);
			losses.add(attacker + " lost " + lost(battle.warpartiesLost().get(i)));
			if (arrivals.get(i).camp()) {
				continue;
			}
			List<String> alongside = new ArrayList<>();
			for (int j = 0; j < arrivals.size(); j++) {
				if (j != i) {
					String other = arrivals.get(j).name();
					alongside.add((other.equals(attacker) ? "your" : other + "'s") + " Warparty of "
							+ listed(battle.warparties().get(j)));
				}
			}
			String allies = alongside.isEmpty() ? "" : " alongside " + joined(alongside);
			reports.get(attacker)
				.add(WARFARE, army == null
						? "Your Warparty of " + warparty + " met no Army at " + defender
								+ (allies.isEmpty() ? "" : "," + allies) + "."
						: "Your Warparty of " + warparty + " fought " + defender + "'s Army of " + army + allies
								+ ". You lost " + lost(battle.warpartiesLost().get(i)) + " and " + defender + " lost "
								+ lost(battle.armyLost()) + ".");
		}
		defenderReport.add(WARFARE, army == null ? joined(sides) + " met no Army at your Nation."
				: "Your Army of " + army + " fought " + joined(sides) + ". " + joined(losses) + ".");
		fought.located()
			.forEach(attacker -> defenderReport.add(SPIES_AND_SCOUTS,
					"The Battle revealed where " + attacker + " lies; you have Located it."));
		for (int i = 0; i < arrivals.size(); i++) {
			if (!fought.survived(i)) {
				String attacker = arrivals.get(i).name();
				if (!arrivals.get(i).camp()) {
					reports.get(attacker).add(WARFARE, "Your Warparty sent to " + defender + " was destroyed.");
				}
				defenderReport.add(WARFARE, ruled(attacker) + "'s Warparty was destroyed.");
			}
		}
		List<Arrival> survivors = fought.survivors();
		if (survivors.isEmpty()) {
			return;
		}
		List<String> attackers = survivors.stream().filter(arrival -> !arrival.camp()).map(Arrival::name).distinct().toList();
		Nation.DefenseWorn defenseWorn = fought.defenseWorn();
		if (defenseWorn.worn() > 0) {
			attackers.forEach(attacker -> reports.get(attacker)
				.add(WARFARE, warpartiesFrom(attacker, survivors) + " wore " + defender + "'s Defense down by "
						+ defenseWorn.worn() + ", " + buildingsHarmed(defenseWorn) + "."));
			defenderReport.add(WARFARE, warpartiesOf(survivors) + " wore your Defense down from " + defenseWorn.before()
					+ " to " + defenseWorn.after() + ", " + buildingsHarmed(defenseWorn) + ".");
		}
		Map<String, Integer> buildingsDamaged = fought.buildingsDamaged();
		if (!buildingsDamaged.isEmpty()) {
			attackers.forEach(attacker -> reports.get(attacker)
				.add(WARFARE, warpartiesFrom(attacker, survivors) + " damaged " + defender + "'s "
						+ listed(buildingsDamaged) + "."));
			defenderReport.add(WARFARE, warpartiesOf(survivors) + " damaged your " + listed(buildingsDamaged) + ".");
		}
		if (fought.populationLost() > 0) {
			String attackerReason = switch (fought.exposure()) {
				case DEFENDED -> "";
				case ARMY_WIPED_OUT -> ", doubled as " + defender + "'s Army was wiped out";
				case UNDEFENDED -> ", doubled as " + defender + " had no Army or Defense";
			};
			String defenderReason = switch (fought.exposure()) {
				case DEFENDED -> "";
				case ARMY_WIPED_OUT -> ", doubled as your Army was wiped out";
				case UNDEFENDED -> ", doubled as you had no Army or Defense";
			};
			attackers.forEach(attacker -> reports.get(attacker)
				.add(WARFARE, Line.of(warpartiesFrom(attacker, survivors) + " cost " + defender + " ",
						neutral(POPULATION, fought.populationLost()), attackerReason + ".")));
			defenderReport.add(WARFARE, Line.of(warpartiesOf(survivors) + " cost your Nation ",
					loss(POPULATION, fought.populationLost()), defenderReason + "."));
		}
		Nation.Conquest conquest = fought.conquest();
		if (conquest != null) {
			List<Arrival> conquering = survivors.stream().filter(arrival -> !arrival.camp()).toList();
			Plunder seized = conquest.seized();
			if (conquering.size() == 1) {
				reports.get(attackers.getFirst())
					.add(WARFARE, Line.of("Your Warparty conquered " + defender + ", seizing ",
							seized.described(Effect.GAIN), ", and is heading home."));
			}
			else {
				for (String attacker : attackers) {
					reports.get(attacker).add(WARFARE, warpartiesFrom(attacker, conquering) + " conquered " + defender + ".");
					for (int i = 0; i < arrivals.size(); i++) {
						if (fought.survived(i) && arrivals.get(i).name().equals(attacker)) {
							reports.get(attacker)
								.add(WARFARE, Line.of("Your Warparty of " + listed(battle.warparties().get(i)) + " seized ",
										fought.shares().get(i).described(Effect.GAIN),
										" as its share and is heading home."));
						}
					}
				}
			}
			defenderReport.add(DECISIONS_AND_ALERTS, Line.of(warpartiesOf(conquering) + " conquered your Nation, seizing ",
					seized.described(Effect.LOSS), ". You are knocked out of the Game."));
			reports.forEach((nation, report) -> {
				if (!attackers.contains(nation) && !nation(nation).conquered()) {
					report.add(DECISIONS_AND_ALERTS, joined(attackers.stream().map(this::ruled).toList()) + " conquered " + defender + ".");
				}
			});
			conquest.turnedBack()
				.forEach((nation, turned) -> turned.forEach(back -> reports.get(nation)
					.add(WARFARE, "Your Warparty of " + listed(back.units()) + " heading to " + defender
							+ " turned back home, as " + defender + " was conquered.")));
			conquest.spiesTurnedBack()
				.forEach((nation, turned) -> turned
					.forEach(back -> reports.get(nation).add(SPIES_AND_SCOUTS, spyTurnedBack(back, defender + " was conquered"))));
				conquest.tasksEnded()
					.forEach((nation, ended) -> ended
					.forEach(task -> reports.get(nation).add(SPIES_AND_SCOUTS, taskEnded(task, defender + " was conquered"))));
			return;
		}
		String share = survivors.size() > 1 ? " as its share" : "";
		for (int i = 0; i < arrivals.size(); i++) {
			if (!fought.survived(i)) {
				continue;
			}
			String attacker = arrivals.get(i).name();
			boolean camp = arrivals.get(i).camp();
			Plunder plunder = fought.shares().get(i);
			if (plunder.isNone()) {
				if (!camp) {
					reports.get(attacker).add(WARFARE, "Your Warparty took nothing from " + defender + " and is heading home.");
				}
				defenderReport.add(WARFARE, ruled(attacker) + "'s Warparty took nothing from you and is heading home.");
			}
			else {
				if (!camp) {
					reports.get(attacker)
						.add(WARFARE, Line.of("Your Warparty took ", plunder.described(Effect.GAIN),
								" from " + defender + share + " and is heading home."));
				}
				defenderReport.add(WARFARE, Line.of(ruled(attacker) + "'s Warparty took ",
						camp ? plunder.carriedOff(Effect.LOSS) : plunder.described(Effect.LOSS),
						" from you and is heading home."));
			}
		}
	}

	/**
	 * Such as "Caesar of Rome's and Cleopatra of Egypt's Warparties", for the
	 * Nation they attacked.
	 */
	private static String warpartiesOf(List<Arrival> arrivals) {
		List<String> nations = arrivals.stream()
			.map(arrival -> arrival.attacker().ruled() + "'s")
			.distinct()
			.toList();
		return joined(nations) + (arrivals.size() == 1 ? " Warparty" : " Warparties");
	}

	/**
	 * Such as "Your Warparty and Egypt's", for a Nation that sent at least one
	 * of them.
	 */
	private static String warpartiesFrom(String nation, List<Arrival> arrivals) {
		long own = arrivals.stream().filter(arrival -> arrival.attacker().name().equals(nation)).count();
		List<String> others = arrivals.stream()
			.map(arrival -> arrival.attacker().name())
			.filter(other -> !other.equals(nation))
			.distinct()
			.map(other -> other + "'s")
			.toList();
		return (own == 1 ? "Your Warparty" : "Your Warparties") + (others.isEmpty() ? "" : " and " + joined(others));
	}

	/**
	 * Such as "destroying Walls ×1 and damaging Moat".
	 */
	private static String buildingsHarmed(Nation.DefenseWorn defenseWorn) {
		List<String> harmed = new ArrayList<>();
		if (!defenseWorn.destroyed().isEmpty()) {
			harmed.add("destroying " + listed(defenseWorn.destroyed()));
		}
		if (!defenseWorn.damaged().isEmpty()) {
			harmed.add("damaging " + joined(defenseWorn.damaged()));
		}
		return String.join(" and ", harmed);
	}

	/**
	 * Damages up to the given number of a Nation's undamaged non-defensive
	 * Buildings, each picked by one draw from the seeded random source from
	 * those still undamaged (see {@link Nation#undamagedNonDefensive}).
	 *
	 * @return how many of each Building type were Damaged, in the order first
	 *         Damaged
	 */
	private Map<String, Integer> damageBuildings(Nation nation, int count) {
		Map<String, Integer> damaged = new LinkedHashMap<>();
		for (int left = count; left > 0; left--) {
			List<String> undamaged = nation.undamagedNonDefensive(ruleset);
			if (undamaged.isEmpty()) {
				break;
			}
			String building = undamaged.get((int) (random.nextDouble() * undamaged.size()));
			nation.damage(building);
			damaged.merge(building, 1, Integer::sum);
		}
		return damaged;
	}

	private static String lost(Map<String, Integer> units) {
		return units.isEmpty() ? "no Units" : listed(units);
	}

	/**
	 * Rolls every Event, in the order listed, for each Nation still in the
	 * Game in order (for a Scout Event, only one with Scouts) or each of its
	 * travelling Warparties in the order sent, that the Event's conditions
	 * hold for, drawing once from the seeded random source for each. One that
	 * happens takes effect at once, as luck varies it (see
	 * {@link Event.Effects#varied}), and is announced in the Turn Report of
	 * the Nation it happened to, or that sent the Warparty. An Event
	 * whose effects need what the Nation or Warparty does not have (see
	 * {@link #canBefall}) is not rolled for it.
	 * <p>
	 * A Dilemma that happens is not faced at once: once every Event is rolled,
	 * each Nation still in the Game in order faces at most one of the Dilemmas
	 * that happened for it. It is picked from those the Nation has never
	 * faced, or from all of them if it has faced them all, by one more draw
	 * from the seeded random source, made only when there are several to pick
	 * from.
	 */
	private void rollEvents(Map<String, TurnReport.Draft> reports, List<Witnessed> witnessed) {
		Map<Nation, List<Event>> happened = new LinkedHashMap<>();
		for (Event event : ruleset.events()) {
			for (Nation nation : remaining()) {
				TurnReport.Draft report = reports.get(nation.name());
				switch (event.subject()) {
					case NATION, SCOUTS -> {
						if ((event.subject() == Event.Subject.NATION || nation.scouts() > 0)
								&& event.when().holdForNation(turn, nation, nation.defense(ruleset), largestApproaching(nation))
								&& canBefall(event.effects(), nation) && happens(event)) {
							if (event.dilemma()) {
								happened.computeIfAbsent(nation, ignored -> new ArrayList<>()).add(event);
							}
							else {
								report.add(EVENTS,
										announced(event.text(), befall(event.name(), event.effects(), nation, reports, witnessed)));
							}
						}
					}
					case WARPARTY -> {
						for (Warparty warparty : nation.warparties()) {
							if (!warparty.awaitingBattle() && event.when().holdForWarparty(turn, warparty)
									&& canBefall(event.effects(), warparty) && happens(event)) {
								report.add(EVENTS, announced(event.text(), befall(event.effects(), nation, warparty)));
							}
						}
					}
				}
			}
		}
		for (Nation nation : remaining()) {
			List<Event> dilemmas = happened.getOrDefault(nation, List.of());
			if (dilemmas.isEmpty()) {
				continue;
			}
			List<Event> unfaced = dilemmas.stream().filter(dilemma -> !nation.hasFaced(dilemma)).toList();
			List<Event> pickable = unfaced.isEmpty() ? dilemmas : unfaced;
			Event faced = pickable.size() == 1 ? pickable.getFirst()
					: pickable.get((int) (random.nextDouble() * pickable.size()));
			nation.faceDilemma(faced);
			reports.get(nation.name())
				.add(DECISIONS_AND_ALERTS, faced.text() + "; choose what to do before this Turn ends.");
		}
	}

	/**
	 * Has each Nation still in the Game in order, if it has set Scouts to
	 * Watch for Warparties, watch for every other Nation's Warparty abroad,
	 * taking the Nations in order and each one's Warparties in the order sent,
	 * drawing once from the seeded random source for each. Each Watch Task
	 * gives an equal chance, of its own, of discovering a Warparty. One discovered is announced in the Turn
	 * Report; a Warparty that has arrived at the Nation watching was
	 * announced already, and is not looked for.
	 */
	private void watchForWarparties(Map<String, TurnReport.Draft> reports) {
		for (Nation scouting : remaining()) {
			int watching = scouting.watchTasks();
			if (watching == 0) {
				continue;
			}
			double chance = 1 - Math.pow(1 - DISCOVERY_CHANCE_PER_WATCH, watching);
			for (Nation other : remaining()) {
				if (other == scouting) {
					continue;
				}
				for (Warparty warparty : other.warparties()) {
					if (warparty.awaitingBattle() && warparty.target().equals(scouting.name())) {
						continue;
					}
					if (random.nextDouble() < chance) {
						reports.get(scouting.name())
							.add(SPIES_AND_SCOUTS, "Your Scouts discovered " + other.ruled() + "'s Warparty of " + listed(warparty.units())
									+ " " + whereabouts(warparty, scouting.name()) + ".");
					}
				}
			}
			for (Camp camp : camps) {
				for (Warparty warparty : camp.warparties()) {
					if (warparty.awaitingBattle() && warparty.target().equals(scouting.name())) {
						continue;
					}
					if (random.nextDouble() < chance) {
						String whose = scouting.hasHeardOf(camp.name()) ? camp.ruled() + "'s" : "a Barbarian Camp's";
						reports.get(scouting.name())
							.add(SPIES_AND_SCOUTS, "Your Scouts discovered " + whose + " Warparty of "
									+ listed(warparty.units()) + " " + whereabouts(warparty, scouting.name()) + ".");
					}
				}
			}
		}
	}

	/**
	 * Has each Nation still in the Game in order search, with each of its
	 * Locate Tasks in the order set, for the Nation or Barbarian Camp the Task
	 * is about, or, for one searching the wilds, for any Camp still standing
	 * it has not Located, drawing once from the seeded random source for each.
	 * Each Scout on the Task gives an equal chance, of its own, of Locating
	 * it. A Task searching the wilds while there is no such Camp, or about a
	 * Camp already Located this Turn, draws nothing; one that finds several
	 * draws once more for which it Locates, each as likely as the others. A
	 * Task that succeeds ends, its Scouts going Idle (see
	 * {@link Nation#locate}), and is announced in the Turn Report, along with
	 * what Locating a Camp showed. The search comes first as a Turn resolves,
	 * before anything else draws.
	 */
	private void searchForNations(Map<String, TurnReport.Draft> reports) {
		for (Nation searching : remaining()) {
			for (Task task : searching.tasks()) {
				if (task.kind() != Task.Kind.LOCATE) {
					continue;
				}
				List<Camp> sought = task.target() == null
						? camps.stream()
							.filter(camp -> !camp.destroyed() && searching.campLocated(camp.name()) == null)
							.toList()
						: camp(task.target()).filter(camp -> searching.campLocated(camp.name()) == null)
							.map(List::of)
							.orElse(List.of());
				if (sought.isEmpty() && (task.target() == null || camp(task.target()).isPresent())) {
					continue;
				}
				double chance = 1 - Math.pow(1 - LOCATE_CHANCE_PER_SCOUT, task.scouts());
				if (random.nextDouble() >= chance) {
					continue;
				}
				if (sought.isEmpty()) {
					searching.locate(task.target());
					reports.get(searching.name())
						.add(SPIES_AND_SCOUTS, "Your Scouts Located " + task.target()
								+ ", so you can now send Spies and Warparties there.");
					continue;
				}
				Camp found = sought.size() == 1 ? sought.getFirst()
						: sought.get((int) (random.nextDouble() * sought.size()));
				CampFacts facts = found.facts(ruleset.barbarians(), turn);
				searching.locateCamp(found.name(), facts, task);
				reports.get(searching.name())
					.add(SPIES_AND_SCOUTS, "Your Scouts Located " + found.name()
							+ ", so you can now send Warparties there: " + campFact(facts) + ".");
			}
		}
	}

	/**
	 * Such as "its Army is Warrior ×3, its Defense is 4, and it raids Rome and
	 * Carthage".
	 */
	private static String campFact(CampFacts facts) {
		return (facts.army().isEmpty() ? "it has no Army" : "its Army is " + listed(facts.army())) + ", its Defense is "
				+ facts.defense() + ", and "
				+ (facts.targets().isEmpty() ? "it raids no one" : "it raids " + joined(facts.targets()));
	}

	/**
	 * Such as "Your Scouts searching for Egypt are Idle again, as Egypt was
	 * conquered", for a Task about a Nation that left the Game.
	 */
	private static String taskEnded(Task task, String reason) {
		return "Your Scouts searching for " + task.target() + " are Idle again, as " + reason + ".";
	}

	/**
	 * Has every pair of Warparties from different Nations travelling abroad,
	 * outward or homeward, draw once from the seeded random source for whether
	 * they meet, taking each Nation still in the Game in order and each of its
	 * Warparties in the order sent, against each later Nation's Warparties in
	 * the order sent; then each Nation's against each Barbarian Camp's in
	 * order, Camps' Warparties never meeting each other. A Warparty that has
	 * met one already this Turn meets no other, and a pair with such a
	 * Warparty draws nothing. Both Players are told in their Turn Report, and
	 * choose during the next Turn whether to fight; a Camp chooses at once to
	 * fight if its Warparty's Strength is at least the other's, and the Nation
	 * hears of it.
	 */
	private void meet(Map<String, TurnReport.Draft> reports) {
		List<Nation> left = remaining();
		Set<Warparty> met = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int i = 0; i < left.size(); i++) {
			Nation nation = left.get(i);
			for (Warparty warparty : nation.warparties()) {
				for (Nation other : left.subList(i + 1, left.size())) {
					for (Warparty otherWarparty : other.warparties()) {
						if (!meets(warparty, otherWarparty, met)) {
							continue;
						}
						encounters.add(new Encounter(
								new Encounter.Side(nation.name(), warparty.id(), warparty.units(), Encounter.Choice.NONE),
								new Encounter.Side(other.name(), otherWarparty.id(), otherWarparty.units(),
										Encounter.Choice.NONE)));
						reports.get(nation.name()).add(WARFARE, metLine(warparty, other.ruled(), otherWarparty));
						reports.get(other.name()).add(WARFARE, metLine(otherWarparty, nation.ruled(), warparty));
					}
				}
			}
		}
		for (Nation nation : left) {
			for (Warparty warparty : nation.warparties()) {
				for (Camp camp : camps) {
					for (Warparty theirs : camp.warparties()) {
						if (!meets(warparty, theirs, met)) {
							continue;
						}
						Encounter.Choice choice = theirs.strength() >= warparty.strength() ? Encounter.Choice.FIGHT
								: Encounter.Choice.PASS;
						encounters.add(new Encounter(
								new Encounter.Side(nation.name(), warparty.id(), warparty.units(), Encounter.Choice.NONE),
								new Encounter.Side(camp.name(), theirs.id(), theirs.units(), choice)));
						nation.hearOf(camp.name());
						reports.get(nation.name()).add(WARFARE, metLine(warparty, camp.ruled(), theirs));
					}
				}
			}
		}
	}

	/**
	 * Has each Barbarian Camp in order grow (see {@link Camp#grow}) and then
	 * raid (see {@link Camp#raid}), drawing from the seeded random source as
	 * they say; no Nation is told until a Camp's Warparty arrives. Then a new
	 * Camp may appear (see {@link #appear}).
	 */
	private void barbarians() {
		List<String> inGame = remaining().stream().map(Nation::name).toList();
		for (Camp camp : camps) {
			camp.grow(turn, ruleset, random);
			camp.raid(inGame, ruleset, random);
		}
		appear(inGame);
	}

	/**
	 * Has a Barbarian Camp appear in the wilds, by chance, if the ruleset has
	 * any and one may: none before Turn {@value #FIRST_CAMP_TURN}, none for
	 * {@value #TURNS_BETWEEN_CAMPS} Turns after the last, never more standing
	 * at once than one fewer than the Nations still in the Game, and each
	 * named for a place no other Camp has been. If one may, one draw from the
	 * seeded random source says whether it does, {@value #CAMP_CHANCE} likely;
	 * then one each picks its place and its Ruler, one how many Targets it
	 * has, from one to every Nation still in the Game, one each picks them,
	 * one how many Units it has, from {@value Camp#FEWEST_UNITS} to
	 * {@value Camp#MOST_UNITS}, one each picks their fighting Unit types, and,
	 * if the ruleset has barbarian Buildings, one how many it has, up to
	 * {@value Camp#MOST_BUILDINGS}, and one each picks them; every pick is
	 * among those left, each as likely as the others. No Nation is told.
	 *
	 * @param inGame the Nations still in the Game, in order
	 */
	private void appear(List<String> inGame) {
		Barbarians barbarians = ruleset.barbarians();
		List<String> places = barbarians.places()
			.stream()
			.filter(place -> camps.stream().noneMatch(camp -> camp.name().equals(place + Camp.NAMED)))
			.toList();
		long standing = camps.stream().filter(camp -> !camp.destroyed()).count();
		if (places.isEmpty() || turn < FIRST_CAMP_TURN || lastCampOn > 0 && turn - lastCampOn < TURNS_BETWEEN_CAMPS
				|| standing >= inGame.size() - 1 || random.nextDouble() >= CAMP_CHANCE) {
			return;
		}
		String place = picked(places);
		String ruler = picked(barbarians.rulers());
		List<String> unpicked = new ArrayList<>(inGame);
		int targetCount = 1 + (int) (random.nextDouble() * unpicked.size());
		List<String> picked = new ArrayList<>();
		for (int i = 0; i < targetCount; i++) {
			picked.add(unpicked.remove((int) (random.nextDouble() * unpicked.size())));
		}
		List<String> targets = inGame.stream().filter(picked::contains).toList();
		List<UnitType> fighting = Camp.fighting(ruleset);
		int units = Camp.FEWEST_UNITS + (int) (random.nextDouble() * (Camp.MOST_UNITS - Camp.FEWEST_UNITS + 1));
		Map<String, Integer> drawn = new LinkedHashMap<>();
		for (int i = 0; i < units; i++) {
			drawn.merge(picked(fighting).name(), 1, Integer::sum);
		}
		Map<String, Integer> army = new LinkedHashMap<>();
		fighting.stream().filter(unit -> drawn.containsKey(unit.name())).forEach(unit -> army.put(unit.name(), drawn.get(unit.name())));
		Map<String, Integer> buildings = new LinkedHashMap<>();
		if (!barbarians.buildings().isEmpty()) {
			int count = (int) (random.nextDouble() * (Camp.MOST_BUILDINGS + 1));
			Map<String, Integer> built = new LinkedHashMap<>();
			for (int i = 0; i < count; i++) {
				built.merge(picked(barbarians.buildings()).name(), 1, Integer::sum);
			}
			barbarians.buildings()
				.stream()
				.filter(building -> built.containsKey(building.name()))
				.forEach(building -> buildings.put(building.name(), built.get(building.name())));
		}
		camps.add(new Camp(place, ruler, targets, army, buildings));
		lastCampOn = turn;
	}

	/**
	 * One of the given items, picked by one draw from the seeded random
	 * source, each as likely as the others.
	 */
	private <T> T picked(List<T> items) {
		return items.get((int) (random.nextDouble() * items.size()));
	}

	/**
	 * Whether two Warparties abroad meet: neither waits for a Battle nor has
	 * met another this Turn, and one draw from the seeded random source says
	 * they do. One that meets is marked as having met.
	 */
	private boolean meets(Warparty warparty, Warparty other, Set<Warparty> met) {
		if (warparty.awaitingBattle() || other.awaitingBattle() || met.contains(warparty) || met.contains(other)
				|| random.nextDouble() >= ENCOUNTER_CHANCE) {
			return false;
		}
		met.add(warparty);
		met.add(other);
		return true;
	}

	/**
	 * Such as "Your Warparty of Warrior ×2 heading to Carthage met Cleopatra of
	 * Egypt's Warparty of Archer ×3; choose whether to fight before this Turn
	 * ends."
	 *
	 * @param other the other Nation or Barbarian Camp, named by its Ruler
	 */
	private static String metLine(Warparty own, String other, Warparty theirs) {
		return yourWarparty(own) + " met " + other
				+ "'s Warparty of " + listed(theirs.units()) + "; choose whether to fight before this Turn ends.";
	}

	/**
	 * Settles each of last Turn's Encounters in the order met: if either side
	 * chose to fight, the Warparties fight by a Battle's rules, the first as
	 * the attacking side, and each loses Units with no delay to its travel;
	 * one wiped out is destroyed, and the other, if any of it survived, takes
	 * the Plunder it carried (see {@link #takePlunder}). Otherwise they go
	 * their separate ways. Both Players are told in their Turn Report. A
	 * Nation that chose to fight a Barbarian Camp's Warparty becomes one of
	 * the Camp's Targets.
	 *
	 * @return the Plunder taken, to take once this Turn's Hauls are settled,
	 *         so that it is decided on and arrives as a Haul seized this Turn
	 *         would
	 */
	private List<Runnable> settleEncounters(Map<String, TurnReport.Draft> reports) {
		List<Runnable> plunderTaken = new ArrayList<>();
		for (Encounter encounter : encounters) {
			Sender first = sender(encounter.first().nation());
			Sender second = sender(encounter.second().nation());
			Optional<Warparty> firstWarparty = first.warparty(encounter.first().warparty());
			Optional<Warparty> secondWarparty = second.warparty(encounter.second().warparty());
			if (firstWarparty.isEmpty() || secondWarparty.isEmpty()) {
				continue;
			}
			if (second instanceof Camp camp && encounter.first().choice() == Encounter.Choice.FIGHT) {
				camp.target(first.name());
			}
			Warparty firstBefore = firstWarparty.get();
			Warparty secondBefore = secondWarparty.get();
			if (!encounter.fought()) {
				reportTo(first, reports).ifPresent(report -> report.add(WARFARE,
						separateWays(firstBefore, second.ruled(), secondBefore)));
				reportTo(second, reports).ifPresent(report -> report.add(WARFARE,
						separateWays(secondBefore, first.ruled(), firstBefore)));
				continue;
			}
			Battle battle = Battle.fight(List.of(firstBefore.units()), secondBefore.units(), ruleset, random);
			Map<String, Integer> firstLost = battle.warpartiesLost().getFirst();
			Map<String, Integer> secondLost = battle.armyLost();
			first.foughtEncounter(firstBefore, battle.warpartySurvivors(0), ruleset);
			second.foughtEncounter(secondBefore, battle.armySurvivors(), ruleset);
			reportEncounter(encounter.first(), encounter.second(), firstBefore, secondBefore, firstLost, secondLost,
					reports);
			reportEncounter(encounter.second(), encounter.first(), secondBefore, firstBefore, secondLost, firstLost,
					reports);
			boolean firstWipedOut = battle.warpartySurvivors(0).isEmpty();
			boolean secondWipedOut = battle.armySurvivors().isEmpty();
			if (firstWipedOut) {
				reportDestroyed(firstBefore, first, second, reports);
			}
			if (secondWipedOut) {
				reportDestroyed(secondBefore, second, first, reports);
			}
			if (firstWipedOut && !secondWipedOut) {
				plunderTaken.add(() -> takePlunder(second, firstBefore, first, reports));
			}
			if (secondWipedOut && !firstWipedOut) {
				plunderTaken.add(() -> takePlunder(first, secondBefore, second, reports));
			}
		}
		encounters.clear();
		return plunderTaken;
	}

	/**
	 * The Turn Report a Nation is to be told in, or none for a Barbarian
	 * Camp.
	 */
	private static Optional<TurnReport.Draft> reportTo(Sender sender, Map<String, TurnReport.Draft> reports) {
		return sender.camp() ? Optional.empty() : Optional.of(reports.get(sender.name()));
	}

	/**
	 * Has a Warparty that wiped out another in an Encounter take the Plunder
	 * that one carried. A Barbarian Camp's carries it off for good. A
	 * Nation's sends what was plundered from its own Nation back home, and
	 * keeps what was plundered from a Camp or a Nation no longer in the Game;
	 * of what was plundered from a third Nation, its Player chooses by the end
	 * of the next Turn whether to keep it or send it back (see
	 * {@link #settleHauls}). Each resource goes as a Haul of its own, Captives
	 * as Population, arriving in {@value #HAUL_DELIVERY_TURNS} Turns.
	 *
	 * @param lost the Warparty wiped out, as it was before the Encounter
	 * @param loser the Nation or Camp that sent it
	 */
	private void takePlunder(Sender winner, Warparty lost, Sender loser, Map<String, TurnReport.Draft> reports) {
		Plunder plunder = lost.plunder();
		if (plunder.isNone() || !(winner instanceof Nation taking)) {
			return;
		}
		String from = lost.target();
		TurnReport.Draft report = reports.get(taking.name());
		List<Haul> hauls = new ArrayList<>();
		if (plunder.gold() > 0) {
			hauls.add(new Haul(GOLD, plunder.gold()));
		}
		if (plunder.research() > 0) {
			hauls.add(new Haul(RESEARCH, plunder.research()));
		}
		if (plunder.captives() > 0) {
			hauls.add(new Haul(POPULATION, plunder.captives()));
		}
		Line taken = Line.joined(hauls.stream().map(haul -> Line.of(neutral(haul.resource(), haul.amount()))).toList());
		String took = "Your Warparty took " + loser.name() + "'s Plunder of ";
		if (from.equals(taking.name())) {
			hauls.forEach(haul -> deliveries.add(new Delivery(taking.name(), taking.name(), from, haul, HAUL_DELIVERY_TURNS)));
			report.add(WARFARE, Line.of(took, taken, ", taken from your Nation; it will be back in " + HAUL_DELIVERY_TURNS
					+ " Turns."));
			return;
		}
		if (camp(from).isPresent() || !nation(from).inGame()) {
			hauls.forEach(haul -> deliveries.add(new Delivery(taking.name(), taking.name(), from, haul, HAUL_DELIVERY_TURNS)));
			report.add(WARFARE, Line.of(took, taken, ", taken from " + from + "; it will arrive in " + HAUL_DELIVERY_TURNS
					+ " Turns."));
			return;
		}
		hauls.forEach(haul -> taking.faceHaul(from, haul, true));
		report.add(DECISIONS_AND_ALERTS, Line.of(took, taken, ", taken from " + from
				+ "; choose whether to keep it or send it back before this Turn ends."));
	}

	/**
	 * Such as "Your Warparty of Warrior ×2 heading to Carthage fought Cleopatra
	 * of Egypt's Warparty of Archer ×3, as Egypt chose to fight. You lost
	 * Warrior ×1 and Egypt lost no Units.", for a Nation; a Barbarian Camp is
	 * told nothing.
	 */
	private void reportEncounter(Encounter.Side own, Encounter.Side other, Warparty ownWarparty,
			Warparty otherWarparty, Map<String, Integer> ownLost, Map<String, Integer> otherLost,
			Map<String, TurnReport.Draft> reports) {
		if (camp(own.nation()).isPresent()) {
			return;
		}
		String why = own.choice() == Encounter.Choice.FIGHT
				? other.choice() == Encounter.Choice.FIGHT ? "you both chose" : "you chose"
				: other.nation() + " chose";
		reports.get(own.nation())
			.add(WARFARE, yourWarparty(ownWarparty) + " fought "
					+ ruled(other.nation()) + "'s Warparty of " + listed(otherWarparty.units()) + ", as " + why
					+ " to fight. You lost " + lost(ownLost) + " and " + other.nation() + " lost " + lost(otherLost)
					+ ".");
	}

	/**
	 * Such as "Your Warparty of Warrior ×2 heading to Carthage and Cleopatra of
	 * Egypt's Warparty of Archer ×3 went their separate ways, as neither of you
	 * chose to fight."
	 *
	 * @param other the other Nation or Barbarian Camp, named by its Ruler
	 */
	private static String separateWays(Warparty own, String other, Warparty theirs) {
		return yourWarparty(own) + " and " + other
				+ "'s Warparty of " + listed(theirs.units())
				+ " went their separate ways, as neither of you chose to fight.";
	}

	/**
	 * Tells the Nations on both sides that a Warparty was wiped out fighting
	 * in an Encounter.
	 */
	private void reportDestroyed(Warparty destroyed, Sender sender, Sender other,
			Map<String, TurnReport.Draft> reports) {
		reportTo(sender, reports)
			.ifPresent(report -> report.add(WARFARE, "Your Warparty " + whereabouts(destroyed, null) + " was destroyed."));
		reportTo(other, reports).ifPresent(report -> report.add(WARFARE, sender.ruled() + "'s Warparty was destroyed."));
	}

	/**
	 * Such as "Your Warparty of Warrior ×2 heading to Carthage", for the
	 * Nation that sent it.
	 */
	private static String yourWarparty(Warparty warparty) {
		return "Your Warparty of " + listed(warparty.units()) + " " + whereabouts(warparty, null);
	}

	/**
	 * Has each Nation still in the Game in order move each of its Spies on a
	 * Mission, in the order sent, one Turn further into it. A Spy whose
	 * Mission ends goes Idle, and the Player is alerted, unless it succeeded
	 * at a Steal Mission: it is then Escaping instead, its sender is told so,
	 * and nothing more is drawn for it until the next Turn (see
	 * {@link #takeHauls} and {@link #escape}). Idle Spies do nothing.
	 * <p>
	 * An Intel Mission that ends reports its Fact, as it stands once the Turn
	 * has resolved, in the Turn Report and on the Map, tagged with this Turn,
	 * drawing no luck. A hostile Mission that ends draws once from the seeded
	 * random source for whether it succeeds; one that succeeds harms its
	 * target (see {@link #sabotage}), whose Turn Report says what it harmed
	 * without naming the sender. It then draws once more for whether it
	 * Unmasks its Spy, {@value #UNMASK_CHANCE_ON_SUCCESS} likely if it
	 * succeeded and {@value #UNMASK_CHANCE_ON_FAILURE} if it failed, and an
	 * Unmasked Spy draws once more for whether it is caught on the spot (see
	 * {@link #caught}), its sender told nothing but what is witnessed, or
	 * flees home, both sides told and its target told who sent it, without
	 * Locating the sender. Otherwise its sender is told whether it succeeded.
	 */
	private void workOnMissions(Map<String, TurnReport.Draft> reports, List<Witnessed> witnessed) {
		for (Nation spying : remaining()) {
			for (Spy spy : spying.workOnMissions()) {
				TurnReport.Draft report = reports.get(spying.name());
				Mission mission = ruleset.mission(spy.mission()).orElseThrow();
				if (mission.hostile()) {
					Nation target = nation(spy.target());
					boolean succeeded = random.nextDouble() < mission.chance();
					if (succeeded && mission.steals() != null) {
						spying.changeSpy(spy, succeeding -> succeeding.escaping(mission));
						report.add(SPIES_AND_SCOUTS, "Your Spy in " + spy.target() + " succeeded at " + mission.name()
								+ " and is Escaping; it will take its Haul from what " + spy.target() + " gains next Turn.");
						continue;
					}
					if (succeeded) {
						List<Line> harmed = sabotage(mission, target, reports);
						if (!harmed.isEmpty()) {
							reports.get(spy.target()).add(SPIES_AND_SCOUTS, announced(mission.text(), harmed));
						}
					}
					boolean unmasked = random.nextDouble() < (succeeded ? UNMASK_CHANCE_ON_SUCCESS : UNMASK_CHANCE_ON_FAILURE);
					if (unmasked && random.nextDouble() < CAUGHT_WHEN_UNMASKED_CHANCE) {
						caught(spy, spying, target, "Your people unmasked and", reports, witnessed);
						continue;
					}
					report.add(SPIES_AND_SCOUTS, "Your Spy in " + spy.target() + (succeeded ? " succeeded" : " failed")
							+ " at " + mission.name() + ".");
					if (unmasked) {
						spying.fleeHome(spy);
						report.add(SPIES_AND_SCOUTS, "Your Spy in " + spy.target() + " was Unmasked and is fleeing home.");
						reports.get(spy.target()).add(SPIES_AND_SCOUTS,
								"A Spy was Unmasked in your Nation and fled home; " + spying.ruled() + " sent it.");
						continue;
					}
				}
				else {
					report.add(SPIES_AND_SCOUTS, Line.of("Your Spy in " + spy.target() + " reported that ",
							learnFact(spying, spy.target(), mission.reports()), "."));
				}
				report.add(DECISIONS_AND_ALERTS,
						"Your Spy in " + spy.target() + " finished " + mission.name() + " and is Idle; set it a new Mission.");
			}
		}
	}

	/**
	 * Has each Nation still in the Game in order have its Escaping Spies, in
	 * the order sent, take their Haul from what the Nation each is at gained
	 * as it earned and paid upkeep this Turn, never from what it held before:
	 * each draws once from the seeded random source for a share of
	 * {@value #MIN_HAUL_SHARE} to all of what is left of that gain, at least
	 * 1, which the robbed Nation loses and whose Turn Report says what was
	 * taken without naming the sender. A Spy whose target gained none of what
	 * it steals, or none is left, takes nothing and draws nothing.
	 *
	 * @param gains how much Gold and Research each Nation gained as it earned
	 *        and paid upkeep this Turn; what is taken is taken from it
	 */
	private void takeHauls(Map<String, Nation.Change> gains, Map<String, TurnReport.Draft> reports) {
		for (Nation spying : remaining()) {
			for (Spy spy : spying.spiesEscaping()) {
				Mission mission = ruleset.mission(spy.mission()).orElseThrow();
				Resource resource = mission.steals();
				Nation.Change gain = gains.get(spy.target());
				int gained = resource == GOLD ? gain.gold() : gain.research();
				if (gained <= 0) {
					continue;
				}
				double share = MIN_HAUL_SHARE + (1 - MIN_HAUL_SHARE) * random.nextDouble();
				int taken = Math.min(gained, Math.max(1, (int) Math.round(gained * share)));
				int gold = resource == GOLD ? taken : 0;
				int research = resource == RESEARCH ? taken : 0;
				nation(spy.target()).gain(-gold, 0, -research);
				gains.put(spy.target(), new Nation.Change(gain.gold() - gold, 0, gain.research() - research));
				spying.changeSpy(spy, escaping -> escaping.took(new Haul(resource, taken)));
				reports.get(spy.target())
					.add(SPIES_AND_SCOUTS, announced(mission.text(),
							List.of(Line.of(loss(resource, taken), " was taken from what your Nation gained"))));
			}
		}
	}

	/**
	 * Has each Nation still in the Game in order have its Escaping Spies, in
	 * the order sent, stop Escaping. One that took nothing is back in place
	 * and Idle, drawing nothing. One with a Haul draws once from the seeded
	 * random source for whether it is Unmasked, {@value #UNMASK_CHANCE_ON_SUCCESS} likely, and an Unmasked Spy
	 * draws once more for whether it is caught on the spot (see
	 * {@link #caught}), its Haul going straight back to the Nation it robbed,
	 * or flees home carrying its Haul, both sides told and its target told who
	 * sent it, without Locating the sender, or otherwise sets off home
	 * carrying it. Its sender is told what became of it.
	 */
	private void escape(Map<String, TurnReport.Draft> reports, List<Witnessed> witnessed) {
		for (Nation spying : remaining()) {
			for (Spy spy : spying.spiesEscaping()) {
				TurnReport.Draft report = reports.get(spying.name());
				Nation target = nation(spy.target());
				Haul haul = spy.haul();
				if (haul == null) {
					spying.changeSpy(spy, Spy::idle);
					report.add(SPIES_AND_SCOUTS, "Your Spy in " + spy.target() + " found nothing to steal there.");
					report.add(DECISIONS_AND_ALERTS, "Your Spy in " + spy.target() + " is Idle; set it a new Mission.");
					continue;
				}
				Line stolen = Line.of(gain(haul.resource(), haul.amount()));
				boolean unmasked = random.nextDouble() < UNMASK_CHANCE_ON_SUCCESS;
				if (unmasked && random.nextDouble() < CAUGHT_WHEN_UNMASKED_CHANCE) {
					caught(spy, spying, target, "Your people unmasked and", reports, witnessed);
					target.gain(haul);
					reports.get(target.name()).add(SPIES_AND_SCOUTS, Line.of("You took back the ", stolen, " it stole."));
					continue;
				}
				if (unmasked) {
					spying.fleeHome(spy);
					report.add(SPIES_AND_SCOUTS,
							Line.of("Your Spy in " + spy.target() + " stole ", stolen, ", was Unmasked and is fleeing home with it."));
					reports.get(spy.target())
						.add(SPIES_AND_SCOUTS, "A Spy was Unmasked in your Nation and fled home; " + spying.ruled() + " sent it.");
					continue;
				}
				spying.changeSpy(spy, Spy::carryingHaulHome);
				report.add(SPIES_AND_SCOUTS, Line.of("Your Spy in " + spy.target() + " stole ", stolen, " and is heading home with it."));
			}
		}
	}

	/**
	 * Applies a hostile Mission's harm to the Nation its Spy is at: its
	 * Population loss, Defense wear and Building damage as an Event's effects
	 * would be, varied by luck and drawing as {@link #befall} does, then its
	 * Army losses, drawing once from the seeded random source to vary how many
	 * Units die and once more for each one, picked from those left with each
	 * Unit as likely as any other.
	 *
	 * @return what it harmed, such as "your Farm ×1 was damaged"
	 */
	private List<Line> sabotage(Mission mission, Nation target, Map<String, TurnReport.Draft> reports) {
		Mission.Harm harm = mission.harm();
		Event.Effects effects = new Event.Effects(0, -harm.populationLost(), 0, harm.damageDefense(), 0, null, 0, null, 0,
				null, harm.damageBuildings());
		// It catches no Spy, so nothing is witnessed.
		List<Line> harmed = new ArrayList<>(befall(mission.name(), effects, target, reports, new ArrayList<>()));
		int units = target.army().values().stream().mapToInt(Integer::intValue).sum();
		if (harm.armyKilled() > 0 && units > 0) {
			int dying = Math.min(units,
					Event.Effects.varied(Math.max(1, (int) Math.round(units * harm.armyKilled())), random));
			Map<String, Integer> died = new LinkedHashMap<>();
			for (int left = dying; left > 0; left--) {
				int pick = (int) (random.nextDouble() * (units - dying + left));
				for (Map.Entry<String, Integer> unit : target.army().entrySet()) {
					if (pick < unit.getValue()) {
						target.changeArmy(Map.of(unit.getKey(), -1));
						died.merge(unit.getKey(), 1, Integer::sum);
						break;
					}
					pick -= unit.getValue();
				}
			}
			harmed.add(Line.of(countedEach(died) + " died"));
		}
		return harmed;
	}

	/**
	 * Has a Nation learn one kind of Fact of another Nation as it stands now,
	 * tagged with this Turn.
	 *
	 * @return what was learned, such as "Egypt has 50 Gold"
	 */
	private Line learnFact(Nation spying, String name, Mission.FactKind kind) {
		Nation target = nation(name);
		Facts facts = spying.learnedOf(name);
		Line reported = switch (kind) {
			case GOLD -> {
				facts = facts.withGold(target.gold(), turn);
				yield Line.of(name + " has ", neutral(GOLD, target.gold()));
			}
			case POPULATION -> {
				facts = facts.withPopulation(target.population(), turn);
				yield Line.of(name + " has ", neutral(POPULATION, target.population()));
			}
			case RESEARCH -> {
				facts = facts.withResearch(target.research(), turn);
				yield Line.of(name + " has ", neutral(RESEARCH, target.research()));
			}
			case ARMY -> {
				facts = facts.withArmy(target.army(), turn);
				yield Line.of(armyFact(target));
			}
			case BUILDINGS -> {
				facts = facts.withBuildings(target.buildings(), target.damagedBuildings(), target.defense(ruleset), turn);
				yield Line.of(buildingsFact(target));
			}
		};
		spying.learn(name, facts);
		return reported;
	}

	/**
	 * Such as "Egypt's Army is Warrior ×2" or "Egypt has no Army".
	 */
	private static String armyFact(Nation nation) {
		return nation.army().isEmpty() ? nation.name() + " has no Army"
				: nation.name() + "'s Army is " + listed(nation.army());
	}

	/**
	 * Such as "Egypt has built Walls ×1, and its Defense is 10" or "Egypt has
	 * built Market ×2, with Market ×1 damaged, and its Defense is 0".
	 */
	private String buildingsFact(Nation nation) {
		Map<String, Integer> damaged = nation.damagedBuildings();
		return nation.name() + " has built "
				+ (nation.buildings().isEmpty() ? "nothing" : listed(nation.buildings()))
				+ (damaged.isEmpty() ? "" : ", with " + listed(damaged) + " damaged") + ", and its Defense is "
				+ nation.defense(ruleset);
	}

	/**
	 * Has each Nation still in the Game in order try to catch other Nations'
	 * Spies: with its Spies at home, those in place at it, each Spy on a
	 * hostile Mission {@value #HOSTILE_CATCH_MULTIPLIER} times as likely to be
	 * caught as any other; then with its Scouts Hunting Travelling Spies, and
	 * then with each of its travelling Warparties in the order sent, those
	 * still travelling anywhere. No one tries to catch an Escaping Spy. Each
	 * try takes the other Nations in order and each one's Spies in the order
	 * sent, drawing once from the seeded random source for each Spy not yet
	 * caught; a Spy caught draws once more for what becomes of it. See
	 * {@link #caught}. The Haul a travelling Spy caught carried is seized; see
	 * {@link #seize}.
	 */
	private void catchSpies(Map<String, TurnReport.Draft> reports, List<Witnessed> witnessed) {
		for (Nation catching : remaining()) {
			int spies = catching.spiesAtHome(ruleset);
			if (spies > 0) {
				for (Nation other : others(catching)) {
					for (Spy spy : other.spiesInPlace()) {
						if (!spy.target().equals(catching.name())) {
							continue;
						}
						if (spy.escaping()) {
							continue;
						}
						double perSpy = onHostileMission(spy) ? HOSTILE_CATCH_MULTIPLIER * CATCH_CHANCE_PER_SPY_AT_HOME
								: CATCH_CHANCE_PER_SPY_AT_HOME;
						if (random.nextDouble() < 1 - Math.pow(1 - perSpy, spies)) {
							caught(spy, other, catching, "Your Spies", reports, witnessed);
						}
					}
				}
			}
			int hunting = catching.huntingScouts();
			if (hunting > 0) {
				double chance = 1 - Math.pow(1 - CATCH_CHANCE_PER_SCOUT, hunting);
				for (Nation other : others(catching)) {
					for (Spy spy : other.spiesTravelling()) {
						if (random.nextDouble() < chance) {
							caught(spy, other, catching, "Your Scouts", reports, witnessed);
							seize(spy, catching, "your Scouts", reports);
						}
					}
				}
			}
			for (Warparty warparty : catching.warparties()) {
				if (warparty.awaitingBattle()) {
					continue;
				}
				for (Nation other : others(catching)) {
					for (Spy spy : other.spiesTravelling()) {
						if (random.nextDouble() < CATCH_CHANCE_PER_WARPARTY) {
							caught(spy, other, catching, "Your Warparty of " + listed(warparty.units()) + " "
									+ whereabouts(warparty, catching.name()), reports, witnessed);
							seize(spy, catching, "your Warparty", reports);
						}
					}
				}
			}
		}
	}

	/**
	 * Seizes the Haul, if any, a Spy caught travelling carried. If the Nation
	 * that caught it is the one it was stolen from, the Haul is on its way back
	 * there; otherwise that Nation decides, by the end of the next Turn,
	 * whether to keep it or send it back.
	 *
	 * @param caughtBy what caught it, such as "your Scouts"
	 */
	private void seize(Spy spy, Nation catching, String caughtBy, Map<String, TurnReport.Draft> reports) {
		Haul haul = spy.haul();
		if (haul == null) {
			return;
		}
		Line carried = Line.of("The Spy " + caughtBy + " caught carried ", neutral(haul.resource(), haul.amount()));
		if (spy.target().equals(catching.name())) {
			deliveries.add(new Delivery(catching.name(), catching.name(), spy.target(), haul, HAUL_DELIVERY_TURNS));
			reports.get(catching.name())
				.add(SPIES_AND_SCOUTS, Line.of(carried, " stolen from your Nation; it will be back in "
						+ HAUL_DELIVERY_TURNS + " Turns."));
			return;
		}
		catching.faceHaul(spy.target(), haul, false);
		reports.get(catching.name())
			.add(DECISIONS_AND_ALERTS, Line.of(carried, " stolen from " + spy.target()
					+ "; choose whether to keep it or send it back before this Turn ends."));
	}

	/**
	 * A Haul on its way to a Nation.
	 *
	 * @param to the Nation it is on its way to
	 * @param sentBy the Nation that seized it from a caught Spy and sent it
	 * @param stolenFrom the Nation it was stolen from
	 * @param turnsLeft how many more Turns must resolve before it arrives
	 */
	private record Delivery(String to, String sentBy, String stolenFrom, Haul haul, int turnsLeft) {

		Delivery travelled() {
			return new Delivery(to, sentBy, stolenFrom, haul, turnsLeft - 1);
		}

	}

	/**
	 * Brings every Haul on its way one Turn closer, in the order sent. One
	 * that arrives is added to the Nation it was on its way to, if it is still
	 * in the Game, which is told whose it was, or who returned it.
	 */
	private void deliverHauls(Map<String, TurnReport.Draft> reports) {
		List<Delivery> travelling = List.copyOf(deliveries);
		deliveries.clear();
		for (Delivery delivery : travelling) {
			Delivery travelled = delivery.travelled();
			if (travelled.turnsLeft() > 0) {
				deliveries.add(travelled);
				continue;
			}
			Nation receiving = nation(delivery.to());
			if (!receiving.inGame()) {
				continue;
			}
			receiving.gain(delivery.haul());
			Line amount = Line.of(gain(delivery.haul().resource(), delivery.haul().amount()));
			Line arrived = !delivery.to().equals(delivery.stolenFrom())
					? Line.of("The ", amount, " you kept, stolen from " + delivery.stolenFrom() + ", arrived.")
					: delivery.sentBy().equals(delivery.to()) ? Line.of("The ", amount, " stolen from your Nation came back.")
							: Line.of(ruled(delivery.sentBy()) + " returned the ", amount, " stolen from your Nation.");
			reports.get(delivery.to()).add(SPIES_AND_SCOUTS, arrived);
		}
	}

	/**
	 * Has each Nation still in the Game in order settle its Haul decisions, in
	 * the order seized: each Haul, kept unless the Player chose to send it
	 * back, sets off to arrive in {@value #HAUL_DELIVERY_TURNS} Turns, and
	 * nothing can intercept it. One sent back to a Nation no longer in the
	 * Game is kept.
	 */
	private void settleHauls(Map<String, TurnReport.Draft> reports) {
		for (Nation deciding : remaining()) {
			TurnReport.Draft report = reports.get(deciding.name());
			for (HaulDecision decision : deciding.settleHauls()) {
				Line amount = Line.of(neutral(decision.haul().resource(), decision.haul().amount()));
				String arriving = "; it will arrive in " + HAUL_DELIVERY_TURNS + " Turns.";
				boolean returnable = nation(decision.stolenFrom()).inGame();
				if (decision.kept() || !returnable) {
					deliveries.add(new Delivery(deciding.name(), deciding.name(), decision.stolenFrom(), decision.haul(),
							HAUL_DELIVERY_TURNS));
					report.add(SPIES_AND_SCOUTS, decision.kept()
							? Line.of("You kept the ", amount, " stolen from " + decision.stolenFrom() + arriving)
							: Line.of(decision.stolenFrom() + " is no longer in the Game, so you kept the ", amount,
									" stolen from it" + arriving));
					continue;
				}
				deliveries.add(new Delivery(decision.stolenFrom(), deciding.name(), decision.stolenFrom(), decision.haul(),
						HAUL_DELIVERY_TURNS));
				report.add(SPIES_AND_SCOUTS,
						Line.of("You sent the ", amount, " back to " + decision.stolenFrom() + arriving));
			}
		}
	}

	/**
	 * Whether a Spy is on a hostile Mission.
	 */
	private boolean onHostileMission(Spy spy) {
		return spy.mission() != null && ruleset.mission(spy.mission()).orElseThrow().hostile();
	}

	/**
	 * Once all catching is done, reports each capture this Turn to its
	 * sender if one of the sender's Spies is still in place at the Nation that
	 * caught it.
	 */
	private static void tellWitnesses(Map<String, TurnReport.Draft> reports, List<Witnessed> witnessed) {
		for (Witnessed capture : witnessed) {
			if (capture.sender().spiesInPlace().stream().anyMatch(spy -> spy.target().equals(capture.at()))) {
				reports.get(capture.sender().name()).add(SPIES_AND_SCOUTS, capture.line());
			}
		}
	}

	/**
	 * A capture of a Spy its sender is told of, if one of its Spies is still
	 * in place at the Nation that caught it once all catching is done.
	 *
	 * @param at the Nation that caught it
	 * @param line what the sender's Turn Report would say
	 */
	private record Witnessed(Nation sender, String at, String line) {
	}

	/**
	 * The Nations still in the Game other than the one given, in order.
	 */
	private List<Nation> others(Nation nation) {
		return remaining().stream().filter(other -> other != nation).toList();
	}

	/**
	 * A Spy caught by another Nation: it is lost to its sender, and then dies,
	 * stays silent or confesses, which reveals its sender and what the sender
	 * has as the Turn resolved, and Locates the sender. The Nation that caught
	 * it is told in its Turn Report; what the sender may be told is added to
	 * those witnessed.
	 *
	 * @param caughtBy what caught it, such as "Your Scouts", or how, such as
	 * "Your people unmasked and"
	 */
	private void caught(Spy spy, Nation sender, Nation catching, String caughtBy, Map<String, TurnReport.Draft> reports,
			List<Witnessed> witnessed) {
		sender.loseSpy(spy);
		Captured captured = Captured.picked(random.nextDouble());
		TurnReport.Draft report = reports.get(catching.name());
		String caught = caughtBy + " caught a Spy " + whereabouts(spy, catching.name());
		if (captured == Captured.CONFESSES) {
			report.add(SPIES_AND_SCOUTS, caught + ", and it confessed that " + sender.ruled() + " sent it.");
			catching.learn(sender.name(), catching.learnedOf(sender.name())
				.withGold(sender.gold(), turn)
				.withPopulation(sender.population(), turn)
				.withResearch(sender.research(), turn)
				.withArmy(sender.army(), turn)
				.withBuildings(sender.buildings(), sender.damagedBuildings(), sender.defense(ruleset), turn));
			report.add(SPIES_AND_SCOUTS, Line.of("From its confession, " + sender.name() + " has ",
					neutral(GOLD, sender.gold()), ", ", neutral(POPULATION, sender.population()), " and ",
					neutral(RESEARCH, sender.research()), "; " + armyFact(sender) + "; " + buildingsFact(sender) + "."));
			if (catching.locate(sender.name())) {
				report.add(SPIES_AND_SCOUTS, "From its confession, you have Located " + sender.name() + ".");
			}
		}
		else {
			report.add(SPIES_AND_SCOUTS, caught + captured.toCatcher + ".");
		}
		witnessed.add(new Witnessed(sender, catching.name(),
				"Your Spy in " + catching.name() + " reported that " + catching.name() + " caught "
						+ (spy.inPlace() ? "your other Spy there" : "your Spy " + whereabouts(spy, sender.name()))
						+ captured.toSender + "."));
	}

	/**
	 * Such as "in your Nation", "heading to Egypt" or "heading home from your
	 * Nation", for the Nation that sees it.
	 */
	private static String whereabouts(Spy spy, String seenBy) {
		String target = spy.target().equals(seenBy) ? "your Nation" : spy.target();
		if (spy.homeward()) {
			return "heading home from " + target;
		}
		return spy.inPlace() ? "in " + target : "heading to " + target;
	}

	/**
	 * Such as "Your Spy in Egypt is heading home, as Egypt was conquered", for
	 * a Spy as it was before it turned back.
	 */
	private static String spyTurnedBack(Spy spy, String reason) {
		return (spy.inPlace() ? "Your Spy in " + spy.target() + " is heading home"
				: "Your Spy heading to " + spy.target() + " turned back home") + ", as " + reason + ".";
	}

	/**
	 * Such as "heading to Egypt", "waiting at Egypt for the Battle" or
	 * "heading home from your Nation", for the Nation that sees it.
	 */
	private static String whereabouts(Warparty warparty, String seenBy) {
		String target = warparty.target().equals(seenBy) ? "your Nation" : warparty.target();
		if (warparty.homeward()) {
			return "heading home from " + target;
		}
		return warparty.awaitingBattle() ? "waiting at " + target + " for the Battle" : "heading to " + target;
	}

	private boolean happens(Event event) {
		return random.nextDouble() < event.chance();
	}

	/**
	 * How many Units the largest Warparty travelling to a Nation holds, or 0 if
	 * none is.
	 */
	private int largestApproaching(Nation target) {
		return senders().stream()
			.flatMap(sender -> sender.warparties().stream())
			.filter(warparty -> warparty.approaching(target.name()))
			.mapToInt(Warparty::unitCount)
			.max()
			.orElse(0);
	}

	/**
	 * Whether a Nation has what an Event's effects need: some of each Unit
	 * type they lose in its Army, work in progress if they stall or speed it
	 * up, an undamaged non-defensive Building if they Damage some, a Spy to
	 * catch if they catch one, and room for a Lasting Event if they begin one.
	 */
	private boolean canBefall(Event.Effects effects, Nation nation) {
		return effects.unitsLeaving().stream().allMatch(unit -> nation.army().containsKey(unit))
				&& (effects.workTurns() == 0 || nation.workInProgress() > 0)
				&& (effects.damageBuildings() == 0 || !nation.undamagedNonDefensive(ruleset).isEmpty())
				&& (effects.spyCaught() == null || !catchable(effects.spyCaught(), nation).isEmpty())
				&& (effects.lasting() == null || nation.canBeginLasting(turn));
	}

	/**
	 * Whether a Warparty holds some of each Unit type an Event's effects lose.
	 */
	private static boolean canBefall(Event.Effects effects, Warparty warparty) {
		return effects.unitsLeaving().stream().allMatch(unit -> warparty.units().containsKey(unit));
	}

	/**
	 * A Spy an Event may catch, the Nation that sent it, and the one that would
	 * catch it.
	 */
	private record Catchable(Spy spy, Nation sender, Nation catching) {
	}

	/**
	 * The Spies an Event that happened to a Nation may catch: its own abroad,
	 * other than those heading home, in the order sent, each caught by the
	 * Nation it was sent to; or the other Nations' in place in its land, taking
	 * the Nations in order and each one's Spies in the order sent.
	 */
	private List<Catchable> catchable(Event.SpyCaught whose, Nation nation) {
		return switch (whose) {
			case OWN -> nation.spies()
				.stream()
				.filter(spy -> !spy.homeward())
				.map(spy -> new Catchable(spy, nation, nation(spy.target())))
				.toList();
			case ENEMY -> others(nation).stream()
				.flatMap(other -> other.spiesInPlace()
					.stream()
					.filter(spy -> spy.target().equals(nation.name()))
					.map(spy -> new Catchable(spy, other, nation)))
				.toList();
		};
	}

	/**
	 * Applies an Event's or a Dilemma outcome's effects to a Nation, as luck
	 * varies them. Each non-defensive Building it Damages, then each Scout it
	 * loses (see {@link Nation#loseScouts}), then work it stalls
	 * or speeds up, then a Spy it catches are each picked by one draw from the
	 * seeded random source, after the draws that vary its effects, each
	 * Building from those still undamaged (see
	 * {@link Nation#undamagedNonDefensive}); a Spy caught then draws for what
	 * becomes of it, as
	 * {@link #caught} does. The Nation is told only the Event's text of its own
	 * Spy being caught. A Lasting Event they begin is skipped if one cannot
	 * begin for the Nation now, the rest still applying.
	 *
	 * @param name the Event's name, which a Lasting Event it begins takes
	 * @return what changed, such as "your Nation gained 4 Population"
	 */
	private List<Line> befall(String name, Event.Effects authored, Nation nation,
			Map<String, TurnReport.Draft> reports, List<Witnessed> witnessed) {
		Event.Effects effects = authored.varied(random);
		List<Line> changes = new ArrayList<>();
		resourcesChanged(nation.change(effects.gold(), effects.population(), effects.research()))
			.ifPresent(changes::add);
		if (effects.damageDefense() > 0) {
			Nation.DefenseWorn defenseWorn = nation.wearDefense(effects.damageDefense(), ruleset);
			if (defenseWorn.worn() > 0) {
				changes.add(Line.of("your Defense was worn down from " + defenseWorn.before() + " to "
						+ defenseWorn.after() + ", " + buildingsHarmed(defenseWorn)));
			}
		}
		Map<String, Integer> damaged = damageBuildings(nation, effects.damageBuildings());
		if (!damaged.isEmpty()) {
			int count = damaged.values().stream().mapToInt(Integer::intValue).sum();
			changes.add(Line.of("your " + listed(damaged) + (count == 1 ? " was" : " were") + " damaged"));
		}
		if (!effects.units().isEmpty()) {
			changes.addAll(unitsChanged(nation.changeArmy(effects.units()), "your Army"));
		}
		if (effects.scoutsLost() > 0) {
			Map<String, Integer> scoutsLost = nation.loseScouts(effects.scoutsLost(), random);
			if (!scoutsLost.isEmpty()) {
				int count = scoutsLost.values().stream().mapToInt(Integer::intValue).sum();
				changes.add(Line.of(scoutsCounted(scoutsLost) + (count == 1 ? " was" : " were") + " lost in the wilds"));
			}
		}
		if (effects.workTurns() != 0 && nation.workInProgress() > 0) {
			int pick = (int) (random.nextDouble() * nation.workInProgress());
			changes.add(Line.of(workChanged(nation.changeWork(pick, effects.workTurns(), turn + 1))));
		}
		if (effects.spyCaught() != null) {
			List<Catchable> catchable = catchable(effects.spyCaught(), nation);
			if (!catchable.isEmpty()) {
				Catchable picked = catchable.get((int) (random.nextDouble() * catchable.size()));
				caught(picked.spy(), picked.sender(), picked.catching(), "Your people", reports, witnessed);
			}
		}
		if (effects.lasting() != null && nation.canBeginLasting(turn)) {
			LastingEvent lasting = new LastingEvent(name, effects.lasting());
			nation.beginLasting(lasting, turn);
			changes.add(Line.of("for " + turns(lasting.turnsLeft()) + ", your Nation will have ", lastingChanges(lasting)));
		}
		return changes;
	}

	/**
	 * Such as "your Nation gained 4 Population and lost 5 Gold", if anything
	 * changed.
	 */
	private static Optional<Line> resourcesChanged(Nation.Change change) {
		Map<Resource, Integer> changed = new EnumMap<>(Resource.class);
		changed.put(GOLD, change.gold());
		changed.put(POPULATION, change.population());
		changed.put(RESEARCH, change.research());
		List<Line> gained = new ArrayList<>();
		List<Line> lost = new ArrayList<>();
		changed.forEach((resource, amount) -> {
			if (amount > 0) {
				gained.add(Line.of(gain(resource, amount)));
			}
			else if (amount < 0) {
				lost.add(Line.of(loss(resource, -amount)));
			}
		});
		List<Line> resources = new ArrayList<>();
		if (!gained.isEmpty()) {
			resources.add(Line.of("gained ", Line.joined(gained)));
		}
		if (!lost.isEmpty()) {
			resources.add(Line.of("lost ", Line.joined(lost)));
		}
		if (resources.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(Line.of("your Nation ", resources.getFirst(),
				resources.size() > 1 ? Line.of(" and ", resources.getLast()) : Line.of()));
	}

	/**
	 * What a Lasting Event changes each Turn, such as "no Population growth and
	 * 5 Gold less each Turn".
	 */
	private static Line lastingChanges(LastingEvent lasting) {
		List<Line> changes = new ArrayList<>();
		if (lasting.noGrowth()) {
			changes.add(Line.of("no Population growth"));
		}
		Map<Resource, Integer> each = new EnumMap<>(Resource.class);
		each.put(GOLD, lasting.gold());
		each.put(POPULATION, lasting.population());
		each.put(RESEARCH, lasting.research());
		each.forEach((resource, amount) -> {
			if (amount > 0) {
				changes.add(Line.of(gain(resource, amount), " more each Turn"));
			}
			else if (amount < 0) {
				changes.add(Line.of(loss(resource, -amount), " less each Turn"));
			}
		});
		return Line.joined(changes);
	}

	/**
	 * Applies a Warparty Event's effects to it, as luck varies them.
	 *
	 * @param warparty the Warparty, as {@link Nation#warparties()} gave it
	 * @return what changed, such as "1 Warrior joined your Warparty heading to
	 *         Carthage"
	 */
	private List<Line> befall(Event.Effects authored, Nation nation, Warparty warparty) {
		Event.Effects effects = authored.varied(random);
		List<Line> changes = new ArrayList<>();
		Optional<Warparty> current = Optional.of(warparty);
		if (!effects.units().isEmpty()) {
			changes.addAll(unitsChanged(nation.changeWarparty(warparty, effects.units(), ruleset),
					"your Warparty " + whereabouts(warparty, nation.name())));
			current = nation.warparty(warparty.id());
			if (current.isEmpty()) {
				changes.add(Line.of(warparty.plunder().isNone() ? "your Warparty disbanded"
						: "your Warparty disbanded, and its Plunder was lost"));
			}
		}
		if (effects.travelTurns() != 0 && current.isPresent()) {
			changes.add(Line.of(delayed(current.get(), nation.delay(current.get(), effects.travelTurns()))));
		}
		return changes;
	}

	/**
	 * Such as "2 Warriors joined your Army" and "1 Archer left your Army".
	 *
	 * @param where such as "your Army"
	 */
	private static List<Line> unitsChanged(Nation.UnitsChanged changed, String where) {
		List<Line> lines = new ArrayList<>();
		if (!changed.joined().isEmpty()) {
			lines.add(Line.of(countedEach(changed.joined()) + " joined " + where));
		}
		if (!changed.left().isEmpty()) {
			lines.add(Line.of(countedEach(changed.left()) + " left " + where));
		}
		return lines;
	}

	/**
	 * Such as "2 Warriors and 1 Archer".
	 */
	private static String countedEach(Map<String, Integer> units) {
		return joined(units.entrySet().stream().map(unit -> counted(unit.getKey(), unit.getValue())).toList());
	}

	/**
	 * Such as "your Walls will now be built in 3 Turns" or "your Archer ×1
	 * will now finish training in 1 Turn".
	 */
	private String workChanged(Work work) {
		return switch (work) {
			case Construction construction -> "your " + construction.building() + " will now be built in "
					+ turns(construction.turnsLeft());
			case Repair repair -> "your " + repair.building() + " will now be "
					+ (repair.rebuild() ? "rebuilt" : "repaired") + " in " + turns(repair.turnsLeft());
			case Training training -> "your " + training.unit() + " ×" + training.count()
					+ " will now finish training in " + turns(training.readyOnTurn() - turn - 1);
		};
	}

	/**
	 * Such as "1 Turn" or "3 Turns".
	 */
	private static String turns(int turns) {
		return turns + (turns == 1 ? " Turn" : " Turns");
	}

	/**
	 * Such as "your Warparty of Warrior ×2 heading to Carthage will now arrive
	 * in 3 Turns".
	 */
	private static String delayed(Warparty before, Warparty after) {
		String turns = turns(after.turnsLeft());
		return "your Warparty of " + listed(before.units())
				+ (before.homeward() ? " heading home from " + before.target() + " will now get home in " + turns
						: " heading to " + before.target() + " will now arrive in " + turns);
	}

	/**
	 * An Event's or a Dilemma outcome's line in the Turn Report: its text, then
	 * what it changed.
	 */
	private static Line announced(String text, List<Line> changes) {
		if (changes.isEmpty()) {
			return Line.of(text + ".");
		}
		List<Object> pieces = new ArrayList<>(List.of(text + ": "));
		for (int i = 0; i < changes.size(); i++) {
			if (i > 0) {
				pieces.add(" and ");
			}
			pieces.add(changes.get(i));
		}
		pieces.add(".");
		return Line.of(pieces.toArray());
	}

	/**
	 * Applies a Nation's Action at once, or rejects it with a reason for its
	 * Player.
	 */
	public ActionResult act(String nation, Action action) {
		Nation actor = nation(nation);
		if (actor.conquered()) {
			return ActionResult.rejected("Your Nation has been conquered.");
		}
		if (actor.removed()) {
			return ActionResult.rejected("Your Nation has been removed from the Game.");
		}
		if (winner != null) {
			return ActionResult.rejected("The Game is over.");
		}
		return switch (action) {
			case Action.StartBuilding start -> ruleset.building(start.building())
				.map(building -> unlessLocked(actor, building.name(), () -> actor.startBuilding(building)))
				.orElseGet(() -> noSuchBuilding(start.building()));
			case Action.RepairBuilding repair -> ruleset.building(repair.building())
				.map(actor::repair)
				.orElseGet(() -> noSuchBuilding(repair.building()));
			case Action.RebuildBuilding rebuild -> ruleset.building(rebuild.building())
				.map(actor::rebuild)
				.orElseGet(() -> noSuchBuilding(rebuild.building()));
			case Action.SellBuilding sell -> ruleset.building(sell.building())
				.map(actor::sell)
				.orElseGet(() -> noSuchBuilding(sell.building()));
			case Action.TrainUnits train -> {
				if (train.count() < 1) {
					yield ActionResult.rejected("Train at least one Unit.");
				}
				yield ruleset.unit(train.unit())
					.map(unit -> unlessLocked(actor, unit.name(), () -> actor.trainUnits(unit, train.count(), turn)))
					.orElseGet(() -> ActionResult
						.rejected("There is no Unit called " + train.unit() + " that you can train."));
			}
			case Action.SendWarparty send -> {
				Optional<ActionResult> unreachable = unreachable(nation, send.target(), "A Warparty");
				if (unreachable.isPresent()) {
					yield unreachable.get();
				}
				if (send.units().isEmpty()
						|| send.units().values().stream().anyMatch(count -> count == null || count < 1)) {
					yield ActionResult.rejected("Choose at least one Unit to send.");
				}
				if (send.units().keySet().stream().anyMatch(unit -> ruleset.unit(unit).filter(UnitType::scout).isPresent())) {
					yield ActionResult.rejected("Scouts roam the wilds and cannot be sent in a Warparty.");
				}
				if (send.units().keySet().stream().anyMatch(unit -> ruleset.unit(unit).filter(UnitType::spy).isPresent())) {
					yield ActionResult.rejected("Spies are sent on their own, not in a Warparty.");
				}
				yield actor.sendWarparty(send.target(), send.units(), ruleset);
			}
			case Action.SendSpy send -> camp(send.target()).filter(camp -> actor.hasHeardOf(camp.name()))
				.map(camp -> ActionResult.rejected("Spies cannot be sent to a Barbarian Camp."))
				.or(() -> unreachable(nation, send.target(), "A Spy"))
				.orElseGet(() -> actor.sendSpy(send.target(), ruleset));
			case Action.SetMission set -> ruleset.mission(set.mission())
				.map(mission -> actor.setMission(set.spy(), mission))
				.orElseGet(() -> ActionResult.rejected("There is no Mission called " + set.mission() + "."));
			case Action.SetTask set -> setTask(actor, set);
			case Action.CancelTask cancel -> actor.cancelTask(cancel.task());
			case Action.RecallWarparty recall -> recall(actor, recall.warparty());
			case Action.OfferTribute offer -> offer(actor, offer);
			case Action.AnswerTribute answer -> answer(actor, answer);
			case Action.AnswerEncounter answer -> answer(actor, answer);
			case Action.AnswerDilemma answer -> answer(actor, answer);
			case Action.AnswerHaul answer -> actor.answerHaul(answer.haul(), answer.keep());
			case Action.UnlockTechnology unlock -> ruleset.technology(unlock.technology())
				.filter(technology -> actor.sees(technology, ruleset))
				.map(actor::unlock)
				.orElseGet(() -> ActionResult.rejected("There is no Technology called " + unlock.technology() + "."));
		};
	}

	/**
	 * Why a Warparty or Spy cannot be sent from a Nation to a target, if it
	 * cannot: the target must be another Nation still in the Game, or a
	 * Barbarian Camp still standing, which the Nation has Located.
	 *
	 * @param sent what is being sent, such as "A Spy"
	 */
	private Optional<ActionResult> unreachable(String nation, String target, String sent) {
		Optional<Camp> camp = camp(target).filter(heard -> nation(nation).hasHeardOf(heard.name()));
		if (camp.isPresent()) {
			if (camp.get().destroyed()) {
				return Optional.of(ActionResult.rejected(target + " has been destroyed."));
			}
			if (nation(nation).campLocated(target) == null) {
				return Optional
					.of(ActionResult.rejected("You have not Located " + target + " yet; set your Scouts to Locate it."));
			}
			return Optional.empty();
		}
		if (target.equals(nation)) {
			return Optional.of(ActionResult.rejected(sent + " must be sent to another Nation."));
		}
		Optional<ActionResult> outOfGame = outOfGame(target);
		if (outOfGame.isPresent()) {
			return outOfGame;
		}
		if (!nation(nation).hasLocated(target)) {
			return Optional.of(ActionResult.rejected("You have not Located " + target + " yet; set your Scouts to Locate it."));
		}
		return Optional.empty();
	}

	/**
	 * Sets some of a Nation's Idle Scouts to a Task. A Locate Task must be
	 * about another Nation still in the Game, or a Barbarian Camp still
	 * standing that the Nation has heard of, that the Nation has neither
	 * Located nor set another Task to Locate; or, about no target, search the
	 * wilds for any Camp, which only one Task at a time may. A Watch Task
	 * takes exactly one Scout. Watch and Hunt Tasks are about no one Nation.
	 */
	private ActionResult setTask(Nation nation, Action.SetTask set) {
		Optional<Task.Kind> kind = Arrays.stream(Task.Kind.values())
			.filter(known -> known.name().equals(set.kind()))
			.findFirst();
		if (kind.isEmpty()) {
			return ActionResult.rejected("There is no Task called " + set.kind() + ".");
		}
		if (kind.get() == Task.Kind.WATCH && set.scouts() != 1) {
			return ActionResult.rejected("Watching for Warparties takes exactly 1 Scout.");
		}
		if (kind.get() != Task.Kind.LOCATE) {
			return nation.setTask(kind.get(), null, set.scouts());
		}
		String target = set.target();
		if (target == null) {
			if (nation.isLocating(null)) {
				return ActionResult.rejected("Your Scouts are already searching the wilds.");
			}
			return nation.setTask(kind.get(), null, set.scouts());
		}
		Optional<Camp> camp = camp(target).filter(heard -> nation.hasHeardOf(heard.name()));
		if (camp.isPresent()) {
			if (camp.get().destroyed()) {
				return ActionResult.rejected(target + " has been destroyed.");
			}
			if (nation.campLocated(target) != null) {
				return ActionResult.rejected("You have already Located " + target + ".");
			}
			if (nation.isLocating(target)) {
				return ActionResult.rejected("Your Scouts are already Locating " + target + ".");
			}
			return nation.setTask(kind.get(), target, set.scouts());
		}
		if (target.equals(nation.name())) {
			return ActionResult.rejected("Your Scouts cannot Locate your own Nation.");
		}
		Optional<ActionResult> outOfGame = outOfGame(target);
		if (outOfGame.isPresent()) {
			return outOfGame.get();
		}
		if (nation.hasLocated(target)) {
			return ActionResult.rejected("You have already Located " + target + ".");
		}
		if (nation.isLocating(target)) {
			return ActionResult.rejected("Your Scouts are already Locating " + target + ".");
		}
		return nation.setTask(kind.get(), target, set.scouts());
	}

	/**
	 * Why a Nation cannot be the target of an Action, if it cannot: it must be
	 * a Nation still in the Game.
	 */
	private Optional<ActionResult> outOfGame(String target) {
		if (!nations.containsKey(target)) {
			return Optional.of(ActionResult.rejected("There is no Nation called " + target + "."));
		}
		if (nation(target).conquered()) {
			return Optional.of(ActionResult.rejected(target + " has been conquered."));
		}
		if (nation(target).removed()) {
			return Optional.of(ActionResult.rejected(target + " has been removed from the Game."));
		}
		return Optional.empty();
	}

	/**
	 * Recalls one of a Nation's Warparties not yet heading home. Its Player is
	 * told in their next Turn Report, and so is its target's if it had arrived
	 * there, as then there will be no Battle.
	 */
	private ActionResult recall(Nation nation, int id) {
		Optional<Warparty> warparty = nation.warparty(id);
		if (warparty.isEmpty()) {
			return ActionResult.rejected("You have no such Warparty abroad.");
		}
		Warparty recalled = warparty.get();
		if (recalled.homeward()) {
			return ActionResult.rejected("That Warparty is already heading home.");
		}
		nation.recall(recalled);
		tributes.removeIf(tribute -> tribute.isFor(nation.name(), id));
		pendingReport(nation)
			.add(WARFARE, "You Recalled your Warparty of " + listed(recalled.units()) + " heading to " + recalled.target() + ".");
		if (recalled.awaitingBattle()) {
			pendingReport(nation(recalled.target()))
				.add(WARFARE, nation.ruled() + "'s Warparty turned back home before the Battle.");
			nation(recalled.target()).changed();
		}
		return ActionResult.ACCEPTED;
	}

	/**
	 * Offers Tribute against another Nation's Warparty that arrived at this
	 * one, once per Warparty. Nothing is paid until the offer is accepted.
	 */
	private ActionResult offer(Nation defender, Action.OfferTribute offer) {
		Optional<Warparty> warparty = nations.containsKey(offer.nation())
				? nation(offer.nation()).warparty(offer.warparty())
					.filter(arrived -> arrived.awaitingBattle() && arrived.target().equals(defender.name()))
				: Optional.empty();
		if (warparty.isEmpty() && camp(offer.nation()).isPresent()) {
			return ActionResult.rejected("Barbarian Camps accept no Tribute.");
		}
		if (warparty.isEmpty()) {
			return ActionResult.rejected("No such Warparty from " + offer.nation() + " has arrived at your Nation.");
		}
		Nation attacker = nation(offer.nation());
		if (offer.gold() < 0 || offer.population() < 0 || offer.gold() + offer.population() == 0) {
			return ActionResult.rejected("Offer some Gold or Population as Tribute.");
		}
		if (!defender.canAfford(offer.gold(), offer.population())) {
			return ActionResult.rejected("You have only " + defender.gold() + " Gold and " + defender.population()
					+ " Population to offer.");
		}
		if (tributes.stream().anyMatch(tribute -> tribute.isFor(attacker.name(), offer.warparty()))) {
			return ActionResult.rejected("You have already offered " + attacker.name() + " Tribute for that Warparty.");
		}
		tributes.add(new Tribute(defender.name(), attacker.name(), offer.warparty(), offer.gold(), offer.population(),
				Tribute.Answer.NONE));
		defender.changed();
		attacker.changed();
		return ActionResult.ACCEPTED;
	}

	/**
	 * Accepts or refuses the Tribute offered for one of a Nation's Warparties
	 * still awaiting its Battle. Accepting moves the Tribute across at once and
	 * Recalls the Warparty. Both Players are told in their next Turn Report.
	 */
	private ActionResult answer(Nation attacker, Action.AnswerTribute answer) {
		Optional<Tribute> offered = tributes.stream()
			.filter(tribute -> tribute.isFor(attacker.name(), answer.warparty())
					&& tribute.answer() == Tribute.Answer.NONE)
			.findFirst();
		Optional<Warparty> warparty = attacker.warparty(answer.warparty()).filter(Warparty::awaitingBattle);
		if (offered.isEmpty() || warparty.isEmpty()) {
			return ActionResult.rejected("No Tribute awaits your answer for that Warparty.");
		}
		Tribute tribute = offered.get();
		Nation defender = nation(tribute.from());
		if (!answer.accept()) {
			tributes.set(tributes.indexOf(tribute), tribute.answered(Tribute.Answer.REFUSED));
			attacker.changed();
			defender.changed();
			pendingReport(attacker).add(WARFARE,
					Line.of("You refused " + defender.ruled() + "'s Tribute of ", tribute.described(Effect.NEUTRAL), "."));
			pendingReport(defender).add(WARFARE,
					Line.of(attacker.ruled() + " refused your Tribute of ", tribute.described(Effect.NEUTRAL), "."));
			return ActionResult.ACCEPTED;
		}
		if (!defender.canAfford(tribute.gold(), tribute.population())) {
			return ActionResult.rejected(
					defender.name() + " can no longer afford its Tribute of " + tribute.described(Effect.NEUTRAL).text() + ".");
		}
		attacker.recall(warparty.get());
		defender.gain(-tribute.gold(), -tribute.population(), 0);
		attacker.gain(tribute.gold(), tribute.population(), 0);
		defender.changed();
		tributes.set(tributes.indexOf(tribute), tribute.answered(Tribute.Answer.ACCEPTED));
		pendingReport(attacker).add(WARFARE, Line.of("You accepted " + defender.ruled() + "'s Tribute of ",
				tribute.described(Effect.GAIN), ", and your Warparty of " + listed(warparty.get().units())
						+ " turned back home."));
		pendingReport(defender).add(WARFARE, Line.of(attacker.ruled() + " accepted your Tribute of ",
				tribute.described(Effect.LOSS), ", and its Warparty turned back home before the Battle."));
		return ActionResult.ACCEPTED;
	}

	/**
	 * Records whether a Player chose to fight with one of their Nation's
	 * Warparties in an Encounter, replacing any earlier choice; the other
	 * Player sees the choice at once, and the Warparties fight, if either's
	 * latest choice was to, as the Turn ends.
	 */
	private ActionResult answer(Nation nation, Action.AnswerEncounter answer) {
		Optional<Encounter> awaiting = encounters.stream()
			.filter(encounter -> encounter.sideOf(nation.name(), answer.warparty()).isPresent())
			.findFirst();
		if (awaiting.isEmpty()) {
			return ActionResult.rejected("No Encounter awaits your choice for that Warparty.");
		}
		Encounter encounter = awaiting.get();
		Encounter.Side side = encounter.sideOf(nation.name(), answer.warparty()).orElseThrow();
		Encounter.Side other = encounter.facing(side);
		encounters.set(encounters.indexOf(encounter),
				encounter.chosen(side, answer.fight() ? Encounter.Choice.FIGHT : Encounter.Choice.PASS));
		nation.changed();
		if (camp(other.nation()).isEmpty()) {
			nation(other.nation()).changed();
		}
		return ActionResult.ACCEPTED;
	}

	/**
	 * Records the choice a Player made for one of their Nation's Dilemmas,
	 * replacing any earlier choice; the latest applies as the Turn ends, when
	 * their Turn Report describes it.
	 */
	private ActionResult answer(Nation nation, Action.AnswerDilemma answer) {
		Optional<Nation.FacedDilemma> faced = nation.dilemma(answer.dilemma());
		if (faced.isEmpty()) {
			return ActionResult.rejected("There is no Dilemma called " + answer.dilemma() + " awaiting your choice.");
		}
		Optional<Event.Choice> choice = faced.get().dilemma().choice(answer.choice());
		if (choice.isEmpty()) {
			return ActionResult.rejected(answer.dilemma() + " offers no choice called " + answer.choice() + ".");
		}
		nation.choose(faced.get(), choice.get());
		return ActionResult.ACCEPTED;
	}

	/**
	 * Starts a Unit or Building type, unless a Technology the Nation has not
	 * unlocked is needed for it.
	 */
	private ActionResult unlessLocked(Nation nation, String unitOrBuilding, Supplier<ActionResult> start) {
		return nation.locked(unitOrBuilding, ruleset).orElseGet(start);
	}

	private static ActionResult noSuchBuilding(String building) {
		return ActionResult.rejected("There is no Building called " + building + ".");
	}

	private static Optional<Line> buildingsAddedLine(int population, int gold, int research) {
		List<Line> added = new ArrayList<>();
		if (population > 0) {
			added.add(Line.of(gain(POPULATION, population)));
		}
		if (gold > 0) {
			added.add(Line.of(gain(GOLD, gold)));
		}
		if (research > 0) {
			added.add(Line.of(gain(RESEARCH, research)));
		}
		return added.isEmpty() ? Optional.empty()
				: Optional.of(Line.of("Your Buildings added ", Line.joined(added), "."));
	}

	/**
	 * Units counted by type, such as "Warrior ×2 and Archer ×1".
	 */
	private static String listed(Map<String, Integer> units) {
		return joined(units.entrySet().stream().map(e -> e.getKey() + " ×" + e.getValue()).toList());
	}

	/**
	 * Scouts counted together whatever their type, such as "1 Scout" or "3
	 * Scouts".
	 */
	private static String scoutsCounted(Map<String, Integer> scouts) {
		int count = scouts.values().stream().mapToInt(Integer::intValue).sum();
		return count + (count == 1 ? " Scout" : " Scouts");
	}

	/**
	 * How many of each type are in the given list, in the order each first
	 * appears.
	 */
	private static Map<String, Integer> tallied(List<String> types) {
		Map<String, Integer> tally = new LinkedHashMap<>();
		types.forEach(type -> tally.merge(type, 1, Integer::sum));
		return tally;
	}

	/**
	 * The Buildings of one type that a Turn built, repaired or rebuilt, such as
	 * "Your Farm has been built." or "Your 2 Farms have been built.".
	 */
	private static String yourBuildings(String building, int count, String done) {
		return count == 1 ? "Your " + building + " has been " + done + "."
				: "Your " + counted(building, count) + " have been " + done + ".";
	}

	/**
	 * A count of one type, such as "1 Archer", "3 Archers" or "2 Horsemen".
	 */
	static String counted(String type, int count) {
		return count + " " + (count == 1 ? type : plural(type));
	}

	/**
	 * The plural of a Unit or Building type, such as Archers, Horsemen, Spies,
	 * Libraries or Walls.
	 */
	static String plural(String type) {
		if (type.endsWith("s")) {
			return type;
		}
		if (type.endsWith("man")) {
			return type.substring(0, type.length() - 3) + "men";
		}
		if (type.endsWith("y") && !type.matches(".*[aeiou]y")) {
			return type.substring(0, type.length() - 1) + "ies";
		}
		return type + "s";
	}

	/**
	 * Such as "Walls, Moat and Palisade".
	 */
	private static String joined(List<String> items) {
		if (items.size() == 1) {
			return items.getFirst();
		}
		return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
	}

	public NationView viewOf(String nation) {
		Nation own = nation(nation);
		List<MapNation> map = nations.values()
			.stream()
			.map(other -> other.onMap(own.learnedOf(other.name())))
			.toList();
		List<ArrivedWarparty> arrivals = new ArrayList<>();
		for (Nation attacker : remaining()) {
			attacker.awaitingBattle()
				.stream()
				.filter(warparty -> warparty.target().equals(nation))
				.forEach(warparty -> arrivals.add(new ArrivedWarparty(attacker.name(), warparty.id())));
		}
		List<Tribute> involved = tributes.stream().filter(tribute -> tribute.involves(nation)).toList();
		List<Encounter> met = encounters.stream().filter(encounter -> encounter.involves(nation)).toList();
		List<MapCamp> heardOf = own.campsHeardOf()
			.stream()
			.map(name -> camp(name).orElseThrow())
			.map(camp -> new MapCamp(camp.identity(), camp.destroyed(), own.campLocated(camp.name())))
			.toList();
		return own.view(turn, map, heardOf, arrivals, involved, met, ruleset, goldForecast(own), winner);
	}

	/**
	 * See {@link Sender#ruled}.
	 */
	private String ruled(String sender) {
		return sender(sender).ruled();
	}

	private Nation nation(String name) {
		Nation nation = nations.get(name);
		if (nation == null) {
			throw new IllegalArgumentException("There is no Nation called " + name + " in this Game");
		}
		return nation;
	}

}
