package com.miniciv.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
	 * The Units in training, in the order started.
	 */
	private final List<Training> training = new ArrayList<>();

	/**
	 * How many of each Unit type the Nation has at home, in the order first
	 * trained.
	 */
	private final Map<String, Integer> army = new LinkedHashMap<>();

	private TurnReport turnReport;

	private int version = 1;

	Nation(String name, int gold, int population, int research) {
		this.name = name;
		this.gold = gold;
		this.population = population;
		this.research = research;
	}

	/**
	 * Gives the Nation the report of the Turn that just resolved.
	 */
	void turnReport(TurnReport turnReport) {
		this.turnReport = turnReport;
		version++;
	}

	NationView view(int turn, List<String> otherNations, Ruleset ruleset) {
		return new NationView(version, turn, name, gold, population, research, otherNations, ruleset.buildings(),
				Collections.unmodifiableMap(new LinkedHashMap<>(buildings)), construction, ruleset.trainableUnits(),
				Collections.unmodifiableMap(new LinkedHashMap<>(army)), training, upkeep(ruleset), turnReport);
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
	 * The Gold the Army costs each Turn.
	 */
	int upkeep(Ruleset ruleset) {
		return army.entrySet()
			.stream()
			.mapToInt(owned -> ruleset.unit(owned.getKey()).orElseThrow().upkeep() * owned.getValue())
			.sum();
	}

	/**
	 * Lets Units desert, one at a time, until the Nation's Gold covers the
	 * Army's upkeep. The Unit costing the most upkeep deserts first; between
	 * equally costly types, the type listed last in the Army, that is, the one
	 * first trained most recently.
	 *
	 * @return how many of each Unit type deserted, in the order they deserted
	 */
	Map<String, Integer> desertUntilUpkeepIsAffordable(Ruleset ruleset) {
		Map<String, Integer> deserted = new LinkedHashMap<>();
		while (upkeep(ruleset) > gold) {
			String deserter = null;
			int highest = -1;
			for (String unit : army.keySet()) {
				int upkeep = ruleset.unit(unit).orElseThrow().upkeep();
				if (upkeep >= highest) {
					deserter = unit;
					highest = upkeep;
				}
			}
			army.computeIfPresent(deserter, (unit, count) -> count == 1 ? null : count - 1);
			deserted.merge(deserter, 1, Integer::sum);
		}
		return deserted;
	}

	/**
	 * Pays the Army's upkeep, which the Nation must be able to afford.
	 *
	 * @return the Gold paid
	 */
	int payUpkeep(Ruleset ruleset) {
		int upkeep = upkeep(ruleset);
		gold -= upkeep;
		return upkeep;
	}

	/**
	 * Population the Nation's completed Buildings add this Turn.
	 */
	int populationFromBuildings(Ruleset ruleset) {
		return buildings.entrySet()
			.stream()
			.mapToInt(owned -> ruleset.building(owned.getKey()).orElseThrow().populationPerTurn() * owned.getValue())
			.sum();
	}

	/**
	 * Gold the Nation's completed Buildings add this Turn for its current
	 * Population, rounded down.
	 */
	int goldFromBuildings(Ruleset ruleset) {
		BigDecimal goldPerPopulation = buildings.entrySet()
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

}
