package com.miniciv.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Spy in place at another Nation may be set to do there, for a fixed
 * number of Turns; a Spy on no Mission is Idle. An Intel Mission reports a
 * Fact for certain when it ends; a hostile Mission harms the Nation the Spy is
 * at, if it succeeds by chance when it ends.
 *
 * @param name what the Mission is called and chosen by, such as "Report Gold"
 * @param turns how many Turns must resolve before the Mission ends
 * @param reports for an Intel Mission, the kind of Fact it reports; null for a
 *        hostile one
 * @param chance for a hostile Mission, how likely it is to succeed when it
 *        ends, above 0 and at most 1; 0 for an Intel one
 * @param harm for a hostile Mission, what it does to the Nation the Spy is at
 *        if it succeeds; null for an Intel one
 * @param text for a hostile Mission, how the harmed Nation's Turn Report
 *        announces it, such as "Saboteurs struck in the night", followed by
 *        what it harmed; it never names who sent the Spy
 */
public record Mission(String name, int turns, FactKind reports, double chance, Harm harm, String text) {

	/**
	 * An Intel Mission, reporting the given kind of Fact.
	 */
	public Mission(String name, int turns, FactKind reports) {
		this(name, turns, reports, 0, null, null);
	}

	/**
	 * A hostile Mission.
	 */
	public Mission(String name, int turns, double chance, Harm harm, String text) {
		this(name, turns, null, chance, harm, text);
	}

	/**
	 * The kinds of Fact a Mission may report.
	 */
	public enum FactKind {
		GOLD, POPULATION, RESEARCH, ARMY,
		/**
		 * The Buildings completed and Damaged, which reveal Defense.
		 */
		BUILDINGS
	}

	/**
	 * What a hostile Mission that succeeds does to the Nation its Spy is at,
	 * each amount varied by luck as an Event's effects are; one left out is 0.
	 *
	 * @param damageDefense how much the Nation's Defense is worn down by,
	 *        damaging or destroying its defensive Buildings as a Battle would
	 * @param damageBuildings how many of the Nation's undamaged non-defensive
	 *        Buildings are Damaged, picked by luck; luck never varies it
	 * @param armyKilled the share of the Units in the Nation's Army that die,
	 *        at least 1 if it has any, picked by luck; at most 1
	 * @param populationLost how much Population the Nation loses
	 */
	public record Harm(int damageDefense, int damageBuildings, double armyKilled, int populationLost) {
	}

	/**
	 * Whether it is a hostile Mission rather than an Intel one.
	 */
	public boolean hostile() {
		return harm != null;
	}

	/**
	 * What does not make sense about the Mission.
	 */
	List<String> problems() {
		List<String> problems = new ArrayList<>();
		String subject = "Mission " + name;
		if (turns < 1) {
			problems.add(subject + " must take at least 1 Turn.");
		}
		if (!hostile()) {
			if (reports == null) {
				problems.add(subject + " reports nothing.");
			}
			if (chance != 0 || text != null) {
				problems.add(subject + " reports a Fact, so it cannot have a chance or text, which belong to hostile Missions.");
			}
			return problems;
		}
		if (reports != null) {
			problems.add(subject + " harms its target, so it cannot also report a Fact.");
		}
		if (!(chance > 0 && chance <= 1)) {
			problems.add(subject + " must have a chance above 0 and at most 1.");
		}
		if (text == null || text.isBlank()) {
			problems.add(subject + " has no text to announce it.");
		}
		if (harm.damageDefense() < 0 || harm.damageBuildings() < 0 || harm.armyKilled() < 0 || harm.populationLost() < 0) {
			problems.add(subject + " cannot harm by less than 0.");
		}
		if (harm.armyKilled() > 1) {
			problems.add(subject + " cannot kill more than the whole Army.");
		}
		if (harm.damageDefense() == 0 && harm.damageBuildings() == 0 && harm.armyKilled() == 0
				&& harm.populationLost() == 0) {
			problems.add(subject + " harms nothing.");
		}
		return problems;
	}

}
