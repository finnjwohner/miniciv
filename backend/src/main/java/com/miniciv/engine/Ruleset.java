package com.miniciv.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The game content: Unit types, Building types, Technologies and Events. It is
 * checked when made, so a Game never meets content that refers to something
 * missing.
 */
public record Ruleset(List<UnitType> units, List<BuildingType> buildings, List<Technology> technologies,
		List<Event> events) {

	public Ruleset {
		units = List.copyOf(units);
		buildings = List.copyOf(buildings);
		technologies = List.copyOf(technologies);
		events = List.copyOf(events);
		List<String> problems = problems(units, buildings, technologies, events);
		if (!problems.isEmpty()) {
			throw new InvalidRuleset("The ruleset is invalid:\n" + String.join("\n", problems));
		}
	}

	/**
	 * Game content with no Events.
	 */
	public Ruleset(List<UnitType> units, List<BuildingType> buildings, List<Technology> technologies) {
		this(units, buildings, technologies, List.of());
	}

	public Optional<BuildingType> building(String name) {
		return buildings.stream().filter(b -> b.name().equals(name)).findFirst();
	}

	public Optional<UnitType> unit(String name) {
		return units.stream().filter(u -> u.name().equals(name)).findFirst();
	}

	public Optional<Technology> technology(String name) {
		return technologies.stream().filter(t -> t.name().equals(name)).findFirst();
	}

	/**
	 * The Strength of the given Units added up, without counters.
	 *
	 * @param units how many of each Unit type
	 */
	public double strengthOf(Map<String, Integer> units) {
		return units.entrySet()
			.stream()
			.mapToDouble(u -> unit(u.getKey()).orElseThrow().strength() * u.getValue())
			.sum();
	}

	/**
	 * The Technology needed to start a Unit or Building type, if one is.
	 */
	public Optional<Technology> unlockedBy(String unitOrBuilding) {
		return technologies.stream().filter(t -> t.unlocks().contains(unitOrBuilding)).findFirst();
	}

	private static List<String> problems(List<UnitType> units, List<BuildingType> buildings,
			List<Technology> technologies, List<Event> events) {
		List<String> problems = new ArrayList<>();
		Set<String> unitNames = names(units.stream().map(UnitType::name));
		Set<String> buildingNames = names(buildings.stream().map(BuildingType::name));
		Set<String> technologyNames = names(technologies.stream().map(Technology::name));

		if (units.stream().map(UnitType::name).anyMatch(Ruleset::isBlank)) {
			problems.add("A Unit type has no name.");
		}
		if (buildings.stream().map(BuildingType::name).anyMatch(Ruleset::isBlank)) {
			problems.add("A Building type has no name.");
		}
		if (technologies.stream().map(Technology::name).anyMatch(Ruleset::isBlank)) {
			problems.add("A Technology has no name.");
		}
		if (events.stream().map(Event::name).anyMatch(Ruleset::isBlank)) {
			problems.add("An Event has no name.");
		}
		duplicates(Stream.concat(units.stream().map(UnitType::name), buildings.stream().map(BuildingType::name)))
			.forEach(name -> problems.add("There is more than one Unit or Building type called " + name + "."));
		duplicates(technologies.stream().map(Technology::name))
			.forEach(name -> problems.add("There is more than one Technology called " + name + "."));
		duplicates(events.stream().map(Event::name))
			.forEach(name -> problems.add("There is more than one Event called " + name + "."));

		for (UnitType unit : units) {
			String subject = "Unit type " + unit.name();
			if (unit.turns() < 1) {
				problems.add(subject + " must take at least 1 Turn to train.");
			}
			notNegative(problems, subject + " cannot cost less than 0 Gold.", unit.gold());
			notNegative(problems, subject + " cannot cost less than 0 Population.", unit.population());
			notNegative(problems, subject + " cannot cost less than 0 Gold upkeep.", unit.upkeep());
			notNegative(problems, subject + " cannot have less than 0 Strength.", unit.strength());
			for (String countered : unit.counters()) {
				if (!unitNames.contains(countered)) {
					problems.add(subject + " counters " + countered + ", which is not a Unit type.");
				}
			}
		}
		for (BuildingType building : buildings) {
			String subject = "Building type " + building.name();
			if (building.turns() < 1) {
				problems.add(subject + " must take at least 1 Turn to construct.");
			}
			notNegative(problems, subject + " cannot cost less than 0 Gold.", building.gold());
			notNegative(problems, subject + " cannot add less than 0 Population per Turn.", building.populationPerTurn());
			notNegative(problems, subject + " cannot add less than 0 Gold per Turn.", building.goldPerTurn());
			notNegative(problems, subject + " cannot add less than 0 Gold per Population.", building.goldPerPopulation());
			notNegative(problems, subject + " cannot add less than 0 Research per Turn.", building.researchPerTurn());
			notNegative(problems, subject + " cannot add less than 0 Defense.", building.defense());
		}
		for (Technology technology : technologies) {
			String subject = "Technology " + technology.name();
			notNegative(problems, subject + " cannot cost less than 0 Research.", technology.research());
			for (String prerequisite : technology.prerequisites()) {
				if (!technologyNames.contains(prerequisite)) {
					problems.add(subject + " requires " + prerequisite + ", which is not a Technology.");
				}
			}
			for (String unlocked : technology.unlocks()) {
				if (!unitNames.contains(unlocked) && !buildingNames.contains(unlocked)) {
					problems.add(subject + " unlocks " + unlocked + ", which is neither a Unit nor a Building type.");
				}
			}
		}
		for (Technology technology : technologies) {
			if (requires(technology, technology.name(), technologies, new HashSet<>())) {
				problems.add("Technology " + technology.name() + " can never be unlocked, as it requires itself.");
			}
		}
		duplicates(technologies.stream().flatMap(technology -> technology.unlocks().stream()))
			.forEach(name -> problems.add(name + " is unlocked by more than one Technology."));
		events.forEach(event -> problems.addAll(event.problems()));
		return problems;
	}

	/**
	 * Whether a Technology requires another, directly or through its
	 * prerequisites' prerequisites.
	 *
	 * @param visited the Technologies already looked through
	 */
	private static boolean requires(Technology technology, String required, List<Technology> technologies,
			Set<String> visited) {
		for (String prerequisite : technology.prerequisites()) {
			if (prerequisite.equals(required)) {
				return true;
			}
			if (visited.add(prerequisite)) {
				Optional<Technology> next = technologies.stream().filter(t -> t.name().equals(prerequisite)).findFirst();
				if (next.isPresent() && requires(next.get(), required, technologies, visited)) {
					return true;
				}
			}
		}
		return false;
	}

	private static Set<String> names(Stream<String> names) {
		return names.collect(Collectors.toSet());
	}

	private static boolean isBlank(String name) {
		return name == null || name.isBlank();
	}

	private static List<String> duplicates(Stream<String> names) {
		Set<String> seen = new HashSet<>();
		return names.filter(name -> !isBlank(name)).filter(name -> !seen.add(name)).distinct().toList();
	}

	private static void notNegative(List<String> problems, String problem, double value) {
		if (value < 0) {
			problems.add(problem);
		}
	}

}
