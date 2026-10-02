package com.miniciv.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A Nation's state inside the engine.
 */
class Nation {

	private final String name;

	private int gold;

	private int population;

	private int research;

	private final List<Construction> construction = new ArrayList<>();

	/**
	 * How many of each Building the Nation has completed, in the order first
	 * completed.
	 */
	private final Map<String, Integer> buildings = new LinkedHashMap<>();

	/**
	 * How much Defense each defensive Building type has lost; see
	 * {@link BuildingDamage}.
	 */
	private final Map<String, Integer> defenseLost = new LinkedHashMap<>();

	/**
	 * The defensive Buildings being repaired or rebuilt, in the order started.
	 */
	private final List<Repair> repairs = new ArrayList<>();

	/**
	 * The Units in training, in the order started.
	 */
	private final List<Training> training = new ArrayList<>();

	/**
	 * How many of each Unit type the Nation has at home, in the order first
	 * trained.
	 */
	private final Map<String, Integer> army = new LinkedHashMap<>();

	/**
	 * The Nation's Warparties abroad, in the order sent.
	 */
	private final List<Warparty> warparties = new ArrayList<>();

	private TurnReport turnReport;

	/**
	 * The Nation that conquered this one, or null while it is still in the
	 * Game.
	 */
	private String conqueredBy;

	private int version = 1;

	Nation(String name, int gold, int population, int research) {
		this.name = name;
		this.gold = gold;
		this.population = population;
		this.research = research;
	}

	String name() {
		return name;
	}

	/**
	 * Gives the Nation the report of the Turn that just resolved.
	 */
	void turnReport(TurnReport turnReport) {
		this.turnReport = turnReport;
		version++;
	}

	NationView view(int turn, List<String> otherNations, int travelTurns, Ruleset ruleset, String winner) {
		return new NationView(version, turn, name, gold, population, research, otherNations, ruleset.buildings(),
				Collections.unmodifiableMap(new LinkedHashMap<>(buildings)), construction, repairs, defense(ruleset), damage(ruleset),
				ruleset.units(),
				Collections.unmodifiableMap(new LinkedHashMap<>(army)), training, warparties, travelTurns, upkeep(ruleset),
				turnReport, conqueredBy, winner);
	}

	/**
	 * Marks a change the Nation's Player should see that is not to the Nation
	 * itself, such as the Game being won.
	 */
	void changed() {
		version++;
	}

	boolean conquered() {
		return conqueredBy != null;
	}

	/**
	 * Whether the Nation is left with neither Population nor Army, and so is
	 * conquered.
	 */
	boolean hasNeitherPopulationNorArmy() {
		return population == 0 && army.isEmpty();
	}

	/**
	 * Takes the Nation out of the Game: its Warparties abroad disappear, and
	 * its conqueror seizes a share of its Gold and Research, rounded down.
	 *
	 * @return the Gold and Research seized
	 */
	Plunder conquer(String conqueror, double share) {
		conqueredBy = conqueror;
		warparties.clear();
		return plundered((int) Math.floor(gold * share), (int) Math.floor(research * share));
	}

	/**
	 * Adds Gold and Research seized from a conquered Nation.
	 */
	void seize(Plunder seized) {
		gold += seized.gold();
		research += seized.research();
	}

	/**
	 * Turns every Warparty heading to a Nation, and not yet having fought
	 * there, back home.
	 *
	 * @return those turned back, as they were before
	 */
	List<Warparty> turnBackFrom(String target, int travelTurns) {
		List<Warparty> turnedBack = new ArrayList<>();
		for (var it = warparties.listIterator(); it.hasNext();) {
			Warparty current = it.next();
			if (!current.homeward() && current.target().equals(target)) {
				it.set(current.turnedBack(travelTurns));
				turnedBack.add(current);
			}
		}
		return turnedBack;
	}

	void gain(int gold, int population, int research) {
		this.gold += gold;
		this.population += population;
		this.research += research;
	}

	ActionResult startBuilding(BuildingType building) {
		if (building.gold() > gold) {
			return ActionResult
				.rejected(building.name() + " costs " + building.gold() + " Gold, but you have only " + gold + ".");
		}
		gold -= building.gold();
		construction.add(new Construction(building.name(), building.turns()));
		version++;
		return ActionResult.ACCEPTED;
	}

	/**
	 * Pays upfront to repair a defensive Building type's damaged Building: Gold
	 * and Turns in proportion to the Defense it lost, rounded up.
	 */
	ActionResult repair(BuildingType building) {
		int damaged = unrepaired(building) % Math.max(1, building.defense());
		if (damaged == 0) {
			return ActionResult.rejected("You have no damaged " + building.name() + " to repair.");
		}
		return startRepair(building, false, damaged, "Repairing ");
	}

	/**
	 * Pays upfront to rebuild one of a defensive Building type's destroyed
	 * Buildings, for the Gold and Turns constructing it costs.
	 */
	ActionResult rebuild(BuildingType building) {
		if (building.defense() == 0 || unrepaired(building) < building.defense()) {
			return ActionResult.rejected("You have no destroyed " + building.name() + " to rebuild.");
		}
		return startRepair(building, true, building.defense(), "Rebuilding ");
	}

	private ActionResult startRepair(BuildingType building, boolean rebuild, int defense, String doing) {
		int cost = proportion(building.gold(), defense, building);
		if (cost > gold) {
			return ActionResult
				.rejected(doing + building.name() + " costs " + cost + " Gold, but you have only " + gold + ".");
		}
		gold -= cost;
		repairs.add(new Repair(building.name(), rebuild, defense, proportion(building.turns(), defense, building)));
		version++;
		return ActionResult.ACCEPTED;
	}

	/**
	 * A Building's Gold or Turns in proportion to the share of its Defense
	 * given, rounded up.
	 */
	private static int proportion(int whole, int defense, BuildingType building) {
		return (int) Math.ceilDiv((long) whole * defense, building.defense());
	}

	/**
	 * The Defense a Building type has lost and is not yet being repaired or
	 * rebuilt for.
	 */
	private int unrepaired(BuildingType building) {
		int repairing = repairs.stream()
			.filter(r -> r.building().equals(building.name()))
			.mapToInt(Repair::defense)
			.sum();
		return defenseLost.getOrDefault(building.name(), 0) - repairing;
	}

	ActionResult trainUnits(UnitType unit, int count, int turn) {
		long totalGold = (long) unit.gold() * count;
		long totalPopulation = (long) unit.population() * count;
		String units = unit.name() + " ×" + count;
		if (totalGold > gold) {
			return ActionResult.rejected(units + " costs " + totalGold + " Gold, but you have only " + gold + ".");
		}
		if (totalPopulation > population) {
			return ActionResult
				.rejected(units + " costs " + totalPopulation + " Population, but you have only " + population + ".");
		}
		gold -= (int) totalGold;
		population -= (int) totalPopulation;
		training.add(new Training(unit.name(), count, turn + unit.turns()));
		version++;
		return ActionResult.ACCEPTED;
	}

	/**
	 * Takes Units out of the Army and sets them travelling to another Nation.
	 *
	 * @param units how many of each Unit type to send
	 */
	ActionResult sendWarparty(String target, Map<String, Integer> units, int travelTurns) {
		for (Map.Entry<String, Integer> chosen : units.entrySet()) {
			int owned = army.getOrDefault(chosen.getKey(), 0);
			if (owned == 0) {
				return ActionResult.rejected("Your Army has no " + chosen.getKey() + ".");
			}
			if (chosen.getValue() > owned) {
				return ActionResult.rejected("Your Army has only " + owned + " " + chosen.getKey() + ".");
			}
		}
		Map<String, Integer> sent = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> owned : army.entrySet()) {
			int count = units.getOrDefault(owned.getKey(), 0);
			if (count > 0) {
				sent.put(owned.getKey(), count);
			}
		}
		sent.forEach((unit, count) -> army.computeIfPresent(unit, (u, owned) -> owned == count ? null : owned - count));
		warparties.add(new Warparty(target, sent, false, travelTurns));
		version++;
		return ActionResult.ACCEPTED;
	}

	/**
	 * Moves every Warparty heading home one Turn on; one reaching home rejoins
	 * the Army and hands over its plunder.
	 *
	 * @return those that came home, as they were when they got there
	 */
	List<Warparty> travelHome() {
		List<Warparty> returned = new ArrayList<>();
		for (var it = warparties.listIterator(); it.hasNext();) {
			Warparty current = it.next();
			if (!current.homeward()) {
				continue;
			}
			if (current.turnsLeft() > 1) {
				it.set(current.travelled());
			}
			else {
				it.remove();
				current.units().forEach((unit, count) -> army.merge(unit, count, Integer::sum));
				gold += current.plunder().gold();
				research += current.plunder().research();
				returned.add(current);
			}
		}
		return returned;
	}

	/**
	 * Moves every Warparty travelling to its target one Turn on; one reaching
	 * its target waits there for the Battle.
	 *
	 * @return those that arrived at their target
	 */
	List<Warparty> travelOut() {
		List<Warparty> arrived = new ArrayList<>();
		for (var it = warparties.listIterator(); it.hasNext();) {
			Warparty current = it.next();
			if (current.homeward() || current.awaitingBattle()) {
				continue;
			}
			it.set(current.travelled());
			if (current.turnsLeft() == 1) {
				arrived.add(current);
			}
		}
		return arrived;
	}

	/**
	 * A Battle a Warparty fought, the plunder it took, and the Defense and
	 * Population its target lost.
	 *
	 * @param warparty the Warparty as it was before the Battle
	 * @param exposure how exposed the target's Population was
	 * @param conquest how the target was conquered, or null if it was not
	 */
	record BattleFought(Warparty warparty, Battle battle, Plunder plunder, DefenseWorn defenseWorn,
			int populationLost, Exposure exposure, Conquest conquest) {
	}

	/**
	 * What a Nation's conqueror seized from it, and the other Warparties heading
	 * to it that turned back home.
	 *
	 * @param turnedBack those Warparties as they were before, by the Nation
	 * that sent them
	 */
	record Conquest(Plunder seized, Map<String, List<Warparty>> turnedBack) {
	}

	/**
	 * Fights the Battle of every Warparty awaiting one, in the order sent. Its
	 * survivors head home with their plunder; a Warparty with none disappears.
	 * A Battle that conquers its target may turn this Nation's later
	 * Warparties back home before their turn comes, so they fight no Battle.
	 *
	 * @param fight fights a Warparty's Battle at its target and takes its
	 * plunder
	 */
	List<BattleFought> fightBattles(Function<Warparty, BattleFought> fight, int travelTurns) {
		List<BattleFought> fought = new ArrayList<>();
		for (var it = warparties.listIterator(); it.hasNext();) {
			Warparty current = it.next();
			if (!current.awaitingBattle()) {
				continue;
			}
			BattleFought battle = fight.apply(current);
			Map<String, Integer> survivors = battle.battle().warpartySurvivors();
			if (survivors.isEmpty()) {
				it.remove();
			}
			else {
				it.set(current.headingHome(survivors, battle.plunder(), travelTurns));
			}
			fought.add(battle);
		}
		return fought;
	}

	Map<String, Integer> army() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(army));
	}

	/**
	 * Removes Units the Army lost.
	 */
	void lose(Map<String, Integer> units) {
		units.forEach((unit, count) -> army.computeIfPresent(unit, (u, owned) -> owned <= count ? null : owned - count));
	}

	/**
	 * The Nation's Defense: what its defensive Buildings raise it by, less the
	 * Defense they have lost.
	 */
	int defense(Ruleset ruleset) {
		int raised = buildings.entrySet()
			.stream()
			.mapToInt(owned -> ruleset.building(owned.getKey()).orElseThrow().defense() * owned.getValue())
			.sum();
		return raised - defenseLost.values().stream().mapToInt(Integer::intValue).sum();
	}

	private List<BuildingDamage> damage(Ruleset ruleset) {
		List<BuildingDamage> damage = new ArrayList<>();
		for (String building : buildings.keySet()) {
			BuildingType type = ruleset.building(building).orElseThrow();
			int unrepaired = unrepaired(type);
			if (unrepaired > 0) {
				int damaged = unrepaired % type.defense();
				damage.add(new BuildingDamage(building, unrepaired / type.defense(), damaged,
						proportion(type.gold(), damaged, type), proportion(type.turns(), damaged, type)));
			}
		}
		return damage;
	}

	/**
	 * How far a Warparty wore a Nation's Defense down.
	 *
	 * @param before the Nation's Defense before
	 * @param after the Nation's Defense after
	 * @param destroyed how many of each defensive Building type it destroyed
	 * @param damaged the defensive Building types it left with a damaged
	 * Building
	 */
	record DefenseWorn(int before, int after, Map<String, Integer> destroyed, List<String> damaged) {

		int worn() {
			return before - after;
		}

	}

	/**
	 * Wears the Nation's Defense down by up to the given amount, from its
	 * defensive Buildings by type in the order first completed.
	 */
	DefenseWorn wearDefense(int amount, Ruleset ruleset) {
		int before = defense(ruleset);
		int left = amount;
		Map<String, Integer> destroyed = new LinkedHashMap<>();
		List<String> damaged = new ArrayList<>();
		for (Map.Entry<String, Integer> owned : buildings.entrySet()) {
			String building = owned.getKey();
			int defense = ruleset.building(building).orElseThrow().defense();
			int lost = defenseLost.getOrDefault(building, 0);
			int worn = Math.min(left, defense * owned.getValue() - lost);
			if (worn == 0) {
				continue;
			}
			left -= worn;
			defenseLost.put(building, lost + worn);
			int newlyDestroyed = (lost + worn) / defense - lost / defense;
			if (newlyDestroyed > 0) {
				destroyed.put(building, newlyDestroyed);
			}
			if ((lost + worn) % defense != 0) {
				damaged.add(building);
			}
		}
		return new DefenseWorn(before, before - (amount - left), destroyed, damaged);
	}

	/**
	 * Loses Population to a Warparty, as much as the Nation has.
	 *
	 * @return the Population lost
	 */
	int losePopulation(int amount) {
		int lost = Math.min(amount, population);
		population -= lost;
		return lost;
	}

	/**
	 * Gives up Gold and Research to plunderers, as much as the Nation has.
	 *
	 * @return the Gold and Research taken
	 */
	Plunder plundered(int gold, int research) {
		int goldTaken = Math.min(gold, this.gold);
		int researchTaken = Math.min(research, this.research);
		this.gold -= goldTaken;
		this.research -= researchTaken;
		return new Plunder(goldTaken, researchTaken);
	}

	/**
	 * Moves the Units ready by the start of a Turn from training into the Army.
	 *
	 * @return how many of each Unit type joined, in the order started
	 */
	Map<String, Integer> progressTraining(int nextTurn) {
		Map<String, Integer> joined = new LinkedHashMap<>();
		for (var it = training.iterator(); it.hasNext();) {
			Training current = it.next();
			if (current.readyOnTurn() == nextTurn) {
				it.remove();
				army.merge(current.unit(), current.count(), Integer::sum);
				joined.merge(current.unit(), current.count(), Integer::sum);
			}
		}
		return joined;
	}

	/**
	 * The Gold the Nation's Units cost each Turn, at home and abroad.
	 */
	int upkeep(Ruleset ruleset) {
		return upkeepOf(army, ruleset) + warparties.stream().mapToInt(w -> upkeepOf(w.units(), ruleset)).sum();
	}

	private static int upkeepOf(Map<String, Integer> units, Ruleset ruleset) {
		return units.entrySet()
			.stream()
			.mapToInt(owned -> ruleset.unit(owned.getKey()).orElseThrow().upkeep() * owned.getValue())
			.sum();
	}

	/**
	 * Units that deserted as a Turn resolved, from the Army or from one
	 * Warparty.
	 *
	 * @param warparty the Warparty they left, as it was before, or null if they
	 * left the Army
	 * @param units how many of each Unit type deserted, in the order they
	 * deserted
	 */
	record Desertion(Warparty warparty, Map<String, Integer> units) {
	}

	/**
	 * Lets Units desert, one at a time, until the Nation's Gold covers its
	 * Units' upkeep. They desert from the Army and from Warparties abroad
	 * alike. The Unit costing the most upkeep deserts first; between equally
	 * costly ones, the one listed last, taking the Army's types in the order
	 * first trained and then each Warparty's in the order sent. A Warparty left
	 * with no Units disappears.
	 *
	 * @return the desertions from the Army first, then from each Warparty in
	 * the order sent
	 */
	List<Desertion> desertUntilUpkeepIsAffordable(Ruleset ruleset) {
		List<Map<String, Integer>> groups = new ArrayList<>();
		groups.add(army);
		warparties.forEach(w -> groups.add(new LinkedHashMap<>(w.units())));
		List<Map<String, Integer>> deserted = new ArrayList<>();
		groups.forEach(g -> deserted.add(new LinkedHashMap<>()));
		while (groups.stream().mapToInt(g -> upkeepOf(g, ruleset)).sum() > gold) {
			int group = -1;
			String deserter = null;
			int highest = -1;
			for (int i = 0; i < groups.size(); i++) {
				for (String unit : groups.get(i).keySet()) {
					int upkeep = ruleset.unit(unit).orElseThrow().upkeep();
					if (upkeep >= highest) {
						group = i;
						deserter = unit;
						highest = upkeep;
					}
				}
			}
			groups.get(group).computeIfPresent(deserter, (unit, count) -> count == 1 ? null : count - 1);
			deserted.get(group).merge(deserter, 1, Integer::sum);
		}
		List<Desertion> desertions = new ArrayList<>();
		if (!deserted.getFirst().isEmpty()) {
			desertions.add(new Desertion(null, deserted.getFirst()));
		}
		List<Warparty> before = List.copyOf(warparties);
		warparties.clear();
		for (int i = 0; i < before.size(); i++) {
			Warparty warparty = before.get(i);
			if (!deserted.get(i + 1).isEmpty()) {
				desertions.add(new Desertion(warparty, deserted.get(i + 1)));
			}
			if (!groups.get(i + 1).isEmpty()) {
				warparties.add(warparty.with(groups.get(i + 1)));
			}
		}
		return desertions;
	}

	/**
	 * Pays its Units' upkeep, which the Nation must be able to afford.
	 *
	 * @return the Gold paid
	 */
	int payUpkeep(Ruleset ruleset) {
		int upkeep = upkeep(ruleset);
		gold -= upkeep;
		return upkeep;
	}

	/**
	 * How many of each completed Building are standing: not destroyed.
	 */
	private Map<String, Integer> standing(Ruleset ruleset) {
		Map<String, Integer> standing = new LinkedHashMap<>(buildings);
		defenseLost.forEach((building, lost) -> standing.computeIfPresent(building,
				(b, count) -> count - lost / ruleset.building(b).orElseThrow().defense()));
		return standing;
	}

	/**
	 * Population the Nation's standing Buildings add this Turn.
	 */
	int populationFromBuildings(Ruleset ruleset) {
		return standing(ruleset).entrySet()
			.stream()
			.mapToInt(owned -> ruleset.building(owned.getKey()).orElseThrow().populationPerTurn() * owned.getValue())
			.sum();
	}

	/**
	 * Gold the Nation's standing Buildings add this Turn for its current
	 * Population, rounded down.
	 */
	int goldFromBuildings(Ruleset ruleset) {
		BigDecimal goldPerPopulation = standing(ruleset).entrySet()
			.stream()
			.map(owned -> BigDecimal.valueOf(ruleset.building(owned.getKey()).orElseThrow().goldPerPopulation())
				.multiply(BigDecimal.valueOf(owned.getValue())))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		return goldPerPopulation.multiply(BigDecimal.valueOf(population)).setScale(0, RoundingMode.FLOOR).intValueExact();
	}

	/**
	 * Moves every Building under construction one Turn on.
	 *
	 * @return the Buildings completed, in the order started
	 */
	List<String> progressConstruction() {
		List<String> completed = new ArrayList<>();
		for (var it = construction.listIterator(); it.hasNext();) {
			Construction current = it.next();
			if (current.turnsLeft() == 1) {
				it.remove();
				buildings.merge(current.building(), 1, Integer::sum);
				completed.add(current.building());
			}
			else {
				it.set(new Construction(current.building(), current.turnsLeft() - 1));
			}
		}
		return completed;
	}

	/**
	 * Moves every repair one Turn on. A finished one restores the Defense it
	 * was paid for, or as much as its Building type has lost by then.
	 *
	 * @return the repairs finished, in the order started
	 */
	List<Repair> progressRepairs() {
		List<Repair> finished = new ArrayList<>();
		for (var it = repairs.listIterator(); it.hasNext();) {
			Repair current = it.next();
			if (current.turnsLeft() == 1) {
				it.remove();
				defenseLost.computeIfPresent(current.building(),
						(building, lost) -> lost <= current.defense() ? null : lost - current.defense());
				finished.add(current);
			}
			else {
				it.set(new Repair(current.building(), current.rebuild(), current.defense(), current.turnsLeft() - 1));
			}
		}
		return finished;
	}

}
