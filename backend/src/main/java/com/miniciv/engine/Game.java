package com.miniciv.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

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

	private final Ruleset ruleset;

	private final Map<String, Nation> nations = new LinkedHashMap<>();

	private final RandomGenerator random;

	private int turn = 1;

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

	public List<String> nations() {
		return List.copyOf(nations.keySet());
	}

	/**
	 * Resolves the Turn for every Nation at once, then starts the next Turn.
	 */
	public void endTurn() {
		for (Nation nation : nations.values()) {
			List<String> report = new ArrayList<>();
			for (String completed : nation.progressConstruction()) {
				report.add("Your " + completed + " has been built.");
			}
			Map<String, Integer> trained = nation.progressTraining(turn + 1);
			if (!trained.isEmpty()) {
				report.add(listed(trained) + " finished training and joined your Army.");
			}
			int buildingPopulation = nation.populationFromBuildings(ruleset);
			int buildingGold = nation.goldFromBuildings(ruleset);
			nation.gain(GOLD_PER_TURN + buildingGold, POPULATION_PER_TURN + buildingPopulation, RESEARCH_PER_TURN);
			report.add("Your Nation gained " + GOLD_PER_TURN + " Gold, " + POPULATION_PER_TURN + " Population and "
					+ RESEARCH_PER_TURN + " Research.");
			buildingsAddedLine(buildingPopulation, buildingGold).ifPresent(report::add);
			Map<String, Integer> deserted = nation.desertUntilUpkeepIsAffordable(ruleset);
			if (!deserted.isEmpty()) {
				report.add("You could not pay your Army's upkeep, so " + listed(deserted) + " deserted.");
			}
			int upkeep = nation.payUpkeep(ruleset);
			if (upkeep > 0) {
				report.add("Your Army's upkeep cost " + upkeep + " Gold.");
			}
			nation.turnReport(new TurnReport(turn, report));
		}
		turn++;
	}

	/**
	 * Applies a Nation's Action at once, or rejects it with a reason for its
	 * Player.
	 */
	public ActionResult act(String nation, Action action) {
		Nation actor = nation(nation);
		return switch (action) {
			case Action.StartBuilding start -> ruleset.building(start.building())
				.map(actor::startBuilding)
				.orElseGet(() -> ActionResult.rejected("There is no Building called " + start.building() + "."));
			case Action.TrainUnits train -> {
				if (train.count() < 1) {
					yield ActionResult.rejected("Train at least one Unit.");
				}
				yield ruleset.trainableUnit(train.unit())
					.map(unit -> actor.trainUnits(unit, train.count(), turn))
					.orElseGet(() -> ActionResult
						.rejected("There is no Unit called " + train.unit() + " that you can train."));
			}
		};
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
		List<String> counted = units.entrySet().stream().map(e -> e.getKey() + " ×" + e.getValue()).toList();
		if (counted.size() == 1) {
			return counted.getFirst();
		}
		return String.join(", ", counted.subList(0, counted.size() - 1)) + " and " + counted.getLast();
	}

	public NationView viewOf(String nation) {
		Nation own = nation(nation);
		List<String> others = nations.keySet().stream().filter(name -> !name.equals(nation)).toList();
		return own.view(turn, others, ruleset);
	}

	private Nation nation(String name) {
		Nation nation = nations.get(name);
		if (nation == null) {
			throw new IllegalArgumentException("There is no Nation called " + name + " in this Game");
		}
		return nation;
	}

}
