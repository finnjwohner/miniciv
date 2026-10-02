package com.miniciv.engine;

/**
 * A kind of Building a Nation can construct, paying Gold upfront. Once
 * complete, it adds its effects every Turn.
 *
 * @param turns how many Turns construction takes
 * @param populationPerTurn Population it adds each Turn
 * @param goldPerPopulation Gold it adds each Turn for every one of the
 *        Nation's Population
 * @param defense how much it raises the Nation's Defense while standing; a
 *        defensive Building is one that raises it
 */
public record BuildingType(String name, int gold, int turns, int populationPerTurn, double goldPerPopulation,
		int defense) {
}
