package com.miniciv.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Spy in place at another Nation may be set to do there, for a fixed
 * number of Turns; a Spy on no Mission is Idle.
 *
 * @param name what the Mission is called and chosen by, such as "Report Gold"
 * @param turns how many Turns must resolve before the Mission ends
 * @param reports the kind of Fact the Mission reports, for certain, when it ends
 */
public record Mission(String name, int turns, FactKind reports) {

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
	 * What does not make sense about the Mission.
	 */
	List<String> problems() {
		List<String> problems = new ArrayList<>();
		String subject = "Mission " + name;
		if (turns < 1) {
			problems.add(subject + " must take at least 1 Turn.");
		}
		if (reports == null) {
			problems.add(subject + " reports nothing.");
		}
		return problems;
	}

}
