package com.miniciv.engine;

import java.util.List;

/**
 * A kind of Unit a Nation can train, paying Gold and Population upfront and
 * Gold upkeep every Turn after.
 *
 * @param turns how many Turns training takes
 * @param strength how much it contributes in a Battle; Scouts and Spies have
 * only a little
 * @param counters the Unit types this one multiplies its Strength against
 */
public record UnitType(String name, int gold, int population, int turns, int upkeep, double strength,
		List<String> counters) {

	public UnitType {
		counters = counters == null ? List.of() : List.copyOf(counters);
	}

}
