package com.miniciv.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Something that may happen to a Nation or a Warparty as a Turn ends, outside
 * the Players' direct actions. Each Turn, every Nation or Warparty its
 * conditions hold for, and that has what its effects need (the Units it loses,
 * work in progress to stall or speed up, a Spy to catch), has the given chance
 * of it happening, and its effects are announced in the Turn Report.
 *
 * @param name what the content calls it, unique among Events
 * @param subject what it happens to
 * @param chance how likely it is to happen to each Nation or Warparty its
 *        conditions hold for, above 0 and at most 1
 * @param when the conditions that must all hold for it to happen
 * @param effects what it changes when it happens
 * @param text how the Turn Report announces it, such as "A dust storm swept
 *        the plains", followed by its effects
 * @param choices for a Dilemma, the two or three choices it offers its
 *        Player, one of them the worst; empty for any other Event
 */
public record Event(String name, Subject subject, double chance, Conditions when, Effects effects, String text,
		List<Choice> choices) {

	public Event {
		when = when == null ? Conditions.NONE : when;
		effects = effects == null ? Effects.NONE : effects;
		choices = choices == null ? List.of() : List.copyOf(choices);
	}

	/**
	 * An Event that offers no choices.
	 */
	public Event(String name, Subject subject, double chance, Conditions when, Effects effects, String text) {
		this(name, subject, chance, when, effects, text, List.of());
	}

	/**
	 * Whether it is a Dilemma, offering its Player choices.
	 */
	boolean dilemma() {
		return !choices.isEmpty();
	}

	/**
	 * The choice of the given name this Dilemma offers, if any.
	 */
	Optional<Choice> choice(String name) {
		return choices.stream().filter(choice -> choice.name().equals(name)).findFirst();
	}

	/**
	 * The choice that applies to a Dilemma its Player did not answer in time.
	 */
	Choice worstChoice() {
		return choices.stream().filter(Choice::worst).findFirst().orElseThrow();
	}

	/**
	 * One of the choices a Dilemma offers, which leads to one of its outcomes
	 * by chance: either the single outcome its own effects and text describe,
	 * or one of the outcomes it lists.
	 *
	 * @param name what the Player picks it by, unique within the Dilemma
	 * @param effects what it changes when it applies, if it lists no outcomes
	 * @param worst whether it is the one that applies if the Player does not
	 *        choose before the Turn ends
	 * @param text how the Turn Report describes it, such as "You quarantined
	 *        the sick", followed by its effects, if it lists no outcomes
	 * @param outcomes what it may lead to, whose chances total 1; empty if it
	 *        has a single outcome of its own effects and text
	 */
	public record Choice(String name, Effects effects, boolean worst, String text, List<Outcome> outcomes) {

		public Choice {
			effects = effects == null ? Effects.NONE : effects;
			outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
		}

		/**
		 * A choice with a single outcome of its own effects and text.
		 */
		public Choice(String name, Effects effects, boolean worst, String text) {
			this(name, effects, worst, text, List.of());
		}

		/**
		 * A choice that may lead to any of the given outcomes.
		 */
		public Choice(String name, boolean worst, List<Outcome> outcomes) {
			this(name, null, worst, null, outcomes);
		}

		/**
		 * The outcome this choice leads to, by chance: luck below the first
		 * outcome's chance picks it, luck below the first two's picks the
		 * second, and so on. A choice of a single outcome draws no luck.
		 */
		Outcome outcome(RandomGenerator random) {
			if (outcomes.isEmpty()) {
				return new Outcome(1, effects, text);
			}
			if (outcomes.size() == 1) {
				return outcomes.getFirst();
			}
			double luck = random.nextDouble();
			double total = 0;
			for (Outcome outcome : outcomes) {
				total += outcome.chance();
				if (luck < total) {
					return outcome;
				}
			}
			return outcomes.getLast();
		}

	}

	/**
	 * One of the things a Dilemma choice may lead to.
	 *
	 * @param chance how likely the choice is to lead to it, above 0 and at most
	 *        1
	 * @param effects what it changes
	 * @param text how the Turn Report describes it, such as "The bandits
	 *        ambushed your hunters", followed by its effects
	 */
	public record Outcome(double chance, Effects effects, String text) {

		public Outcome {
			effects = effects == null ? Effects.NONE : effects;
		}

	}

	/**
	 * What an Event happens to.
	 */
	public enum Subject {

		/**
		 * A Nation still in the Game.
		 */
		@JsonProperty("Nation")
		NATION,

		/**
		 * A Warparty travelling to its target or heading home, but not one
		 * waiting at its target for the Battle.
		 */
		@JsonProperty("Warparty")
		WARPARTY

	}

	/**
	 * The fixed set of conditions an Event may need; one left out always holds.
	 *
	 * @param turnAtLeast the Turn ending is at least this one
	 * @param populationAtLeast the Nation has at least this much Population
	 * @param approachingUnitsAtLeast a Warparty of at least this many Units is
	 *        travelling to the Nation
	 * @param unitsAtLeast the Warparty has at least this many Units
	 * @param goldAtLeast the Nation has at least this much Gold
	 * @param researchAtLeast the Nation has at least this much Research
	 * @param defenseAtLeast the Nation's Defense is at least this much
	 */
	public record Conditions(Integer turnAtLeast, Integer populationAtLeast, Integer approachingUnitsAtLeast,
			Integer unitsAtLeast, Integer goldAtLeast, Integer researchAtLeast, Integer defenseAtLeast) {

		static final Conditions NONE = new Conditions(null, null, null, null, null, null, null);

		boolean holdForNation(int turn, Nation nation, int defense, int largestApproaching) {
			return atLeast(turn, turnAtLeast) && atLeast(nation.population(), populationAtLeast)
					&& atLeast(largestApproaching, approachingUnitsAtLeast) && atLeast(nation.gold(), goldAtLeast)
					&& atLeast(nation.research(), researchAtLeast) && atLeast(defense, defenseAtLeast);
		}

		boolean holdForWarparty(int turn, Warparty warparty) {
			return atLeast(turn, turnAtLeast) && atLeast(warparty.unitCount(), unitsAtLeast);
		}

		private static boolean atLeast(int value, Integer least) {
			return least == null || value >= least;
		}

	}

	/**
	 * Whose Spy an Event catches.
	 */
	public enum SpyCaught {

		/**
		 * One of the Nation's own Spies abroad, caught by the Nation it was
		 * sent to.
		 */
		@JsonProperty("own")
		OWN,

		/**
		 * One of another Nation's Spies in place in the Nation's land.
		 */
		@JsonProperty("enemy")
		ENEMY

	}

	/**
	 * The fixed set of effects an Event may have; one left out is 0 or none.
	 *
	 * @param gold how much the Nation's Gold changes by, not below 0
	 * @param population how much the Nation's Population changes by, not below
	 *        0
	 * @param research how much the Nation's Research changes by, not below 0
	 * @param damageDefense how much the Nation's Defense is worn down by,
	 *        damaging or destroying its defensive Buildings as a Battle would
	 * @param travelTurns how many Turns the Warparty's trip changes by; it
	 *        still takes at least one more
	 * @param units how many of each Unit type join the Nation's Army or the
	 *        Warparty, or leave it if below 0, in the order listed; no more
	 *        leave than are there
	 * @param workTurns how many Turns one of the Nation's Buildings under
	 *        construction, Repairs, Rebuilds or Units in training, picked by
	 *        luck, is stalled by, or sped up by if below 0; it still takes at
	 *        least one more
	 * @param spyCaught whose Spy is caught, picked by luck, or null if none is
	 */
	public record Effects(int gold, int population, int research, int damageDefense, int travelTurns,
			Map<String, Integer> units, int workTurns, SpyCaught spyCaught) {

		static final Effects NONE = new Effects(0, 0, 0, 0, 0);

		public Effects {
			units = units == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(units));
		}

		/**
		 * Effects that change only resources, Defense and travel time.
		 */
		public Effects(int gold, int population, int research, int damageDefense, int travelTurns) {
			this(gold, population, research, damageDefense, travelTurns, null, 0, null);
		}

		/**
		 * These effects as luck varies them, drawing once from the given random
		 * source for each Gold, Population, Research and Defense-damage effect
		 * that is not 0, in that order, then for each Unit type in the order
		 * listed, then for work Turns if not 0; travel Turns never vary.
		 */
		Effects varied(RandomGenerator random) {
			int variedGold = varied(gold, random);
			int variedPopulation = varied(population, random);
			int variedResearch = varied(research, random);
			int variedDamage = varied(damageDefense, random);
			Map<String, Integer> variedUnits = new LinkedHashMap<>();
			units.forEach((unit, count) -> variedUnits.put(unit, varied(count, random)));
			return new Effects(variedGold, variedPopulation, variedResearch, variedDamage, travelTurns, variedUnits,
					varied(workTurns, random), spyCaught);
		}

		/**
		 * The Unit types some of which leave, in the order listed.
		 */
		List<String> unitsLeaving() {
			return units.entrySet().stream().filter(change -> change.getValue() < 0).map(Map.Entry::getKey).toList();
		}

		private static int varied(int authored, RandomGenerator random) {
			return authored == 0 ? 0 : variedBy(random.nextDouble(), authored);
		}

		/**
		 * An effect as luck varies it: by up to a tenth of it either way,
		 * rounded down, but by at least 1 either way or not at all, so small
		 * effects still vary; never to 0, and never from a gain to a loss or
		 * back. Luck of 0 makes it smallest, 0.5 leaves it as authored, and
		 * luck near 1 makes it largest.
		 *
		 * @param luck drawn from the seeded random source, at least 0 and below
		 *        1
		 */
		private static int variedBy(double luck, int authored) {
			int spread = Math.max(1, Math.abs(authored) / 10);
			int change = (int) (luck * (2 * spread + 1)) - spread;
			return Integer.signum(authored) * Math.max(1, Math.abs(authored) + change);
		}

	}

	/**
	 * What is wrong with this Event, if anything.
	 *
	 * @param unitTypes the Unit types in the ruleset
	 */
	List<String> problems(List<UnitType> unitTypes) {
		List<String> problems = new ArrayList<>();
		String subjectName = "Event " + name;
		if (subject == null) {
			problems.add(subjectName + " must happen to a Nation or a Warparty.");
		}
		if (!(chance > 0 && chance <= 1)) {
			problems.add(subjectName + " must have a chance above 0 and at most 1.");
		}
		if (text == null || text.isBlank()) {
			problems.add(subjectName + " has no text to announce it.");
		}
		notBelowZero(problems, subjectName, "turnAtLeast", when.turnAtLeast());
		notBelowZero(problems, subjectName, "populationAtLeast", when.populationAtLeast());
		notBelowZero(problems, subjectName, "approachingUnitsAtLeast", when.approachingUnitsAtLeast());
		notBelowZero(problems, subjectName, "unitsAtLeast", when.unitsAtLeast());
		notBelowZero(problems, subjectName, "goldAtLeast", when.goldAtLeast());
		notBelowZero(problems, subjectName, "researchAtLeast", when.researchAtLeast());
		notBelowZero(problems, subjectName, "defenseAtLeast", when.defenseAtLeast());
		if (effects.damageDefense() < 0) {
			problems.add(subjectName + " cannot damage Defense by less than 0.");
		}
		problems.addAll(unitProblems(subjectName, effects, unitTypes));
		if (subject == Subject.WARPARTY) {
			String unsuited = subjectName + " happens to a Warparty, so it cannot ";
			unsuited(problems, unsuited + "need populationAtLeast.", when.populationAtLeast() != null);
			unsuited(problems, unsuited + "need approachingUnitsAtLeast.", when.approachingUnitsAtLeast() != null);
			unsuited(problems, unsuited + "need goldAtLeast.", when.goldAtLeast() != null);
			unsuited(problems, unsuited + "need researchAtLeast.", when.researchAtLeast() != null);
			unsuited(problems, unsuited + "need defenseAtLeast.", when.defenseAtLeast() != null);
			unsuited(problems, unsuited + "change gold.", effects.gold() != 0);
			unsuited(problems, unsuited + "change population.", effects.population() != 0);
			unsuited(problems, unsuited + "change research.", effects.research() != 0);
			unsuited(problems, unsuited + "change damageDefense.", effects.damageDefense() != 0);
			unsuited(problems, unsuited + "change workTurns.", effects.workTurns() != 0);
			unsuited(problems, unsuited + "change spyCaught.", effects.spyCaught() != null);
		}
		if (subject == Subject.NATION) {
			String unsuited = subjectName + " happens to a Nation, so it cannot ";
			unsuited(problems, unsuited + "need unitsAtLeast.", when.unitsAtLeast() != null);
			unsuited(problems, unsuited + "change travelTurns.", effects.travelTurns() != 0);
		}
		if (dilemma()) {
			problems.addAll(dilemmaProblems(subjectName, unitTypes));
		}
		return problems;
	}

	/**
	 * What is wrong with the Units some effects gain or lose, if anything.
	 *
	 * @param what what has the effects, such as "Event Volunteers" or "Event
	 *        Mercenaries's choice Hire has an outcome that"
	 */
	private List<String> unitProblems(String what, Effects effects, List<UnitType> unitTypes) {
		List<String> problems = new ArrayList<>();
		effects.units().forEach((unit, count) -> {
			Optional<UnitType> type = unitTypes.stream().filter(known -> known.name().equals(unit)).findFirst();
			if (type.isEmpty()) {
				problems.add(what + " gains or loses " + unit + ", which is not a Unit type.");
			}
			else if (subject == Subject.WARPARTY && (type.get().scout() || type.get().spy())) {
				problems.add(what + " happens to a Warparty, so it cannot gain or lose " + unit + ".");
			}
			else if (type.get().scout()) {
				problems.add(what + " cannot gain or lose " + unit + ", as Scouts are never in the Army.");
			}
			if (count == null || count == 0) {
				problems.add(what + " cannot gain or lose 0 " + unit + ".");
			}
		});
		return problems;
	}

	/**
	 * What is wrong with this Event as a Dilemma, if anything.
	 */
	private List<String> dilemmaProblems(String subjectName, List<UnitType> unitTypes) {
		List<String> problems = new ArrayList<>();
		if (subject == Subject.WARPARTY) {
			problems.add(subjectName + " offers choices, so it must happen to a Nation.");
		}
		if (!effects.equals(Effects.NONE)) {
			problems.add(subjectName + " offers choices, so its effects belong to its choices.");
		}
		if (choices.size() < 2 || choices.size() > 3) {
			problems.add(subjectName + " must offer two or three choices.");
		}
		if (choices.stream().filter(Choice::worst).count() != 1) {
			problems.add(subjectName + " must mark exactly one of its choices as the worst.");
		}
		Set<String> named = new HashSet<>();
		for (Choice choice : choices) {
			if (choice.name() == null || choice.name().isBlank()) {
				problems.add(subjectName + " has a choice with no name.");
				continue;
			}
			if (!named.add(choice.name())) {
				problems.add(subjectName + " offers more than one choice called " + choice.name() + ".");
			}
			String choiceName = subjectName + "'s choice " + choice.name();
			if (choice.outcomes().isEmpty()) {
				if (choice.text() == null || choice.text().isBlank()) {
					problems.add(choiceName + " has no text to describe it.");
				}
				if (choice.effects().travelTurns() != 0) {
					problems.add(choiceName + " cannot change travelTurns.");
				}
				if (choice.effects().damageDefense() < 0) {
					problems.add(choiceName + " cannot damage Defense by less than 0.");
				}
				problems.addAll(unitProblems(choiceName, choice.effects(), unitTypes));
			}
			else {
				problems.addAll(outcomeProblems(choiceName, choice, unitTypes));
			}
		}
		return problems;
	}

	/**
	 * What is wrong with the outcomes a Dilemma choice lists, if anything.
	 */
	private List<String> outcomeProblems(String choiceName, Choice choice, List<UnitType> unitTypes) {
		List<String> problems = new ArrayList<>();
		if (!choice.effects().equals(Effects.NONE)) {
			problems.add(choiceName + " has outcomes, so its effects belong to its outcomes.");
		}
		if (choice.text() != null) {
			problems.add(choiceName + " has outcomes, so its text belongs to its outcomes.");
		}
		double total = 0;
		for (Outcome outcome : choice.outcomes()) {
			total += outcome.chance();
			if (!(outcome.chance() > 0 && outcome.chance() <= 1)) {
				problems.add(choiceName + " has an outcome without a chance above 0 and at most 1.");
			}
			if (outcome.text() == null || outcome.text().isBlank()) {
				problems.add(choiceName + " has an outcome with no text to describe it.");
			}
			if (outcome.effects().travelTurns() != 0) {
				problems.add(choiceName + " has an outcome that changes travelTurns.");
			}
			if (outcome.effects().damageDefense() < 0) {
				problems.add(choiceName + " has an outcome that damages Defense by less than 0.");
			}
			problems.addAll(unitProblems(choiceName + " has an outcome that", outcome.effects(), unitTypes));
		}
		// Chances such as 0.1 cannot be added exactly.
		if (Math.abs(total - 1) > 1e-9) {
			problems.add(choiceName + " has outcome chances totalling " + Math.round(total * 1e6) / 1e6 + ", not 1.");
		}
		return problems;
	}

	private static void notBelowZero(List<String> problems, String subject, String condition, Integer least) {
		if (least != null && least < 0) {
			problems.add(subject + " cannot need " + condition + " below 0.");
		}
	}

	private static void unsuited(List<String> problems, String problem, boolean present) {
		if (present) {
			problems.add(problem);
		}
	}

}
