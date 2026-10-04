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
	 * The chance each Scout gives its Nation, each Turn, of
	 * discovering each other Nation's Warparty abroad.
	 */
	private static final double DISCOVERY_CHANCE_PER_SCOUT = 0.2;

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
	 * The chance each Scout gives its Nation, each Turn, of catching
	 * each other Nation's Spy still travelling.
	 */
	private static final double CATCH_CHANCE_PER_SCOUT = 0.1;

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
	 * The Encounters Warparties had as the last Turn resolved, in the order
	 * they met, to be fought or not as this Turn ends.
	 */
	private final List<Encounter> encounters = new ArrayList<>();

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
		Nation nation = nation(name);
		if (winner != null || !nation.inGame()) {
			return;
		}
		nation.remove();
		removed.add(name);
		tributes.removeIf(tribute -> tribute.involves(name));
		encounters.removeIf(encounter -> encounter.involves(name));
		List<Nation> left = remaining();
		for (Nation other : left) {
			TurnReport.Draft report = pendingReport(other);
			report.add(DECISIONS_AND_ALERTS, name + " was removed from the Game, as its Player was disconnected for too long.");
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
		settleEncounters(reports);
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
				reports.get(nation.name()).add(SPIES_AND_SCOUTS, "Your Spy came home from " + returned.target() + " and rejoined your Army.");
			}
		}
		// A Nation conquered earlier in this Turn no longer fights, and its
		// Warparties are gone.
		for (Optional<String> target = nextBattle(); target.isPresent(); target = nextBattle()) {
			reportBattle(fight(nation(target.get())), reports);
		}
		for (Nation nation : remaining()) {
			for (Warparty arrived : nation.travelOut()) {
				reports.get(nation.name())
					.add(WARFARE, "Your Warparty of " + listed(arrived.units()) + " arrived at " + arrived.target()
							+ "; the Battle will be fought as this Turn ends.");
				reports.get(arrived.target())
					.add(DECISIONS_AND_ALERTS, nation.ruled()
							+ "'s Warparty arrived at your Nation; the Battle will be fought as this Turn ends.");
			}
			for (Spy arrived : nation.spiesTravelOut()) {
				reports.get(nation.name()).add(SPIES_AND_SCOUTS, "Your Spy arrived at " + arrived.target() + ".");
			}
		}
		for (Nation nation : remaining()) {
			TurnReport.Draft report = reports.get(nation.name());
			IncomeAndUpkeep earnings = earnAndPayUpkeep(nation);
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
		rollEvents(reports, witnessed);
		scout(reports);
		catchSpies(reports, witnessed);
		tellWitnesses(reports, witnessed);
		workOnMissions(reports);
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
	 */
	private IncomeAndUpkeep earnAndPayUpkeep(Nation nation) {
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
		List<Nation.Desertion> desertions = nation.desertUntilUpkeepIsAffordable(ruleset);
		return new IncomeAndUpkeep(populationGold, populationGrowth, buildingGold, buildingPopulation, buildingResearch,
				lasting.orElse(null), lastingChange, desertions, nation.payUpkeep(ruleset));
	}

	/**
	 * The Gold a Nation will gain or lose as this Turn resolves, worked out by
	 * resolving a copy of it the way {@link #endTurn} does, leaving out what
	 * Dilemmas, Battles, Plunder, Tribute and Events would change, though not
	 * what its active Lasting Event will. Warparties and Spies coming home
	 * change no Gold but Plunder, and leave its Units' upkeep the same, so
	 * they are left out too.
	 */
	private GoldForecast goldForecast(Nation nation) {
		Nation next = nation.copy();
		next.progressWork(turn + 1, ruleset);
		IncomeAndUpkeep earnings = earnAndPayUpkeep(next);
		return new GoldForecast(earnings.populationGold() + earnings.buildingGold(), earnings.upkeep(),
				earnings.lastingChange().gold());
	}

	/**
	 * The Nation the next Battle is fought at: the target of the first
	 * Warparty awaiting a Battle, taking the Nations in order and each one's
	 * Warparties in the order sent.
	 */
	private Optional<String> nextBattle() {
		return remaining().stream()
			.flatMap(nation -> nation.awaitingBattle().stream())
			.map(Warparty::target)
			.findFirst();
	}

	/**
	 * A Warparty that arrived at a Nation, and the Nation that sent it.
	 */
	private record Arrival(Nation attacker, Warparty warparty) {
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
	 * Fights the Battle at a Nation: every Warparty awaiting a Battle there,
	 * from the Nations in order and each one's in the order sent, fights its
	 * Army as one. The Warparties' surviving Strength, rounded down, wears the
	 * Nation's Defense down, and what is left once Defense is 0 reduces its
	 * Population, more so if its Army was wiped out or it had neither Army nor
	 * Defense. If the Nation is then Breached, half of what is left, rounded
	 * down, Damages one of its undamaged non-defensive Buildings for every
	 * {@value #STRENGTH_PER_BUILDING_DAMAGED} Strength (see
	 * {@link #damageBuildings}), and whatever of that half cannot be used
	 * reduces its Population with the rest. A Nation left with neither
	 * Population nor Army is conquered, unless the Warparties were all wiped
	 * out too; otherwise the survivors take plunder scaled by their remaining
	 * Strength, rounded down. What is plundered or seized is split between the
	 * Warparties in proportion to their surviving Strength, so one wiped out
	 * gets none of it. The Nation Locates every attacking Nation.
	 */
	private BattleFought fight(Nation defender) {
		List<Arrival> arrivals = new ArrayList<>();
		for (Nation attacker : remaining()) {
			attacker.awaitingBattle()
				.stream()
				.filter(warparty -> warparty.target().equals(defender.name()))
				.forEach(warparty -> arrivals.add(new Arrival(attacker, warparty)));
		}
		Battle battle = Battle.fight(arrivals.stream().map(arrival -> arrival.warparty().units()).toList(),
				defender.army(), ruleset, random);
		List<String> located = arrivals.stream()
			.map(arrival -> arrival.attacker().name())
			.distinct()
			.filter(defender::locate)
			.toList();
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
		int populationLost = defender.losePopulation(strengthLeft * POPULATION_PER_STRENGTH * multiplier);
		// Survivors with no Strength at all still take an equal share of what
		// is seized.
		List<Double> weights = strength > 0 ? strengths
				: IntStream.range(0, arrivals.size())
					.mapToObj(arrival -> battle.warpartySurvivors(arrival).isEmpty() ? 0.0 : 1.0)
					.toList();
		boolean conquered = battle.anyWarpartySurvived() && defender.hasNeitherPopulationNorArmy();
		Plunder taken;
		if (conquered) {
			List<String> conquerors = IntStream.range(0, arrivals.size())
				.filter(arrival -> !battle.warpartySurvivors(arrival).isEmpty())
				.mapToObj(arrival -> arrivals.get(arrival).attacker().name())
				.distinct()
				.toList();
			taken = defender.conquer(joined(conquerors), CONQUEST_SHARE);
		}
		else {
			taken = defender.plundered((int) Math.floor(strength * PLUNDER_GOLD_PER_STRENGTH),
					(int) Math.floor(strength * PLUNDER_RESEARCH_PER_STRENGTH));
		}
		List<Plunder> shares = shared(taken, weights);
		for (int i = 0; i < arrivals.size(); i++) {
			Nation attacker = arrivals.get(i).attacker();
			if (conquered) {
				attacker.seize(shares.get(i));
			}
			attacker.foughtBattle(arrivals.get(i).warparty(), battle.warpartySurvivors(i),
					conquered ? Plunder.NONE : shares.get(i), shares.get(i), ruleset);
		}
		return new BattleFought(defender, arrivals, battle, located, shares, defenseWorn, buildingsDamaged, populationLost,
				exposure, conquered ? turnBackFrom(defender, taken) : null);
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
			String attacker = arrivals.get(i).attacker().name();
			String warparty = listed(battle.warparties().get(i));
			sides.add(ruled(attacker) + "'s Warparty of " + warparty);
			losses.add(attacker + " lost " + lost(battle.warpartiesLost().get(i)));
			List<String> alongside = new ArrayList<>();
			for (int j = 0; j < arrivals.size(); j++) {
				if (j != i) {
					String other = arrivals.get(j).attacker().name();
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
				String attacker = arrivals.get(i).attacker().name();
				reports.get(attacker).add(WARFARE, "Your Warparty sent to " + defender + " was destroyed.");
				defenderReport.add(WARFARE, ruled(attacker) + "'s Warparty was destroyed.");
			}
		}
		List<Arrival> survivors = fought.survivors();
		if (survivors.isEmpty()) {
			return;
		}
		List<String> attackers = survivors.stream().map(arrival -> arrival.attacker().name()).distinct().toList();
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
			Plunder seized = conquest.seized();
			if (survivors.size() == 1) {
				reports.get(attackers.getFirst())
					.add(WARFARE, Line.of("Your Warparty conquered " + defender + ", seizing ",
							seized.described(Effect.GAIN), ", and is heading home."));
			}
			else {
				for (String attacker : attackers) {
					reports.get(attacker).add(WARFARE, warpartiesFrom(attacker, survivors) + " conquered " + defender + ".");
					for (int i = 0; i < arrivals.size(); i++) {
						if (fought.survived(i) && arrivals.get(i).attacker().name().equals(attacker)) {
							reports.get(attacker)
								.add(WARFARE, Line.of("Your Warparty of " + listed(battle.warparties().get(i)) + " seized ",
										fought.shares().get(i).described(Effect.GAIN),
										" as its share and is heading home."));
						}
					}
				}
			}
			defenderReport.add(DECISIONS_AND_ALERTS, Line.of(warpartiesOf(survivors) + " conquered your Nation, seizing ",
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
			String attacker = arrivals.get(i).attacker().name();
			Plunder plunder = fought.shares().get(i);
			if (plunder.isNone()) {
				reports.get(attacker).add(WARFARE, "Your Warparty took nothing from " + defender + " and is heading home.");
				defenderReport.add(WARFARE, ruled(attacker) + "'s Warparty took nothing from you and is heading home.");
			}
			else {
				reports.get(attacker)
					.add(WARFARE, Line.of("Your Warparty took ", plunder.described(Effect.GAIN),
							" from " + defender + share + " and is heading home."));
				defenderReport.add(WARFARE, Line.of(ruled(attacker) + "'s Warparty took ",
						plunder.described(Effect.LOSS), " from you and is heading home."));
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
	 * Has each Nation still in the Game in order, if it has Scouts,
	 * watch for every other Nation's Warparty abroad, taking the Nations in
	 * order and each one's Warparties in the order sent, drawing once from the
	 * seeded random source for each. Each Scout gives an equal chance, of its
	 * own, of discovering a Warparty. One discovered is announced in the Turn
	 * Report; a Warparty that has arrived at the Nation watching was
	 * announced already, and is not looked for.
	 */
	private void scout(Map<String, TurnReport.Draft> reports) {
		for (Nation scouting : remaining()) {
			int scouts = scouting.scouts();
			if (scouts == 0) {
				continue;
			}
			double chance = 1 - Math.pow(1 - DISCOVERY_CHANCE_PER_SCOUT, scouts);
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
		}
	}

	/**
	 * Has each Nation still in the Game in order search, with each of its
	 * Locate Tasks in the order set, for the Nation the Task is about, drawing
	 * once from the seeded random source for each. Each Scout on the Task
	 * gives an equal chance, of its own, of Locating the Nation. A Task that
	 * succeeds ends, its Scouts going Idle (see {@link Nation#locate}), and is
	 * announced in the Turn Report. The search comes first as a Turn resolves, before anything else
	 * draws.
	 */
	private void searchForNations(Map<String, TurnReport.Draft> reports) {
		for (Nation searching : remaining()) {
			for (Task task : searching.tasks()) {
				if (task.kind() != Task.Kind.LOCATE) {
					continue;
				}
				double chance = 1 - Math.pow(1 - LOCATE_CHANCE_PER_SCOUT, task.scouts());
				if (random.nextDouble() < chance) {
					searching.locate(task.target());
					reports.get(searching.name())
						.add(SPIES_AND_SCOUTS, "Your Scouts Located " + task.target()
								+ ", so you can now send Spies and Warparties there.");
				}
			}
		}
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
	 * the order sent. A Warparty that has met one already this Turn meets no
	 * other, and a pair with such a Warparty draws nothing. Both Players are
	 * told in their Turn Report, and choose during the next Turn whether to
	 * fight.
	 */
	private void meet(Map<String, TurnReport.Draft> reports) {
		List<Nation> left = remaining();
		Set<Warparty> met = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int i = 0; i < left.size(); i++) {
			Nation nation = left.get(i);
			for (Warparty warparty : nation.warparties()) {
				for (Nation other : left.subList(i + 1, left.size())) {
					for (Warparty otherWarparty : other.warparties()) {
						if (warparty.awaitingBattle() || otherWarparty.awaitingBattle() || met.contains(warparty)
								|| met.contains(otherWarparty) || random.nextDouble() >= ENCOUNTER_CHANCE) {
							continue;
						}
						met.add(warparty);
						met.add(otherWarparty);
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
	}

	/**
	 * Such as "Your Warparty of Warrior ×2 heading to Carthage met Cleopatra of
	 * Egypt's Warparty of Archer ×3; choose whether to fight before this Turn
	 * ends."
	 *
	 * @param other the other Nation, named by its Ruler
	 */
	private static String metLine(Warparty own, String other, Warparty theirs) {
		return yourWarparty(own) + " met " + other
				+ "'s Warparty of " + listed(theirs.units()) + "; choose whether to fight before this Turn ends.";
	}

	/**
	 * Settles each of last Turn's Encounters in the order met: if either
	 * Player chose to fight, the Warparties fight by a Battle's rules, the first
	 * as the attacking side, and each loses Units with no delay to its travel;
	 * one wiped out is destroyed. Otherwise they go their separate ways. Both
	 * Players are told in their Turn Report.
	 */
	private void settleEncounters(Map<String, TurnReport.Draft> reports) {
		for (Encounter encounter : encounters) {
			Nation first = nation(encounter.first().nation());
			Nation second = nation(encounter.second().nation());
			Optional<Warparty> firstWarparty = first.warparty(encounter.first().warparty());
			Optional<Warparty> secondWarparty = second.warparty(encounter.second().warparty());
			if (firstWarparty.isEmpty() || secondWarparty.isEmpty()) {
				continue;
			}
			Warparty firstBefore = firstWarparty.get();
			Warparty secondBefore = secondWarparty.get();
			if (!encounter.fought()) {
				reports.get(first.name()).add(WARFARE, separateWays(firstBefore, second.ruled(), secondBefore));
				reports.get(second.name()).add(WARFARE, separateWays(secondBefore, first.ruled(), firstBefore));
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
			if (battle.warpartySurvivors(0).isEmpty()) {
				reportDestroyed(firstBefore, first.name(), second.name(), reports);
			}
			if (battle.armySurvivors().isEmpty()) {
				reportDestroyed(secondBefore, second.name(), first.name(), reports);
			}
		}
		encounters.clear();
	}

	/**
	 * Such as "Your Warparty of Warrior ×2 heading to Carthage fought Cleopatra
	 * of Egypt's Warparty of Archer ×3, as Egypt chose to fight. You lost
	 * Warrior ×1 and Egypt lost no Units."
	 */
	private void reportEncounter(Encounter.Side own, Encounter.Side other, Warparty ownWarparty,
			Warparty otherWarparty, Map<String, Integer> ownLost, Map<String, Integer> otherLost,
			Map<String, TurnReport.Draft> reports) {
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
	 * @param other the other Nation, named by its Ruler
	 */
	private static String separateWays(Warparty own, String other, Warparty theirs) {
		return yourWarparty(own) + " and " + other
				+ "'s Warparty of " + listed(theirs.units())
				+ " went their separate ways, as neither of you chose to fight.";
	}

	/**
	 * Tells both Nations that a Warparty was wiped out fighting in an
	 * Encounter.
	 */
	private void reportDestroyed(Warparty destroyed, String nation, String other,
			Map<String, TurnReport.Draft> reports) {
		reports.get(nation).add(WARFARE, "Your Warparty " + whereabouts(destroyed, null) + " was destroyed.");
		reports.get(other).add(WARFARE, ruled(nation) + "'s Warparty was destroyed.");
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
	 * Mission ends goes Idle, and the Player is alerted. Idle Spies do
	 * nothing.
	 * <p>
	 * An Intel Mission that ends reports its Fact, as it stands once the Turn
	 * has resolved, in the Turn Report and on the Map, tagged with this Turn,
	 * drawing no luck. A hostile Mission that ends draws once from the seeded
	 * random source for whether it succeeds, and its sender is told which; one
	 * that succeeds harms its target (see {@link #sabotage}), whose Turn
	 * Report says what it harmed without naming the sender.
	 */
	private void workOnMissions(Map<String, TurnReport.Draft> reports) {
		for (Nation spying : remaining()) {
			for (Spy spy : spying.workOnMissions()) {
				TurnReport.Draft report = reports.get(spying.name());
				Mission mission = ruleset.mission(spy.mission()).orElseThrow();
				if (mission.hostile()) {
					boolean succeeded = random.nextDouble() < mission.chance();
					report.add(SPIES_AND_SCOUTS, "Your Spy in " + spy.target() + (succeeded ? " succeeded" : " failed")
							+ " at " + mission.name() + ".");
					if (succeeded) {
						List<Line> harmed = sabotage(mission, nation(spy.target()), reports);
						if (!harmed.isEmpty()) {
							reports.get(spy.target()).add(SPIES_AND_SCOUTS, announced(mission.text(), harmed));
						}
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
	 * caught as any other; then with its Scouts, and then with each of its
	 * travelling Warparties in the order sent, those still travelling
	 * anywhere. Each try takes the other
	 * Nations in order and each one's Spies in the order sent, drawing once
	 * from the seeded random source for each Spy not yet caught; a Spy caught
	 * draws once more for what becomes of it. See {@link #caught}.
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
						double perSpy = onHostileMission(spy) ? HOSTILE_CATCH_MULTIPLIER * CATCH_CHANCE_PER_SPY_AT_HOME
								: CATCH_CHANCE_PER_SPY_AT_HOME;
						if (random.nextDouble() < 1 - Math.pow(1 - perSpy, spies)) {
							caught(spy, other, catching, "Your Spies", reports, witnessed);
						}
					}
				}
			}
			int scouts = catching.scouts();
			if (scouts > 0) {
				double chance = 1 - Math.pow(1 - CATCH_CHANCE_PER_SCOUT, scouts);
				for (Nation other : others(catching)) {
					for (Spy spy : other.spiesTravelling()) {
						if (random.nextDouble() < chance) {
							caught(spy, other, catching, "Your Scouts", reports, witnessed);
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
						}
					}
				}
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
	 * @param caughtBy what caught it, such as "Your Scouts"
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
		return remaining().stream()
			.flatMap(nation -> nation.warparties().stream())
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
	 * varies them. Each non-defensive Building it Damages, then work it stalls
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
			Map<String, Integer> scoutsLost = nation.loseScouts(effects.scoutsLost());
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
			case Action.SendSpy send -> unreachable(nation, send.target(), "A Spy")
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
			case Action.UnlockTechnology unlock -> ruleset.technology(unlock.technology())
				.filter(technology -> actor.sees(technology, ruleset))
				.map(actor::unlock)
				.orElseGet(() -> ActionResult.rejected("There is no Technology called " + unlock.technology() + "."));
		};
	}

	/**
	 * Why a Warparty or Spy cannot be sent from a Nation to a target, if it
	 * cannot: the target must be another Nation still in the Game, which the
	 * Nation has Located.
	 *
	 * @param sent what is being sent, such as "A Spy"
	 */
	private Optional<ActionResult> unreachable(String nation, String target, String sent) {
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
	 * about another Nation still in the Game that the Nation has neither
	 * Located nor set another Task to Locate.
	 */
	private ActionResult setTask(Nation nation, Action.SetTask set) {
		Optional<Task.Kind> kind = Arrays.stream(Task.Kind.values())
			.filter(known -> known.name().equals(set.kind()))
			.findFirst();
		if (kind.isEmpty()) {
			return ActionResult.rejected("There is no Task called " + set.kind() + ".");
		}
		String target = set.target();
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
		nation(other.nation()).changed();
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
		return own.view(turn, map, arrivals, involved, met, ruleset, goldForecast(own), winner);
	}

	/**
	 * See {@link Nation#ruled}.
	 */
	private String ruled(String nation) {
		return nation(nation).ruled();
	}

	private Nation nation(String name) {
		Nation nation = nations.get(name);
		if (nation == null) {
			throw new IllegalArgumentException("There is no Nation called " + name + " in this Game");
		}
		return nation;
	}

}
