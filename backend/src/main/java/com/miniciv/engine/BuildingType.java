package com.miniciv.engine;

/**
 * A kind of Building a Nation can construct, paying Gold upfront. Once
 * complete, it adds its effects every Turn.
 *
 * @param turns how many Turns construction takes
 * @param populationPerTurn Population it adds each Turn
 * @param goldPerTurn flat Gold it adds each Turn
 * @param goldPerPopulation Gold it adds each Turn for every one of the
 *        Nation's Population
 * @param researchPerTurn flat Research it adds each Turn
 * @param defense how much it raises the Nation's Defense while standing; a
 *        defensive Building is one that raises it
 */
public record BuildingType(String name, int gold, int turns, int populationPerTurn, int goldPerTurn,
		double goldPerPopulation, int researchPerTurn, int defense) {
}
