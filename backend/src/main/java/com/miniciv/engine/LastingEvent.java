package com.miniciv.engine;

/**
 * The Lasting Event active for a Nation: a change that applies again as each
 * of its Turns left resolves.
 *
 * @param name the name of the Event or Dilemma that began it, such as
 *        "Drought"
 * @param turnsLeft how many more Turns it applies on, this one included
 * @param gold how much the Nation's Gold changes by each Turn
 * @param population how much the Nation's Population changes by each Turn
 * @param research how much the Nation's Research changes by each Turn
 * @param noGrowth whether the Nation's Population does not grow
 */
public record LastingEvent(String name, int turnsLeft, int gold, int population, int research, boolean noGrowth) {

	LastingEvent(String name, Event.Lasting lasting) {
		this(name, lasting.turns(), lasting.gold(), lasting.population(), lasting.research(), lasting.noGrowth());
	}

	/**
	 * It with one Turn fewer left.
	 */
	LastingEvent withTurnPassed() {
		return new LastingEvent(name, turnsLeft - 1, gold, population, research, noGrowth);
	}

}
