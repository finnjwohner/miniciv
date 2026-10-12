package com.miniciv.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a Nation learned of a Barbarian Camp by Locating it, as the Camp was
 * then.
 *
 * @param army how many of each Unit type the Camp had at home
 * @param buildings how many of each barbarian Building it had
 * @param defense its Defense
 * @param targets the Nations it could send Warparties at
 * @param turn the Turn it was Located, as the Turn resolved
 */
public record CampFacts(Map<String, Integer> army, Map<String, Integer> buildings, int defense, List<String> targets,
		int turn) {

	public CampFacts {
		army = Collections.unmodifiableMap(new LinkedHashMap<>(army));
		buildings = Collections.unmodifiableMap(new LinkedHashMap<>(buildings));
		targets = List.copyOf(targets);
	}

}
