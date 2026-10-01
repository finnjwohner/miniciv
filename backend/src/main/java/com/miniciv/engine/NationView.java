package com.miniciv.engine;

import java.util.List;
import java.util.Map;

/**
 * What one Nation's Player may see on a Turn: their own state, the other
 * Nations' names and nothing more about them, and the Turn Report of the Turn
 * before, which is null on the first Turn. The version rises with every change
 * to the Nation, so a browser can ignore a view older than one it already has.
 *
 * @param availableBuildings the Buildings the Nation may start
 * @param buildings how many of each Building the Nation has completed
 * @param construction the Buildings under construction, in the order started
 * @param availableUnits the Unit types the Nation may train
 * @param army how many of each Unit type the Nation has at home
 * @param training the Units in training, in the order started
 * @param upkeep the Gold the Army costs each Turn
 */
public record NationView(int version, int turn, String nation, int gold, int population, int research,
		List<String> otherNations, List<BuildingType> availableBuildings, Map<String, Integer> buildings,
		List<Construction> construction, List<UnitType> availableUnits, Map<String, Integer> army,
		List<Training> training, int upkeep, TurnReport turnReport) {

	public NationView {
		otherNations = List.copyOf(otherNations);
		availableBuildings = List.copyOf(availableBuildings);
		construction = List.copyOf(construction);
		availableUnits = List.copyOf(availableUnits);
		training = List.copyOf(training);
	}

}
