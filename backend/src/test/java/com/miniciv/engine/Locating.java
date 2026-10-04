package com.miniciv.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import static com.miniciv.engine.ScoutsTest.draw;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Games whose Nations have Located other Nations before a test plays on, so
 * they can send Spies and Warparties there. Each locating Nation trains free
 * Pathfinders, Scouts costing no upkeep, on the first Turn and sets each to
 * Locate one Nation on the second, through Actions like any Player; scripted
 * luck makes every Locate Task succeed at once and nothing else happen. The
 * test's own luck takes over from the third Turn, untouched. The Pathfinders
 * stay on, Idle.
 */
final class Locating {

	static final UnitType PATHFINDER = new UnitType("Pathfinder", 0, 0, 1, 0, 0, List.of(), true);

	private final Ruleset ruleset;

	private final List<NationIdentity> nations;

	private final RandomGenerator luck;

	private final Map<String, List<String>> locating = new LinkedHashMap<>();

	private Locating(Ruleset ruleset, List<NationIdentity> nations, RandomGenerator luck) {
		this.ruleset = ruleset;
		this.nations = nations;
		this.luck = luck;
	}

	/**
	 * A Game in which the given Nations will Locate others before it is
	 * handed over; see {@link #locate}.
	 */
	static Locating in(Ruleset ruleset, List<NationIdentity> nations, RandomGenerator luck) {
		return new Locating(ruleset, nations, luck);
	}

	/**
	 * A Game, on its third Turn, in which every Nation has Located every
	 * other.
	 */
	static Game everyone(Ruleset ruleset, List<NationIdentity> nations, RandomGenerator luck) {
		Locating locating = in(ruleset, nations, luck);
		for (NationIdentity nation : nations) {
			locating.locate(nation.name(), nations.stream()
				.map(NationIdentity::name)
				.filter(other -> !other.equals(nation.name()))
				.toArray(String[]::new));
		}
		return locating.game();
	}

	/**
	 * A Game, on its third Turn, in which one Nation has Located every other,
	 * and only it has Pathfinders, which may discover the others' Warparties
	 * and catch their travelling Spies.
	 */
	static Game by(String nation, Ruleset ruleset, List<NationIdentity> nations, RandomGenerator luck) {
		return in(ruleset, nations, luck).locate(nation, nations.stream()
			.map(NationIdentity::name)
			.filter(other -> !other.equals(nation))
			.toArray(String[]::new))
			.game();
	}

	/**
	 * Has a Nation Locate the given Nations.
	 */
	Locating locate(String nation, String... targets) {
		locating.computeIfAbsent(nation, n -> new ArrayList<>()).addAll(List.of(targets));
		return this;
	}

	/**
	 * The Game, on its third Turn, with the Nations Located; the ruleset has
	 * Pathfinders added.
	 */
	Game game() {
		List<UnitType> units = new ArrayList<>(ruleset.units());
		units.add(PATHFINDER);
		Opening opening = new Opening(luck);
		Game game = new Game(new Ruleset(units, ruleset.buildings(), ruleset.technologies(), ruleset.events(),
				ruleset.missions()), nations,
				opening);
		locating.forEach((nation, targets) -> accepted(game.act(nation, new Action.TrainUnits("Pathfinder", targets.size()))));
		game.endTurn();
		locating.forEach((nation, targets) -> targets
			.forEach(target -> accepted(game.act(nation, new Action.SetTask("LOCATE", target, 1)))));
		opening.succeed(locating.values().stream().mapToInt(List::size).sum());
		game.endTurn();
		locating.forEach((nation, targets) -> assertThat(game.viewOf(nation).located()).containsAll(targets));
		opening.over = true;
		return game;
	}

	private static void accepted(ActionResult result) {
		assertThat(result).isEqualTo(ActionResult.ACCEPTED);
	}

	/**
	 * Luck that, until the opening is over, makes nothing happen but the
	 * Locate Tasks it is told to make succeed, then draws the test's luck.
	 */
	private static final class Opening implements RandomGenerator {

		private final RandomGenerator luck;

		private int succeeding;

		private boolean over;

		Opening(RandomGenerator luck) {
			this.luck = luck;
		}

		/**
		 * Makes the next draws, which the Locate Tasks make first as a Turn
		 * resolves, succeed.
		 */
		void succeed(int tasks) {
			succeeding = tasks;
		}

		@Override
		public long nextLong() {
			if (over) {
				return luck.nextLong();
			}
			if (succeeding > 0) {
				succeeding--;
				return draw(0);
			}
			return draw(0.99);
		}

	}

}
