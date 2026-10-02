package com.miniciv.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Units a Nation has sent abroad: travelling to their target, or heading home
 * from it.
 *
 * @param target the Nation the Warparty was sent to
 * @param units how many of each Unit type it holds
 * @param homeward whether it has been to its target and is heading home
 * @param turnsLeft how many more Turns must resolve before it arrives where it
 * is heading
 */
public record Warparty(String target, Map<String, Integer> units, boolean homeward, int turnsLeft) {

	public Warparty {
		units = Collections.unmodifiableMap(new LinkedHashMap<>(units));
	}

	/**
	 * This Warparty one Turn closer to where it is heading.
	 */
	Warparty travelled() {
		return new Warparty(target, units, homeward, turnsLeft - 1);
	}

	/**
	 * This Warparty, having reached its target, setting off home.
	 */
	Warparty headingHome(int travelTurns) {
		return new Warparty(target, units, true, travelTurns);
	}

	/**
	 * This Warparty holding only the given Units.
	 */
	Warparty with(Map<String, Integer> units) {
		return new Warparty(target, units, homeward, turnsLeft);
	}

}
