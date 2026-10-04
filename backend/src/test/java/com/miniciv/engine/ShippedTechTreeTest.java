package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped Tech Tree's later Technologies and what they unlock, played out
 * by the engine.
 */
class ShippedTechTreeTest {

	/**
	 * The shipped Ruleset without its Events, so that none happen while Turns
	 * end.
	 */
	static final Ruleset SHIPPED_WITHOUT_EVENTS;

	static {
		Ruleset shipped = RulesetFiles.load("ruleset");
		SHIPPED_WITHOUT_EVENTS = new Ruleset(shipped.units(), shipped.buildings(), shipped.technologies());
	}

	Game game = new Game(SHIPPED_WITHOUT_EVENTS, Identities.of("Rome", "Carthage"), EVEN_LUCK);

	@Test
	void aKnightsStrengthIsMultipliedWhenItFightsArchers() {
		Battle againstArchers = Battle.fight(List.of(Map.of("Knight", 1)), Map.of("Archer", 3),
				SHIPPED_WITHOUT_EVENTS, EVEN_LUCK);
		Battle againstWarriors = Battle.fight(List.of(Map.of("Knight", 1)), Map.of("Warrior", 3),
				SHIPPED_WITHOUT_EVENTS, EVEN_LUCK);

		assertThat(againstArchers.warpartySurvivors(0)).containsExactly(Map.entry("Knight", 1));
		assertThat(againstArchers.armySurvivors()).isEmpty();
		assertThat(againstWarriors.warpartySurvivors(0)).isEmpty();
	}

	@Test
	void aConstructedCastleAddsItsDefense() {
		BuildingType castle = SHIPPED_WITHOUT_EVENTS.building("Castle").orElseThrow();
		unlockAll("Masonry", "Mathematics", "Construction", "Engineering", "Fortification");
		while (game.viewOf("Rome").gold() < castle.gold()) {
			game.endTurn();
		}
		assertThat(game.viewOf("Rome").defense()).isZero();

		assertThat(game.act("Rome", new Action.StartBuilding("Castle"))).isEqualTo(ActionResult.ACCEPTED);
		for (int turn = 1; turn <= castle.turns(); turn++) {
			game.endTurn();
		}

		assertThat(game.viewOf("Rome").buildings()).containsEntry("Castle", 1);
		assertThat(game.viewOf("Rome").defense()).isEqualTo(25);
	}

	@Test
	void engineeringIsLockedUntilBothMathematicsAndConstructionAreUnlocked() {
		assertThat(standingOf("Engineering")).isEqualTo(TechTreeEntry.Standing.HIDDEN);

		unlockAll("Masonry", "Mathematics");
		assertThat(standingOf("Engineering")).isEqualTo(TechTreeEntry.Standing.LOCKED);

		unlockAll("Construction");
		assertThat(standingOf("Engineering")).isEqualTo(TechTreeEntry.Standing.AVAILABLE);
		assertThat(standingOf("Machinery")).isEqualTo(TechTreeEntry.Standing.LOCKED);
		assertThat(standingOf("Fortification")).isEqualTo(TechTreeEntry.Standing.LOCKED);

		unlockAll("Engineering");
		assertThat(standingOf("Machinery")).isEqualTo(TechTreeEntry.Standing.AVAILABLE);
		assertThat(standingOf("Fortification")).isEqualTo(TechTreeEntry.Standing.AVAILABLE);
	}

	@Test
	void astronomyIsLockedUntilBothPhilosophyAndMathematicsAreUnlocked() {
		unlockAll("Writing", "Philosophy");
		assertThat(standingOf("Astronomy")).isEqualTo(TechTreeEntry.Standing.HIDDEN);

		unlockAll("Masonry");
		assertThat(standingOf("Astronomy")).isEqualTo(TechTreeEntry.Standing.LOCKED);

		unlockAll("Mathematics");
		assertThat(standingOf("Astronomy")).isEqualTo(TechTreeEntry.Standing.AVAILABLE);
	}

	@Test
	void chivalryIsLockedUntilBothIronWorkingAndCurrencyAreUnlocked() {
		unlockAll("Bronze Working", "Iron Working");
		assertThat(standingOf("Chivalry")).isEqualTo(TechTreeEntry.Standing.HIDDEN);

		unlockAll("Writing", "Masonry", "Mathematics");
		assertThat(standingOf("Chivalry")).isEqualTo(TechTreeEntry.Standing.LOCKED);

		unlockAll("Currency");
		assertThat(standingOf("Chivalry")).isEqualTo(TechTreeEntry.Standing.AVAILABLE);
	}

	/**
	 * How Rome stands with a Technology, which is hidden if its name is not in
	 * Rome's view.
	 */
	TechTreeEntry.Standing standingOf(String technology) {
		return game.viewOf("Rome")
			.techTree()
			.stream()
			.filter(entry -> technology.equals(entry.name()))
			.findFirst()
			.map(TechTreeEntry::standing)
			.orElse(TechTreeEntry.Standing.HIDDEN);
	}

	/**
	 * Has Rome unlock the given Technologies in order, ending Turns until it has
	 * the Research for each.
	 */
	void unlockAll(String... technologies) {
		for (String technology : technologies) {
			int research = SHIPPED_WITHOUT_EVENTS.technology(technology).orElseThrow().research();
			while (game.viewOf("Rome").research() < research) {
				game.endTurn();
			}
			assertThat(game.act("Rome", new Action.UnlockTechnology(technology))).as(technology)
				.isEqualTo(ActionResult.ACCEPTED);
		}
	}

}
