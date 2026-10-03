package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped Tech Tree's later Technologies and what they unlock, played out
 * by the engine without the shipped Events.
 */
class ShippedTechTreeTest {

	static final Ruleset SHIPPED = RulesetFiles.load("ruleset");

	static final Ruleset RULESET = new Ruleset(SHIPPED.units(), SHIPPED.buildings(), SHIPPED.technologies());

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage"), EVEN_LUCK);

	@Test
	void theShippedTechTreeHasFourteenTechnologiesUnlockingTheNewBuildingsAndUnits() {
		assertThat(SHIPPED.technologies()).hasSize(14);
		assertThat(SHIPPED.unlockedBy("Harbour")).map(Technology::name).contains("Sailing");
		assertThat(SHIPPED.unlockedBy("Aqueduct")).map(Technology::name).contains("Construction");
		assertThat(SHIPPED.unlockedBy("University")).map(Technology::name).contains("Philosophy");
		assertThat(SHIPPED.unlockedBy("Catapult")).map(Technology::name).contains("Engineering");
		assertThat(SHIPPED.unlockedBy("Observatory")).map(Technology::name).contains("Astronomy");
		assertThat(SHIPPED.unlockedBy("Knight")).map(Technology::name).contains("Chivalry");
		assertThat(SHIPPED.unlockedBy("Crossbowman")).map(Technology::name).contains("Machinery");
		assertThat(SHIPPED.unlockedBy("Castle")).map(Technology::name).contains("Fortification");
	}

	@Test
	void theNewBuildingsAddWhatTheirTechnologyPromises() {
		assertThat(SHIPPED.building("Harbour")).map(BuildingType::goldPerTurn).contains(5);
		assertThat(SHIPPED.building("Aqueduct")).map(BuildingType::populationPerTurn).contains(4);
		assertThat(SHIPPED.building("University")).map(BuildingType::researchPerTurn).contains(4);
		assertThat(SHIPPED.building("Observatory")).map(BuildingType::researchPerTurn).contains(3);
		assertThat(SHIPPED.building("Castle")).map(BuildingType::defense).contains(25);
	}

	@Test
	void theCatapultCountersNothingButTrainsSlowerAndCostsMoreUpkeepThanAnyOtherUnit() {
		UnitType catapult = SHIPPED.unit("Catapult").orElseThrow();

		assertThat(catapult.strength()).isEqualTo(6);
		assertThat(catapult.counters()).isEmpty();
		assertThat(SHIPPED.units()).filteredOn(unit -> unit != catapult).allSatisfy(unit -> {
			assertThat(unit.turns()).as(unit.name()).isLessThan(catapult.turns());
			assertThat(unit.upkeep()).as(unit.name()).isLessThan(catapult.upkeep());
		});
	}

	@Test
	void theCrossbowmanCountersKnightsAndHorsemen() {
		assertThat(SHIPPED.unit("Crossbowman")).map(UnitType::strength).contains(5.0);
		assertThat(SHIPPED.unit("Crossbowman")).map(UnitType::counters).contains(List.of("Knight", "Horseman"));
	}

	@Test
	void aKnightsStrengthIsMultipliedWhenItFightsArchers() {
		Battle againstArchers = Battle.fight(List.of(Map.of("Knight", 1)), Map.of("Archer", 3), RULESET, EVEN_LUCK);
		Battle againstWarriors = Battle.fight(List.of(Map.of("Knight", 1)), Map.of("Warrior", 3), RULESET, EVEN_LUCK);

		assertThat(SHIPPED.unit("Knight")).map(UnitType::strength).contains(7.0);
		assertThat(againstArchers.warpartySurvivors(0)).containsExactly(Map.entry("Knight", 1));
		assertThat(againstArchers.armySurvivors()).isEmpty();
		assertThat(againstWarriors.warpartySurvivors(0)).isEmpty();
	}

	@Test
	void aConstructedCastleAddsItsDefense() {
		BuildingType castle = SHIPPED.building("Castle").orElseThrow();
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
		assertThat(standingOf("Engineering")).isEqualTo(TechTreeEntry.Standing.LOCKED);

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
		assertThat(standingOf("Astronomy")).isEqualTo(TechTreeEntry.Standing.LOCKED);

		unlockAll("Masonry", "Mathematics");
		assertThat(standingOf("Astronomy")).isEqualTo(TechTreeEntry.Standing.AVAILABLE);
	}

	@Test
	void chivalryIsLockedUntilBothIronWorkingAndCurrencyAreUnlocked() {
		unlockAll("Bronze Working", "Iron Working");
		assertThat(standingOf("Chivalry")).isEqualTo(TechTreeEntry.Standing.LOCKED);

		unlockAll("Writing", "Masonry", "Mathematics", "Currency");
		assertThat(standingOf("Chivalry")).isEqualTo(TechTreeEntry.Standing.AVAILABLE);
	}

	TechTreeEntry.Standing standingOf(String technology) {
		return game.viewOf("Rome")
			.techTree()
			.stream()
			.filter(entry -> entry.name().equals(technology))
			.findFirst()
			.orElseThrow()
			.standing();
	}

	/**
	 * Has Rome unlock the given Technologies in order, ending Turns until it has
	 * the Research for each.
	 */
	void unlockAll(String... technologies) {
		for (String technology : technologies) {
			int research = RULESET.technology(technology).orElseThrow().research();
			while (game.viewOf("Rome").research() < research) {
				game.endTurn();
			}
			assertThat(game.act("Rome", new Action.UnlockTechnology(technology))).as(technology)
				.isEqualTo(ActionResult.ACCEPTED);
		}
	}

}
