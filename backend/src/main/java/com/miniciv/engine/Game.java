package com.miniciv.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

import static com.miniciv.engine.TurnReport.Kind.COMPLETED;
import static com.miniciv.engine.TurnReport.Kind.DECISIONS_AND_ALERTS;
import static com.miniciv.engine.TurnReport.Kind.EVENTS;
import static com.miniciv.engine.TurnReport.Kind.RESOURCES;
import static com.miniciv.engine.TurnReport.Kind.SPIES_AND_SCOUTS;
import static com.miniciv.engine.TurnReport.Kind.WARFARE;

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
	 * The share of a conquered Nation's Gold and Research its conqueror seizes.
	 */
	private static final double CONQUEST_SHARE = 0.75;

	/**
	 * The chance each Scout gives its Nation, each Turn, of
	 * discovering each other Nation's Warparty abroad.
	 */
	private static final double DISCOVERY_CHANCE_PER_SCOUT = 0.2;

	/**
	 * The chance each Spy in place gives its Nation, each Turn, of a report.
	 */
	private static final double SPY_REPORT_CHANCE = 0.4;

	/**
	 * The chance each Spy at home gives its Nation, each Turn, of catching each
	 * other Nation's Spy in place there.
	 */
	private static final double CATCH_CHANCE_PER_SPY_AT_HOME = 0.2;

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

	/**
	 * The facts a Spy may report, each as likely as its weight: Buildings,
	 * which reveal Defense, only occasionally.
	 */
	private enum SpiedFact {
		GOLD(2), POPULATION(2), RESEARCH(2), ARMY(2), BUILDINGS(1);

		private final int weight;

		SpiedFact(int weight) {
			this.weight = weight;
		}

		/**
		 * The fact a draw between 0 and 1 picks, by weight in the order listed.
		 */
		static SpiedFact picked(double draw) {
			double total = Arrays.stream(values()).mapToInt(fact -> fact.weight).sum();
			double at = draw * total;
			for (SpiedFact fact : values()) {
				if (at < fact.weight) {
					return fact;
				}
				at -= fact.weight;
			}
			return values()[values().length - 1];
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
	 * and Spy at it or heading to it, turns back home. The other Nations are
	 * told in their next Turn Report, and if only one is left it wins the Game.
	 * Has no effect on a Nation no longer in the Game, or once the Game is won.
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
					.add(WARFARE, "You did not answer " + ruled(lapsed.from()) + "'s Tribute of " + lapsed.described()
							+ " in time.");
				reports.get(lapsed.from())
					.add(WARFARE, ruled(lapsed.to()) + " did not answer your Tribute of " + lapsed.described()
							+ " in time, so the Battle went ahead.");
			}
		}
		tributes.clear();
		settleEncounters(reports);
		for (Nation nation : inPlay) {
			TurnReport.Draft report = reports.get(nation.name());
			// The choice that applies draws luck for its outcome, then for that
			// outcome's effects, Nation by Nation and Dilemma by Dilemma.
			for (Nation.FacedDilemma faced : nation.settleDilemmas()) {
				Event.Outcome outcome = faced.applying().outcome(random);
				String line = announced(outcome.text(), befall(outcome.effects(), nation));
				report.add(EVENTS, faced.chosen() == null
						? "You did not choose in time what to do about " + faced.dilemma().name() + ". " + line
						: line);
			}
			for (String completed : nation.progressConstruction()) {
				report.add(COMPLETED, "Your " + completed + " has been built.");
			}
			for (Repair finished : nation.progressRepairs()) {
				report.add(COMPLETED, "Your " + finished.building() + " has been " + (finished.rebuild() ? "rebuilt." : "repaired."));
			}
			Nation.Trained trained = nation.progressTraining(turn + 1, ruleset);
			if (!trained.joined().isEmpty()) {
				report.add(COMPLETED, listed(trained.joined()) + " finished training and joined your Army.");
			}
			if (!trained.setOut().isEmpty()) {
				report.add(COMPLETED, scoutsCounted(trained.setOut()) + " set out into the wilds.");
			}
		}
		for (Nation nation : inPlay) {
			for (Warparty returned : nation.travelHome()) {
				String plunder = returned.plunder().isNone() ? "" : " with " + returned.plunder().described() + ",";
				reports.get(nation.name())
					.add(WARFARE, "Your Warparty came home from " + returned.target() + plunder + " and "
							+ listed(returned.units()) + " rejoined your Army.");
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
			int buildingPopulation = nation.populationFromBuildings(ruleset);
			int buildingGold = nation.goldFromBuildings(ruleset);
			int buildingResearch = nation.researchFromBuildings(ruleset);
			int populationGold = nation.goldFromPopulation();
			nation.gain(populationGold + buildingGold, POPULATION_PER_TURN + buildingPopulation,
					RESEARCH_PER_TURN + buildingResearch);
			report.add(RESOURCES, "Your Population earned " + populationGold + " Gold, and your Nation gained "
					+ POPULATION_PER_TURN + " Population and " + RESEARCH_PER_TURN + " Research.");
			buildingsAddedLine(buildingPopulation, buildingGold, buildingResearch).ifPresent(line -> report.add(RESOURCES, line));
			for (Nation.Desertion desertion : nation.desertUntilUpkeepIsAffordable(ruleset)) {
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
			int upkeep = nation.payUpkeep(ruleset);
			if (upkeep > 0) {
				report.add(RESOURCES, "Your Units' upkeep cost " + upkeep + " Gold.");
			}
		}
		rollEvents(reports);
		scout(reports);
		catchSpies(reports);
		spy(reports);
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
	 * @param shares the plunder each attacking Warparty took or, if the
	 *        Nation was conquered, its share of what was seized
	 * @param exposure how exposed the Nation's Population was
	 * @param conquest how the Nation was conquered, or null if it was not
	 */
	private record BattleFought(Nation defender, List<Arrival> arrivals, Battle battle, List<Plunder> shares,
			Nation.DefenseWorn defenseWorn, int populationLost, Exposure exposure, Nation.Conquest conquest) {

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
	 * Defense. A Nation left with neither Population nor Army is conquered,
	 * unless the Warparties were all wiped out too; otherwise the survivors
	 * take plunder scaled by their remaining Strength, rounded down. What is
	 * plundered or seized is split between the Warparties in proportion to
	 * their surviving Strength, so one wiped out gets none of it.
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
		defender.lose(battle.armyLost());
		List<Double> strengths = IntStream.range(0, arrivals.size())
			.mapToObj(arrival -> battle.warpartySurvivingStrength(arrival, ruleset))
			.toList();
		double strength = strengths.stream().mapToDouble(Double::doubleValue).sum();
		int wholeStrength = (int) Math.floor(strength);
		Nation.DefenseWorn defenseWorn = defender.wearDefense(wholeStrength, ruleset);
		int strengthLeft = wholeStrength - defenseWorn.worn();
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
		return new BattleFought(defender, arrivals, battle, shares, defenseWorn, populationLost, exposure,
				conquered ? turnBackFrom(defender, taken) : null);
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
	 * Records a Nation as conquered, and turns every other Warparty heading to
	 * it back home.
	 */
	private Nation.Conquest turnBackFrom(Nation conquered, Plunder seized) {
		this.conquered.add(conquered.name());
		Map<String, List<Warparty>> turnedBack = new LinkedHashMap<>();
		Map<String, List<Spy>> spiesTurnedBack = new LinkedHashMap<>();
		for (Nation nation : remaining()) {
			List<Warparty> turned = nation.turnBackFrom(conquered.name());
			if (!turned.isEmpty()) {
				turnedBack.put(nation.name(), turned);
			}
			List<Spy> spies = nation.turnSpiesBackFrom(conquered.name());
			if (!spies.isEmpty()) {
				spiesTurnedBack.put(nation.name(), spies);
			}
		}
		return new Nation.Conquest(seized, turnedBack, spiesTurnedBack);
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
				.add(WARFARE, warpartiesFrom(attacker, survivors) + " reduced " + defender + "'s Population by "
						+ fought.populationLost() + attackerReason + "."));
			defenderReport.add(WARFARE, warpartiesOf(survivors) + " reduced your Population by " + fought.populationLost()
					+ defenderReason + ".");
		}
		Nation.Conquest conquest = fought.conquest();
		if (conquest != null) {
			String seized = conquest.seized().described();
			if (survivors.size() == 1) {
				reports.get(attackers.getFirst())
					.add(WARFARE, "Your Warparty conquered " + defender + ", seizing " + seized + ", and is heading home.");
			}
			else {
				for (String attacker : attackers) {
					reports.get(attacker).add(WARFARE, warpartiesFrom(attacker, survivors) + " conquered " + defender + ".");
					for (int i = 0; i < arrivals.size(); i++) {
						if (fought.survived(i) && arrivals.get(i).attacker().name().equals(attacker)) {
							reports.get(attacker)
								.add(WARFARE, "Your Warparty of " + listed(battle.warparties().get(i)) + " seized "
										+ fought.shares().get(i).described() + " as its share and is heading home.");
						}
					}
				}
			}
			defenderReport.add(DECISIONS_AND_ALERTS, warpartiesOf(survivors) + " conquered your Nation, seizing " + seized
					+ ". You are knocked out of the Game.");
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
					.add(WARFARE, "Your Warparty took " + plunder.described() + " from " + defender + share
							+ " and is heading home.");
				defenderReport.add(WARFARE,
						ruled(attacker) + "'s Warparty took " + plunder.described() + " from you and is heading home.");
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

	private static String lost(Map<String, Integer> units) {
		return units.isEmpty() ? "no Units" : listed(units);
	}

	/**
	 * Rolls every Event, in the order listed, for each Nation still in the
	 * Game in order or each of its travelling Warparties in the order sent,
	 * that the Event's conditions hold for, drawing once from the seeded random
	 * source for each. One that happens takes effect at once, as luck varies
	 * it (see {@link Event.Effects#varied}), and is announced in the Turn
	 * Report of the Nation it happened to, or that sent the Warparty.
	 */
	private void rollEvents(Map<String, TurnReport.Draft> reports) {
		for (Event event : ruleset.events()) {
			for (Nation nation : remaining()) {
				TurnReport.Draft report = reports.get(nation.name());
				switch (event.subject()) {
					case NATION -> {
						if (event.when().holdForNation(turn, nation, nation.defense(ruleset), largestApproaching(nation))
								&& happens(event)) {
							if (event.dilemma()) {
								nation.faceDilemma(event);
								report.add(DECISIONS_AND_ALERTS, event.text() + "; choose what to do before this Turn ends.");
							}
							else {
								report.add(EVENTS, announced(event.text(), befall(event.effects(), nation)));
							}
						}
					}
					case WARPARTY -> {
						for (Warparty warparty : nation.warparties()) {
							if (!warparty.awaitingBattle() && event.when().holdForWarparty(turn, warparty)
									&& happens(event)) {
								report.add(EVENTS, announced(event.text(),
										List.of(delayed(warparty, nation.delay(warparty, event.effects().travelTurns())))));
							}
						}
					}
				}
			}
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
	 * Has each Nation still in the Game in order watch, through each of its
	 * Spies in place in the order sent, the Nation the Spy is at, drawing once
	 * from the seeded random source for whether the Spy reports and, if it
	 * does, once more for which fact. The fact, as it stands once the Turn has
	 * resolved, is announced in the Turn Report and shown on the Map, tagged
	 * with this Turn.
	 */
	private void spy(Map<String, TurnReport.Draft> reports) {
		for (Nation spying : remaining()) {
			for (Spy spy : spying.spiesInPlace()) {
				if (random.nextDouble() >= SPY_REPORT_CHANCE) {
					continue;
				}
				Nation target = nation(spy.target());
				String name = target.name();
				Facts facts = spying.learnedOf(name);
				String reported = switch (SpiedFact.picked(random.nextDouble())) {
					case GOLD -> {
						facts = facts.withGold(target.gold(), turn);
						yield name + " has " + target.gold() + " Gold";
					}
					case POPULATION -> {
						facts = facts.withPopulation(target.population(), turn);
						yield name + " has " + target.population() + " Population";
					}
					case RESEARCH -> {
						facts = facts.withResearch(target.research(), turn);
						yield name + " has " + target.research() + " Research";
					}
					case ARMY -> {
						facts = facts.withArmy(target.army(), turn);
						yield armyFact(target);
					}
					case BUILDINGS -> {
						facts = facts.withBuildings(target.buildings(), target.defense(ruleset), turn);
						yield buildingsFact(target);
					}
				};
				spying.learn(name, facts);
				reports.get(spying.name()).add(SPIES_AND_SCOUTS, "Your Spy in " + name + " reported that " + reported + ".");
			}
		}
	}

	/**
	 * Such as "Egypt's Army is Warrior ×2" or "Egypt has no Army".
	 */
	private static String armyFact(Nation nation) {
		return nation.army().isEmpty() ? nation.name() + " has no Army"
				: nation.name() + "'s Army is " + listed(nation.army());
	}

	/**
	 * Such as "Egypt has built Walls ×1, and its Defense is 10".
	 */
	private String buildingsFact(Nation nation) {
		return nation.name() + " has built "
				+ (nation.buildings().isEmpty() ? "nothing" : listed(nation.buildings())) + ", and its Defense is "
				+ nation.defense(ruleset);
	}

	/**
	 * Has each Nation still in the Game in order try to catch other Nations'
	 * Spies: with its Spies at home, those in place at it; then with its
	 * Scouts, and then with each of its travelling Warparties in the
	 * order sent, those still travelling anywhere. Each try takes the other
	 * Nations in order and each one's Spies in the order sent, drawing once
	 * from the seeded random source for each Spy not yet caught; a Spy caught
	 * draws once more for what becomes of it. See {@link #caught}. Once every
	 * Nation has tried, each capture is reported to its sender if one of the
	 * sender's Spies is still in place at the Nation that caught it.
	 */
	private void catchSpies(Map<String, TurnReport.Draft> reports) {
		List<Witnessed> witnessed = new ArrayList<>();
		for (Nation catching : remaining()) {
			int spies = catching.spiesAtHome(ruleset);
			if (spies > 0) {
				double chance = 1 - Math.pow(1 - CATCH_CHANCE_PER_SPY_AT_HOME, spies);
				for (Nation other : others(catching)) {
					for (Spy spy : other.spiesInPlace()) {
						if (spy.target().equals(catching.name()) && random.nextDouble() < chance) {
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
	 * has as the Turn resolved. The Nation that caught it is told in its Turn
	 * Report; what the sender may be told is added to those witnessed.
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
				.withBuildings(sender.buildings(), sender.defense(ruleset), turn));
			report.add(SPIES_AND_SCOUTS, "From its confession, " + sender.name() + " has " + sender.gold() + " Gold, "
					+ sender.population() + " Population and " + sender.research() + " Research; "
					+ armyFact(sender) + "; " + buildingsFact(sender) + ".");
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
	 * Applies an Event's or a Dilemma outcome's effects to a Nation, as luck
	 * varies them.
	 *
	 * @return what changed, such as "your Nation gained 4 Population"
	 */
	private List<String> befall(Event.Effects authored, Nation nation) {
		Event.Effects effects = authored.varied(random);
		List<String> changes = new ArrayList<>();
		Nation.Change change = nation.change(effects.gold(), effects.population(), effects.research());
		Map<String, Integer> changed = new LinkedHashMap<>();
		changed.put("Gold", change.gold());
		changed.put("Population", change.population());
		changed.put("Research", change.research());
		List<String> gained = new ArrayList<>();
		List<String> lost = new ArrayList<>();
		changed.forEach((resource, amount) -> {
			if (amount > 0) {
				gained.add(amount + " " + resource);
			}
			else if (amount < 0) {
				lost.add(-amount + " " + resource);
			}
		});
		List<String> resources = new ArrayList<>();
		if (!gained.isEmpty()) {
			resources.add("gained " + joined(gained));
		}
		if (!lost.isEmpty()) {
			resources.add("lost " + joined(lost));
		}
		if (!resources.isEmpty()) {
			changes.add("your Nation " + String.join(" and ", resources));
		}
		if (effects.damageDefense() > 0) {
			Nation.DefenseWorn defenseWorn = nation.wearDefense(effects.damageDefense(), ruleset);
			if (defenseWorn.worn() > 0) {
				changes.add("your Defense was worn down from " + defenseWorn.before() + " to " + defenseWorn.after()
						+ ", " + buildingsHarmed(defenseWorn));
			}
		}
		return changes;
	}

	/**
	 * Such as "your Warparty of Warrior ×2 heading to Carthage will now arrive
	 * in 3 Turns".
	 */
	private static String delayed(Warparty before, Warparty after) {
		String turns = after.turnsLeft() + (after.turnsLeft() == 1 ? " Turn" : " Turns");
		return "your Warparty of " + listed(before.units())
				+ (before.homeward() ? " heading home from " + before.target() + " will now get home in " + turns
						: " heading to " + before.target() + " will now arrive in " + turns);
	}

	/**
	 * An Event's or a Dilemma outcome's line in the Turn Report: its text, then
	 * what it changed.
	 */
	private static String announced(String text, List<String> changes) {
		return text + (changes.isEmpty() ? "." : ": " + String.join(" and ", changes) + ".");
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
			case Action.RecallWarparty recall -> recall(actor, recall.warparty());
			case Action.OfferTribute offer -> offer(actor, offer);
			case Action.AnswerTribute answer -> answer(actor, answer);
			case Action.AnswerEncounter answer -> answer(actor, answer);
			case Action.AnswerDilemma answer -> answer(actor, answer);
			case Action.UnlockTechnology unlock -> ruleset.technology(unlock.technology())
				.map(actor::unlock)
				.orElseGet(() -> ActionResult.rejected("There is no Technology called " + unlock.technology() + "."));
		};
	}

	/**
	 * Why a Warparty or Spy cannot be sent from a Nation to a target, if it
	 * cannot: the target must be another Nation still in the Game.
	 *
	 * @param sent what is being sent, such as "A Spy"
	 */
	private Optional<ActionResult> unreachable(String nation, String target, String sent) {
		if (target.equals(nation)) {
			return Optional.of(ActionResult.rejected(sent + " must be sent to another Nation."));
		}
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
			pendingReport(attacker)
				.add(WARFARE, "You refused " + defender.ruled() + "'s Tribute of " + tribute.described() + ".");
			pendingReport(defender).add(WARFARE, attacker.ruled() + " refused your Tribute of " + tribute.described() + ".");
			return ActionResult.ACCEPTED;
		}
		if (!defender.canAfford(tribute.gold(), tribute.population())) {
			return ActionResult.rejected(
					defender.name() + " can no longer afford its Tribute of " + tribute.described() + ".");
		}
		attacker.recall(warparty.get());
		defender.gain(-tribute.gold(), -tribute.population(), 0);
		attacker.gain(tribute.gold(), tribute.population(), 0);
		defender.changed();
		tributes.set(tributes.indexOf(tribute), tribute.answered(Tribute.Answer.ACCEPTED));
		pendingReport(attacker).add(WARFARE, "You accepted " + defender.ruled() + "'s Tribute of "
				+ tribute.described() + ", and your Warparty of " + listed(warparty.get().units())
				+ " turned back home.");
		pendingReport(defender).add(WARFARE, attacker.ruled() + " accepted your Tribute of " + tribute.described()
				+ ", and its Warparty turned back home before the Battle.");
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

	private static Optional<String> buildingsAddedLine(int population, int gold, int research) {
		List<String> added = new ArrayList<>();
		if (population > 0) {
			added.add(population + " Population");
		}
		if (gold > 0) {
			added.add(gold + " Gold");
		}
		if (research > 0) {
			added.add(research + " Research");
		}
		return added.isEmpty() ? Optional.empty() : Optional.of("Your Buildings added " + joined(added) + ".");
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
		return own.view(turn, map, arrivals, involved, met, ruleset, winner);
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
