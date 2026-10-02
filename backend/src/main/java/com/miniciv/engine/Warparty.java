package com.miniciv.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Units a Nation has sent abroad: travelling to their target, waiting there
 * for the Battle, or heading home from it.
 *
 * @param id the number telling it apart from the Nation's other Warparties,
 * counting up in the order sent
 * @param target the Nation the Warparty was sent to
 * @param units how many of each Unit type it holds
 * @param homeward whether it has fought at its target and is heading home
 * @param turnsLeft how many more Turns must resolve before it arrives where it
 * is heading; 0 once it has arrived at its target and waits for the Battle
 * @param turnsTravelled how many Turns it travelled toward its target, delays
 * included
 * @param plunder what it took from its target and is carrying home
 */
public record Warparty(int id, String target, Map<String, Integer> units, boolean homeward, int turnsLeft,
		int turnsTravelled, Plunder plunder) {

	public Warparty {
		units = Collections.unmodifiableMap(new LinkedHashMap<>(units));
	}

	/**
	 * A Warparty carrying no plunder.
	 */
	public Warparty(int id, String target, Map<String, Integer> units, boolean homeward, int turnsLeft,
			int turnsTravelled) {
		this(id, target, units, homeward, turnsLeft, turnsTravelled, Plunder.NONE);
	}

	/**
	 * Whether it has arrived at its target and waits for the Battle.
	 */
	boolean awaitingBattle() {
		return !homeward && turnsLeft == 0;
	}

	/**
	 * Whether it is travelling to the given Nation and has not yet arrived.
	 */
	boolean approaching(String nation) {
		return !homeward && turnsLeft > 0 && target.equals(nation);
	}

	/**
	 * This Warparty one Turn closer to where it is heading.
	 */
	Warparty travelled() {
		return new Warparty(id, target, units, homeward, turnsLeft - 1, homeward ? turnsTravelled : turnsTravelled + 1, plunder);
	}

	/**
	 * This Warparty, having fought at its target, setting off home with its
	 * surviving Units and its plunder.
	 */
	Warparty headingHome(Map<String, Integer> survivors, Plunder plunder, int travelTurns) {
		return new Warparty(id, target, survivors, true, travelTurns, turnsTravelled, plunder);
	}

	/**
	 * This Warparty, before it has fought at its target, turning back home: the
	 * trip home takes as long as it has travelled so far, and at least one
	 * Turn.
	 */
	Warparty turnedBack() {
		return new Warparty(id, target, units, true, Math.max(1, turnsTravelled), turnsTravelled, plunder);
	}

	/**
	 * How many Units it holds, of every type.
	 */
	int unitCount() {
		return units.values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * This Warparty with its trip changed by the given number of Turns, still
	 * taking at least one more.
	 */
	Warparty delayed(int turns) {
		return new Warparty(id, target, units, homeward, Math.max(1, turnsLeft + turns), turnsTravelled, plunder);
	}

	/**
	 * This Warparty holding only the given Units.
	 */
	Warparty with(Map<String, Integer> units) {
		return new Warparty(id, target, units, homeward, turnsLeft, turnsTravelled, plunder);
	}

}
