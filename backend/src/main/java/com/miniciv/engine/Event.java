package com.miniciv.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Something that may happen to a Nation or a Warparty as a Turn ends, outside
 * the Players' direct actions. Each Turn, every Nation or Warparty its
 * conditions hold for has the given chance of it happening, and its effects
 * are announced in the Turn Report.
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
	 * One of the choices a Dilemma offers.
	 *
	 * @param name what the Player picks it by, unique within the Dilemma
	 * @param effects what it changes when it applies
	 * @param worst whether it is the one that applies if the Player does not
	 *        choose before the Turn ends
	 * @param text how the Turn Report describes it, such as "You quarantined
	 *        the sick", followed by its effects
	 */
	public record Choice(String name, Effects effects, boolean worst, String text) {

		public Choice {
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
	 */
	public record Conditions(Integer turnAtLeast, Integer populationAtLeast, Integer approachingUnitsAtLeast,
			Integer unitsAtLeast) {

		static final Conditions NONE = new Conditions(null, null, null, null);

		boolean holdForNation(int turn, int population, int largestApproaching) {
			return atLeast(turn, turnAtLeast) && atLeast(population, populationAtLeast)
					&& atLeast(largestApproaching, approachingUnitsAtLeast);
		}

		boolean holdForWarparty(int turn, Warparty warparty) {
			return atLeast(turn, turnAtLeast) && atLeast(warparty.unitCount(), unitsAtLeast);
		}

		private static boolean atLeast(int value, Integer least) {
			return least == null || value >= least;
		}

	}

	/**
	 * The fixed set of effects an Event may have; one left out is 0.
	 *
	 * @param gold how much the Nation's Gold changes by, not below 0
	 * @param population how much the Nation's Population changes by, not below
	 *        0
	 * @param research how much the Nation's Research changes by, not below 0
	 * @param damageDefense how much the Nation's Defense is worn down by,
	 *        damaging or destroying its defensive Buildings as a Battle would
	 * @param travelTurns how many Turns the Warparty's trip changes by; it
	 *        still takes at least one more
	 */
	public record Effects(int gold, int population, int research, int damageDefense, int travelTurns) {

		static final Effects NONE = new Effects(0, 0, 0, 0, 0);

	}

	/**
	 * What is wrong with this Event, if anything.
	 */
	List<String> problems() {
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
		if (effects.damageDefense() < 0) {
			problems.add(subjectName + " cannot damage Defense by less than 0.");
		}
		if (subject == Subject.WARPARTY) {
			String unsuited = subjectName + " happens to a Warparty, so it cannot ";
			unsuited(problems, unsuited + "need populationAtLeast.", when.populationAtLeast() != null);
			unsuited(problems, unsuited + "need approachingUnitsAtLeast.", when.approachingUnitsAtLeast() != null);
			unsuited(problems, unsuited + "change gold.", effects.gold() != 0);
			unsuited(problems, unsuited + "change population.", effects.population() != 0);
			unsuited(problems, unsuited + "change research.", effects.research() != 0);
			unsuited(problems, unsuited + "change damageDefense.", effects.damageDefense() != 0);
		}
		if (subject == Subject.NATION) {
			String unsuited = subjectName + " happens to a Nation, so it cannot ";
			unsuited(problems, unsuited + "need unitsAtLeast.", when.unitsAtLeast() != null);
			unsuited(problems, unsuited + "change travelTurns.", effects.travelTurns() != 0);
		}
		if (dilemma()) {
			problems.addAll(dilemmaProblems(subjectName));
		}
		return problems;
	}

	/**
	 * What is wrong with this Event as a Dilemma, if anything.
	 */
	private List<String> dilemmaProblems(String subjectName) {
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
			if (choice.text() == null || choice.text().isBlank()) {
				problems.add(choiceName + " has no text to describe it.");
			}
			if (choice.effects().travelTurns() != 0) {
				problems.add(choiceName + " cannot change travelTurns.");
			}
			if (choice.effects().damageDefense() < 0) {
				problems.add(choiceName + " cannot damage Defense by less than 0.");
			}
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
