package com.miniciv.engine;

import java.util.List;

/**
 * A kind of Unit a Nation can train, paying Gold and Population upfront and
 * Gold upkeep every Turn after.
 *
 * @param turns how many Turns training takes
 * @param strength how much it contributes in a Battle; Spies have only a
 * little
 * @param counters the Unit types this one multiplies its Strength against
 * @param scout whether it is a Scout: one that roams the wilds from the moment
 * it is trained, never part of the Army and never fighting, but may discover
 * other Nations' Warparties abroad and catch their travelling Spies
 * @param spy whether it is a Spy: one sent on its own, never in a Warparty, to
 * stay in another Nation and report its hidden state
 */
public record UnitType(String name, int gold, int population, int turns, int upkeep, double strength,
		List<String> counters, boolean scout, boolean spy) {

	public UnitType {
		counters = counters == null ? List.of() : List.copyOf(counters);
	}

	/**
	 * A Unit type that is not a Spy.
	 */
	public UnitType(String name, int gold, int population, int turns, int upkeep, double strength,
			List<String> counters, boolean scout) {
		this(name, gold, population, turns, upkeep, strength, counters, scout, false);
	}

	/**
	 * A Unit type that is neither a Scout nor a Spy.
	 */
	public UnitType(String name, int gold, int population, int turns, int upkeep, double strength,
			List<String> counters) {
		this(name, gold, population, turns, upkeep, strength, counters, false, false);
	}

}
