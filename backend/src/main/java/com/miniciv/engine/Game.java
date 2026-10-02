package com.miniciv.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

/**
 * One match of miniciv. The engine knows only Nations and Turns: no time,
 * sockets or Players. Everything random draws from the seeded random source,
 * so a Game replays identically from the same seed.
 */
public class Game {

	private static final int BASE_GOLD = 50;

	private static final int BASE_POPULATION = 10;

	private static final int BASE_RESEARCH = 0;

	private static final int GOLD_PER_TURN = 10;

	private static final int POPULATION_PER_TURN = 2;

	private static final int RESEARCH_PER_TURN = 3;

	/**
	 * How many Turns a Warparty takes to reach another Nation, and to come
	 * back.
	 */
	private static final int TRAVEL_TURNS = 3;

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
	 * What each Nation still in the Game is to be told of removals in its next
	 * Turn Report.
	 */
	private final Map<String, List<String>> removalReports = new LinkedHashMap<>();

	/**
	 * The last Nation standing, or null while the Game goes on.
	 */
	private String winner;

	public Game(Ruleset ruleset, List<String> nations, RandomGenerator random) {
		this.ruleset = ruleset;
		for (String name : nations) {
			this.nations.put(name, new Nation(name, BASE_GOLD, BASE_POPULATION, BASE_RESEARCH));
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
	 * Warparties abroad are gone and every Warparty heading to it turns back
	 * home. The other Nations are told in their next Turn Report, and if only
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
		List<Nation> left = remaining();
		for (Nation other : left) {
			List<String> report = removalReports.computeIfAbsent(other.name(), n -> new ArrayList<>());
			report.add(name + " was removed from the Game, as its Player was disconnected for too long.");
			for (Warparty back : other.turnBackFrom(name, TRAVEL_TURNS)) {
				report.add("Your Warparty of " + listed(back.units()) + " heading to " + name
						+ " turned back home, as " + name + " was removed from the Game.");
			}
			other.changed();
		}
		if (left.size() == 1) {
			winner = left.getFirst().name();
			removalReports.clear();
		}
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
		Map<String, List<String>> reports = new LinkedHashMap<>();
		inPlay.forEach(nation -> reports.put(nation.name(),
				new ArrayList<>(removalReports.getOrDefault(nation.name(), List.of()))));
		removalReports.clear();
		for (Nation nation : inPlay) {
			List<String> report = reports.get(nation.name());
			for (String completed : nation.progressConstruction()) {
				report.add("Your " + completed + " has been built.");
			}
			for (Repair finished : nation.progressRepairs()) {
				report.add("Your " + finished.building() + " has been " + (finished.rebuild() ? "rebuilt." : "repaired."));
			}
			Map<String, Integer> trained = nation.progressTraining(turn + 1);
			if (!trained.isEmpty()) {
				report.add(listed(trained) + " finished training and joined your Army.");
			}
		}
		for (Nation nation : inPlay) {
			for (Warparty returned : nation.travelHome()) {
				String plunder = returned.plunder().isNone() ? "" : " with " + returned.plunder().described() + ",";
				reports.get(nation.name())
					.add("Your Warparty came home from " + returned.target() + plunder + " and "
							+ listed(returned.units()) + " rejoined your Army.");
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
					.add("Your Warparty of " + listed(arrived.units()) + " arrived at " + arrived.target()
							+ "; the Battle will be fought as this Turn ends.");
				reports.get(arrived.target())
					.add("A Warparty from " + nation.name()
							+ " arrived at your Nation; the Battle will be fought as this Turn ends.");
			}
		}
		for (Nation nation : remaining()) {
			List<String> report = reports.get(nation.name());
			int buildingPopulation = nation.populationFromBuildings(ruleset);
			int buildingGold = nation.goldFromBuildings(ruleset);
			nation.gain(GOLD_PER_TURN + buildingGold, POPULATION_PER_TURN + buildingPopulation, RESEARCH_PER_TURN);
			report.add("Your Nation gained " + GOLD_PER_TURN + " Gold, " + POPULATION_PER_TURN + " Population and "
					+ RESEARCH_PER_TURN + " Research.");
			buildingsAddedLine(buildingPopulation, buildingGold).ifPresent(report::add);
			for (Nation.Desertion desertion : nation.desertUntilUpkeepIsAffordable(ruleset)) {
				String from = desertion.warparty() == null ? "your Army"
						: "your Warparty sent to " + desertion.warparty().target();
				report.add("You could not pay your Units' upkeep, so " + listed(desertion.units()) + " deserted from "
						+ from + ".");
			}
			int upkeep = nation.payUpkeep(ruleset);
			if (upkeep > 0) {
				report.add("Your Units' upkeep cost " + upkeep + " Gold.");
			}
		}
		rollEvents(reports);
		List<Nation> left = remaining();
		if (left.size() == 1) {
			winner = left.getFirst().name();
			nations.values().forEach(Nation::changed);
			reports.forEach((nation, report) -> report
				.add(nation.equals(winner) ? "You have won the Game." : winner + " has won the Game."));
		}
		inPlay.forEach(nation -> nation.turnReport(new TurnReport(turn, reports.get(nation.name()))));
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
					conquered ? Plunder.NONE : shares.get(i), TRAVEL_TURNS);
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
		for (Nation nation : remaining()) {
			List<Warparty> turned = nation.turnBackFrom(conquered.name(), TRAVEL_TURNS);
			if (!turned.isEmpty()) {
				turnedBack.put(nation.name(), turned);
			}
		}
		return new Nation.Conquest(seized, turnedBack);
	}

	private void reportBattle(BattleFought fought, Map<String, List<String>> reports) {
		Battle battle = fought.battle();
		List<Arrival> arrivals = fought.arrivals();
		String defender = fought.defender().name();
		List<String> defenderReport = reports.get(defender);
		String army = battle.army().isEmpty() ? null : listed(battle.army());
		List<String> sides = new ArrayList<>();
		List<String> losses = new ArrayList<>(List.of("You lost " + lost(battle.armyLost())));
		for (int i = 0; i < arrivals.size(); i++) {
			String attacker = arrivals.get(i).attacker().name();
			String warparty = listed(battle.warparties().get(i));
			sides.add(attacker + "'s Warparty of " + warparty);
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
				.add(army == null
						? "Your Warparty of " + warparty + " met no Army at " + defender
								+ (allies.isEmpty() ? "" : "," + allies) + "."
						: "Your Warparty of " + warparty + " fought " + defender + "'s Army of " + army + allies
								+ ". You lost " + lost(battle.warpartiesLost().get(i)) + " and " + defender + " lost "
								+ lost(battle.armyLost()) + ".");
		}
		defenderReport.add(army == null ? joined(sides) + " met no Army at your Nation."
				: "Your Army of " + army + " fought " + joined(sides) + ". " + joined(losses) + ".");
		for (int i = 0; i < arrivals.size(); i++) {
			if (!fought.survived(i)) {
				String attacker = arrivals.get(i).attacker().name();
				reports.get(attacker).add("Your Warparty sent to " + defender + " was destroyed.");
				defenderReport.add(attacker + "'s Warparty was destroyed.");
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
				.add(warpartiesFrom(attacker, survivors) + " wore " + defender + "'s Defense down by "
						+ defenseWorn.worn() + ", " + buildingsHarmed(defenseWorn) + "."));
			defenderReport.add(warpartiesOf(survivors) + " wore your Defense down from " + defenseWorn.before()
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
				.add(warpartiesFrom(attacker, survivors) + " reduced " + defender + "'s Population by "
						+ fought.populationLost() + attackerReason + "."));
			defenderReport.add(warpartiesOf(survivors) + " reduced your Population by " + fought.populationLost()
					+ defenderReason + ".");
		}
		Nation.Conquest conquest = fought.conquest();
		if (conquest != null) {
			String seized = conquest.seized().described();
			if (survivors.size() == 1) {
				reports.get(attackers.getFirst())
					.add("Your Warparty conquered " + defender + ", seizing " + seized + ", and is heading home.");
			}
			else {
				for (String attacker : attackers) {
					reports.get(attacker).add(warpartiesFrom(attacker, survivors) + " conquered " + defender + ".");
					for (int i = 0; i < arrivals.size(); i++) {
						if (fought.survived(i) && arrivals.get(i).attacker().name().equals(attacker)) {
							reports.get(attacker)
								.add("Your Warparty of " + listed(battle.warparties().get(i)) + " seized "
										+ fought.shares().get(i).described() + " as its share and is heading home.");
						}
					}
				}
			}
			defenderReport.add(warpartiesOf(survivors) + " conquered your Nation, seizing " + seized
					+ ". You are knocked out of the Game.");
			reports.forEach((nation, report) -> {
				if (!attackers.contains(nation) && !nation(nation).conquered()) {
					report.add(joined(attackers) + " conquered " + defender + ".");
				}
			});
			conquest.turnedBack()
				.forEach((nation, turned) -> turned.forEach(back -> reports.get(nation)
					.add("Your Warparty of " + listed(back.units()) + " heading to " + defender
							+ " turned back home, as " + defender + " was conquered.")));
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
				reports.get(attacker).add("Your Warparty took nothing from " + defender + " and is heading home.");
				defenderReport.add(attacker + "'s Warparty took nothing from you and is heading home.");
			}
			else {
				reports.get(attacker)
					.add("Your Warparty took " + plunder.described() + " from " + defender + share
							+ " and is heading home.");
				defenderReport
					.add(attacker + "'s Warparty took " + plunder.described() + " from you and is heading home.");
			}
		}
	}

	/**
	 * Such as "Rome's and Egypt's Warparties", for the Nation they attacked.
	 */
	private static String warpartiesOf(List<Arrival> arrivals) {
		List<String> nations = arrivals.stream().map(arrival -> arrival.attacker().name() + "'s").distinct().toList();
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
	 * source for each. One that happens takes effect at once and is announced
	 * in the Turn Report of the Nation it happened to, or that sent the
	 * Warparty.
	 */
	private void rollEvents(Map<String, List<String>> reports) {
		for (Event event : ruleset.events()) {
			for (Nation nation : remaining()) {
				List<String> report = reports.get(nation.name());
				switch (event.subject()) {
					case NATION -> {
						if (event.when().holdForNation(turn, nation.population(), largestApproaching(nation))
								&& happens(event)) {
							report.add(announced(event, befall(event, nation)));
						}
					}
					case WARPARTY -> {
						for (Warparty warparty : nation.warparties()) {
							if (!warparty.awaitingBattle() && event.when().holdForWarparty(turn, warparty)
									&& happens(event)) {
								report.add(announced(event,
										List.of(delayed(warparty, nation.delay(warparty, event.effects().travelTurns())))));
							}
						}
					}
				}
			}
		}
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
	 * Applies an Event's effects to a Nation.
	 *
	 * @return what changed, such as "your Nation gained 4 Population"
	 */
	private List<String> befall(Event event, Nation nation) {
		Event.Effects effects = event.effects();
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
	 * An Event's line in the Turn Report: its text, then what it changed.
	 */
	private static String announced(Event event, List<String> changes) {
		return event.text() + (changes.isEmpty() ? "." : ": " + String.join(" and ", changes) + ".");
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
				.map(actor::startBuilding)
				.orElseGet(() -> noSuchBuilding(start.building()));
			case Action.RepairBuilding repair -> ruleset.building(repair.building())
				.map(actor::repair)
				.orElseGet(() -> noSuchBuilding(repair.building()));
			case Action.RebuildBuilding rebuild -> ruleset.building(rebuild.building())
				.map(actor::rebuild)
				.orElseGet(() -> noSuchBuilding(rebuild.building()));
			case Action.TrainUnits train -> {
				if (train.count() < 1) {
					yield ActionResult.rejected("Train at least one Unit.");
				}
				yield ruleset.unit(train.unit())
					.map(unit -> actor.trainUnits(unit, train.count(), turn))
					.orElseGet(() -> ActionResult
						.rejected("There is no Unit called " + train.unit() + " that you can train."));
			}
			case Action.SendWarparty send -> {
				if (send.target().equals(nation)) {
					yield ActionResult.rejected("A Warparty must be sent to another Nation.");
				}
				if (!nations.containsKey(send.target())) {
					yield ActionResult.rejected("There is no Nation called " + send.target() + ".");
				}
				if (nation(send.target()).conquered()) {
					yield ActionResult.rejected(send.target() + " has been conquered.");
				}
				if (nation(send.target()).removed()) {
					yield ActionResult.rejected(send.target() + " has been removed from the Game.");
				}
				if (send.units().isEmpty()
						|| send.units().values().stream().anyMatch(count -> count == null || count < 1)) {
					yield ActionResult.rejected("Choose at least one Unit to send.");
				}
				yield actor.sendWarparty(send.target(), send.units(), TRAVEL_TURNS);
			}
		};
	}

	private static ActionResult noSuchBuilding(String building) {
		return ActionResult.rejected("There is no Building called " + building + ".");
	}

	private static Optional<String> buildingsAddedLine(int population, int gold) {
		List<String> added = new ArrayList<>();
		if (population > 0) {
			added.add(population + " Population");
		}
		if (gold > 0) {
			added.add(gold + " Gold");
		}
		return added.isEmpty() ? Optional.empty()
				: Optional.of("Your Buildings added " + String.join(" and ", added) + ".");
	}

	/**
	 * Units counted by type, such as "Warrior ×2 and Archer ×1".
	 */
	private static String listed(Map<String, Integer> units) {
		return joined(units.entrySet().stream().map(e -> e.getKey() + " ×" + e.getValue()).toList());
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
		List<MapNation> map = nations.values().stream().map(Nation::onMap).toList();
		return own.view(turn, map, TRAVEL_TURNS, ruleset, winner);
	}

	private Nation nation(String name) {
		Nation nation = nations.get(name);
		if (nation == null) {
			throw new IllegalArgumentException("There is no Nation called " + name + " in this Game");
		}
		return nation;
	}

}
