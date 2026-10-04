package com.miniciv.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What one Nation has learned of another's hidden state, each fact the latest
 * learned of its kind, or null if none has been.
 *
 * @param army how many of each Unit type the Nation had at home
 * @param buildings how many of each Building the Nation had completed
 * @param defense the Nation's Defense, learned along with its Buildings
 * @param damaged how many of each non-defensive Building the Nation had
 *        Damaged, learned along with its Buildings
 */
public record Facts(Fact<Integer> gold, Fact<Integer> population, Fact<Integer> research,
		Fact<Map<String, Integer>> army, Fact<Map<String, Integer>> buildings, Fact<Integer> defense,
		Fact<Map<String, Integer>> damaged) {

	/**
	 * Nothing learned at all.
	 */
	public static final Facts NONE = new Facts(null, null, null, null, null, null, null);

	Facts withGold(int gold, int turn) {
		return new Facts(new Fact<>(gold, turn), population, research, army, buildings, defense, damaged);
	}

	Facts withPopulation(int population, int turn) {
		return new Facts(gold, new Fact<>(population, turn), research, army, buildings, defense, damaged);
	}

	Facts withResearch(int research, int turn) {
		return new Facts(gold, population, new Fact<>(research, turn), army, buildings, defense, damaged);
	}

	Facts withArmy(Map<String, Integer> army, int turn) {
		return new Facts(gold, population, research, new Fact<>(copied(army), turn), buildings, defense, damaged);
	}

	Facts withBuildings(Map<String, Integer> buildings, Map<String, Integer> damaged, int defense, int turn) {
		return new Facts(gold, population, research, army, new Fact<>(copied(buildings), turn), new Fact<>(defense, turn),
				new Fact<>(copied(damaged), turn));
	}

	private static Map<String, Integer> copied(Map<String, Integer> units) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(units));
	}

}
