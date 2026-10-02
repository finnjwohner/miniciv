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
 * @param scout whether it is a Scout: one that never fights or leaves its
 * Nation, but may discover other Nations' Warparties abroad
 */
public record UnitType(String name, int gold, int population, int turns, int upkeep, double strength,
		List<String> counters, boolean scout) {

	public UnitType {
		counters = counters == null ? List.of() : List.copyOf(counters);
	}

	/**
	 * A Unit type that is not a Scout.
	 */
	public UnitType(String name, int gold, int population, int turns, int upkeep, double strength,
			List<String> counters) {
		this(name, gold, population, turns, upkeep, strength, counters, false);
	}

}
