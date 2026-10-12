package com.miniciv.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The content Barbarian Camps are made from as they appear: the places they
 * are named for, their Rulers, and the barbarian defensive Buildings they may
 * start with. A ruleset with no places has no Barbarian Camps at all.
 *
 * @param places what each Camp is named for, such as "Mountain Passage", each
 *        used by at most one Camp in a Game
 * @param rulers the names a Camp's Ruler may have, such as "Grak the Red"
 * @param buildings the barbarian defensive Buildings a Camp may start with
 */
public record Barbarians(List<String> places, List<String> rulers, List<Building> buildings) {

	/**
	 * No Barbarian Camps.
	 */
	public static final Barbarians NONE = new Barbarians(List.of(), List.of(), List.of());

	public Barbarians {
		places = places == null ? List.of() : List.copyOf(places);
		rulers = rulers == null ? List.of() : List.copyOf(rulers);
		buildings = buildings == null ? List.of() : List.copyOf(buildings);
	}

	/**
	 * A barbarian defensive Building, which raises its Camp's Defense only and
	 * is never built or Repaired.
	 *
	 * @param name what it is called, such as "Spike Pit"
	 * @param defense how much it raises its Camp's Defense while standing
	 */
	public record Building(String name, int defense) {
	}

	/**
	 * Whether Barbarian Camps can appear at all.
	 */
	boolean appear() {
		return !places.isEmpty();
	}

	/**
	 * The barbarian Building of the given name.
	 */
	Building building(String name) {
		return buildings.stream()
			.filter(building -> building.name().equals(name))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("There is no barbarian Building called " + name));
	}

	/**
	 * What does not make sense in the content, for a ruleset with the given
	 * Unit types.
	 */
	List<String> problems(List<UnitType> units) {
		List<String> problems = new ArrayList<>();
		if (!appear()) {
			return problems;
		}
		if (places.stream().anyMatch(Barbarians::isBlank)) {
			problems.add("A Barbarian Camp place has no name.");
		}
		if (new HashSet<>(places).size() < places.size()) {
			problems.add("A Barbarian Camp place is listed more than once.");
		}
		if (rulers.isEmpty() || rulers.stream().anyMatch(Barbarians::isBlank)) {
			problems.add("Barbarian Camps need Rulers, each with a name.");
		}
		Set<String> names = new HashSet<>();
		for (Building building : buildings) {
			if (isBlank(building.name())) {
				problems.add("A barbarian Building has no name.");
			}
			else if (!names.add(building.name())) {
				problems.add("There is more than one barbarian Building called " + building.name() + ".");
			}
			if (building.defense() < 1) {
				problems.add("Barbarian Building " + building.name() + " must raise Defense by at least 1.");
			}
		}
		if (units.stream().noneMatch(Camp::fights)) {
			problems.add("Barbarian Camps need a fighting Unit type: one with Strength, neither a Scout nor a Spy.");
		}
		return problems;
	}

	private static boolean isBlank(String name) {
		return name == null || name.isBlank();
	}

}
