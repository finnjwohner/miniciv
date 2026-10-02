package com.miniciv.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Units a Nation has sent abroad: travelling to their target, waiting there
 * for the Battle, or heading home from it.
 *
 * @param target the Nation the Warparty was sent to
 * @param units how many of each Unit type it holds
 * @param homeward whether it has fought at its target and is heading home
 * @param turnsLeft how many more Turns must resolve before it arrives where it
 * is heading; 0 once it has arrived at its target and waits for the Battle
 * @param plunder what it took from its target and is carrying home
 */
public record Warparty(String target, Map<String, Integer> units, boolean homeward, int turnsLeft,
		Plunder plunder) {

	public Warparty {
		units = Collections.unmodifiableMap(new LinkedHashMap<>(units));
	}

	/**
	 * A Warparty carrying no plunder.
	 */
	public Warparty(String target, Map<String, Integer> units, boolean homeward, int turnsLeft) {
		this(target, units, homeward, turnsLeft, Plunder.NONE);
	}

	/**
	 * Whether it has arrived at its target and waits for the Battle.
	 */
	boolean awaitingBattle() {
		return !homeward && turnsLeft == 0;
	}

	/**
	 * This Warparty one Turn closer to where it is heading.
	 */
	Warparty travelled() {
		return new Warparty(target, units, homeward, turnsLeft - 1, plunder);
	}

	/**
	 * This Warparty, having fought at its target, setting off home with its
	 * surviving Units and its plunder.
	 */
	Warparty headingHome(Map<String, Integer> survivors, Plunder plunder, int travelTurns) {
		return new Warparty(target, survivors, true, travelTurns, plunder);
	}

	/**
	 * This Warparty holding only the given Units.
	 */
	Warparty with(Map<String, Integer> units) {
		return new Warparty(target, units, homeward, turnsLeft, plunder);
	}

}
