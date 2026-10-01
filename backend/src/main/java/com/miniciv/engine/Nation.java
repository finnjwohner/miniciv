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

	NationView view(int turn, List<String> otherNations, List<BuildingType> availableBuildings) {
		return new NationView(version, turn, name, gold, population, research, otherNations, availableBuildings,
				Collections.unmodifiableMap(new LinkedHashMap<>(buildings)), construction, turnReport);
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
