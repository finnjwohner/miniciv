package com.miniciv.engine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

	private final Map<String, Nation> nations = new LinkedHashMap<>();

	private final RandomGenerator random;

	private int turn = 1;

	public Game(List<String> nations, RandomGenerator random) {
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
			nation.gain(GOLD_PER_TURN, POPULATION_PER_TURN, RESEARCH_PER_TURN);
			nation.turnReport(new TurnReport(turn, List.of("Your Nation gained " + GOLD_PER_TURN + " Gold, "
					+ POPULATION_PER_TURN + " Population and " + RESEARCH_PER_TURN + " Research.")));
		}
		turn++;
	}

	public NationView viewOf(String nation) {
		Nation own = nations.get(nation);
		if (own == null) {
			throw new IllegalArgumentException("There is no Nation called " + nation + " in this Game");
		}
		List<String> others = nations.keySet().stream().filter(name -> !name.equals(nation)).toList();
		return own.view(turn, others);
	}

}
