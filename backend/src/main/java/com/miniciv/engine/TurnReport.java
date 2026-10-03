package com.miniciv.engine;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What happened to one Nation as a Turn resolved, told to its Player at the
 * start of the next Turn, grouped into sections by kind.
 *
 * @param turn the Turn that resolved
 * @param netChange how the Nation's resources moved as the Turn resolved
 * @param sections the sections with something in them, in the order of
 *        {@link Kind}
 */
public record TurnReport(int turn, NetChange netChange, List<Section> sections) {

	/**
	 * The kinds of section a Turn Report is grouped into, in the order they
	 * are told.
	 */
	public enum Kind {
		/**
		 * Dilemmas waiting, enemy Warparties arriving, Nations conquered or
		 * removed, and the Game won.
		 */
		DECISIONS_AND_ALERTS,
		/**
		 * Battles, Plunder, Encounters, Tribute, Warparties arriving, returning,
		 * Recalled or turned back.
		 */
		WARFARE,
		/**
		 * Buildings built, repaired or rebuilt, and Units trained.
		 */
		COMPLETED,
		/**
		 * Events other than Dilemmas, and Dilemma outcomes.
		 */
		EVENTS,
		/**
		 * Spy reports, arrivals and returns, captures, confessions, Scout
		 * discoveries, and Spies turned back.
		 */
		SPIES_AND_SCOUTS,
		/**
		 * Income, upkeep and desertions.
		 */
		RESOURCES
	}

	/**
	 * One section of a Turn Report.
	 *
	 * @param kind what the section tells of
	 * @param lines what happened, in the order it happened
	 */
	public record Section(Kind kind, List<String> lines) {

		public Section {
			lines = List.copyOf(lines);
		}

	}

	/**
	 * How a Nation's Gold, Population and Research moved as a Turn resolved:
	 * what it had once the Turn resolved less what it had just before, so what
	 * it spent during the Turn never counts.
	 */
	public record NetChange(int gold, int population, int research) {
	}

	public TurnReport {
		sections = List.copyOf(sections);
	}

	/**
	 * Every line of the Turn Report, section by section.
	 */
	public List<String> lines() {
		return sections.stream().flatMap(section -> section.lines().stream()).toList();
	}

	/**
	 * A Turn Report being written as the Turn resolves, each line added to its
	 * section.
	 */
	static final class Draft {

		private final Map<Kind, List<String>> lines = new EnumMap<>(Kind.class);

		/**
		 * The Nation's Gold, Population and Research just before the Turn
		 * started resolving.
		 */
		private int gold;

		private int population;

		private int research;

		void add(Kind kind, String line) {
			lines.computeIfAbsent(kind, k -> new ArrayList<>()).add(line);
		}

		/**
		 * Notes what the Nation has as the Turn starts resolving, for the net
		 * change.
		 */
		void resolving(Nation nation) {
			gold = nation.gold();
			population = nation.population();
			research = nation.research();
		}

		/**
		 * The Turn Report of the given Turn, now that it has resolved for the
		 * Nation.
		 */
		TurnReport of(int turn, Nation nation) {
			NetChange netChange = new NetChange(nation.gold() - gold, nation.population() - population,
					nation.research() - research);
			return new TurnReport(turn, netChange,
					lines.entrySet().stream().map(entry -> new Section(entry.getKey(), entry.getValue())).toList());
		}

	}

}
