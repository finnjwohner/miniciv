package com.miniciv.engine;

import java.util.List;

/**
 * One row of the table of how long Warparties travel: a Warparty whose
 * Strength is at least this one's, but below the next row's, takes this many
 * Turns to reach another Nation.
 *
 * @param strength the least Strength in the bracket
 * @param turns how many Turns a Warparty in the bracket travels
 */
public record TravelBracket(int strength, int turns) {

	/**
	 * The brackets, from the weakest Warparties up.
	 */
	static final List<TravelBracket> ALL = List.of(new TravelBracket(0, 3), new TravelBracket(10, 4),
			new TravelBracket(25, 5), new TravelBracket(50, 6), new TravelBracket(100, 7));

	/**
	 * The haul at which a Warparty heading home takes one more Turn.
	 */
	private static final int HEAVY_HAUL = 30;

	/**
	 * The haul at which a Warparty heading home takes two more Turns.
	 */
	private static final int HEAVIEST_HAUL = 80;

	/**
	 * The most Turns a Warparty takes to come home from a Battle, however much
	 * it carries.
	 */
	private static final int MOST_TURNS_HOME = 7;

	/**
	 * How many Turns a Warparty with the given Strength takes to reach another
	 * Nation.
	 */
	static int turnsOut(double strength) {
		int turns = ALL.get(0).turns();
		for (TravelBracket bracket : ALL) {
			if (strength >= bracket.strength()) {
				turns = bracket.turns();
			}
		}
		return turns;
	}

	/**
	 * How many Turns a Warparty takes to come home from a Battle: the bracket
	 * for the Strength that survived it, and more for the Gold and Research it
	 * carries, whether plundered or seized in a Conquest.
	 */
	static int turnsHome(double strength, Plunder haul) {
		int carried = haul.gold() + haul.research();
		int extra = carried >= HEAVIEST_HAUL ? 2 : carried >= HEAVY_HAUL ? 1 : 0;
		return Math.min(MOST_TURNS_HOME, turnsOut(strength) + extra);
	}

}
