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
	public record Section(Kind kind, List<Line> lines) {

		public Section {
			lines = List.copyOf(lines);
		}

	}

	/**
	 * One line of a Turn Report: plain text, with each Gold, Population and
	 * Research amount in it marked as it was written.
	 *
	 * @param parts the line's text and amounts, in the order read
	 */
	public record Line(List<Part> parts) {

		public Line {
			parts = List.copyOf(parts);
		}

		/**
		 * A line made of the given pieces in order: each a String, an
		 * {@link Amount} or another Line, whose parts it takes on.
		 */
		static Line of(Object... pieces) {
			List<Part> parts = new ArrayList<>();
			for (Object piece : pieces) {
				switch (piece) {
					case String text -> append(parts, new Text(text));
					case Part part -> append(parts, part);
					case Line line -> line.parts().forEach(part -> append(parts, part));
					default -> throw new IllegalArgumentException("A Turn Report line cannot hold " + piece);
				}
			}
			return new Line(parts);
		}

		/**
		 * Adds a part to a line's, running text on from the text before it.
		 */
		private static void append(List<Part> parts, Part part) {
			if (part instanceof Text text) {
				if (text.text().isEmpty()) {
					return;
				}
				if (!parts.isEmpty() && parts.getLast() instanceof Text before) {
					parts.set(parts.size() - 1, new Text(before.text() + text.text()));
					return;
				}
			}
			parts.add(part);
		}

		/**
		 * Lines run together, such as "12 Gold, 3 Population and 4 Research".
		 */
		static Line joined(List<Line> items) {
			List<Object> pieces = new ArrayList<>();
			for (int i = 0; i < items.size(); i++) {
				if (i > 0) {
					pieces.add(i == items.size() - 1 ? " and " : ", ");
				}
				pieces.add(items.get(i));
			}
			return of(pieces.toArray());
		}

		/**
		 * The line as plain text.
		 */
		public String text() {
			StringBuilder text = new StringBuilder();
			for (Part part : parts) {
				text.append(switch (part) {
					case Text plain -> plain.text();
					case Amount amount -> amount.amount() + " " + amount.resource().named();
				});
			}
			return text.toString();
		}

		/**
		 * The amounts marked in the line, in the order read.
		 */
		public List<Amount> amounts() {
			return parts.stream().filter(Amount.class::isInstance).map(Amount.class::cast).toList();
		}

	}

	/**
	 * A piece of a Turn Report line.
	 */
	public sealed interface Part permits Text, Amount {
	}

	/**
	 * Plain text in a Turn Report line.
	 */
	public record Text(String text) implements Part {
	}

	/**
	 * An amount of a resource in a Turn Report line, such as "20 Gold", and
	 * what it was to the Nation reading it.
	 *
	 * @param amount how much, never negative; the line's wording says which
	 *        way it moved
	 */
	public record Amount(Resource resource, int amount, Effect effect) implements Part {

		/**
		 * What an amount was to the Nation reading of it.
		 */
		public enum Effect {

			/**
			 * The Nation gained it, or its Warparty took it.
			 */
			GAIN,

			/**
			 * The Nation lost or paid it.
			 */
			LOSS,

			/**
			 * It is about another Nation, or nothing changed hands.
			 */
			NEUTRAL

		}

		static Amount gain(Resource resource, int amount) {
			return new Amount(resource, amount, Effect.GAIN);
		}

		static Amount loss(Resource resource, int amount) {
			return new Amount(resource, amount, Effect.LOSS);
		}

		static Amount neutral(Resource resource, int amount) {
			return new Amount(resource, amount, Effect.NEUTRAL);
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
	 * Every line of the Turn Report as plain text, section by section.
	 */
	public List<String> lines() {
		return sections.stream().flatMap(section -> section.lines().stream()).map(Line::text).toList();
	}

	/**
	 * A Turn Report being written as the Turn resolves, each line added to its
	 * section.
	 */
	static final class Draft {

		private final Map<Kind, List<Line>> lines = new EnumMap<>(Kind.class);

		/**
		 * The Nation's Gold just before the Turn started resolving.
		 */
		private int gold;

		/**
		 * The Nation's Population just before the Turn started resolving.
		 */
		private int population;

		/**
		 * The Nation's Research just before the Turn started resolving.
		 */
		private int research;

		void add(Kind kind, String line) {
			add(kind, Line.of(line));
		}

		void add(Kind kind, Line line) {
			lines.computeIfAbsent(kind, k -> new ArrayList<>()).add(line);
		}

		/**
		 * Notes what the Nation has as the Turn starts resolving, for the net
		 * change.
		 */
		void startResolving(Nation nation) {
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
