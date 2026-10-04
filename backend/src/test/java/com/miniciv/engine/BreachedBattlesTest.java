package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.DamagedBuildingsTest.LIBRARY;
import static com.miniciv.engine.DamagedBuildingsTest.MARKET;
import static com.miniciv.engine.GameTest.MOAT;
import static com.miniciv.engine.GameTest.WALLS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Battles at Breached Nations, whose leftover Strength Damages their
 * non-defensive Buildings as well as reducing their Population.
 */
class BreachedBattlesTest {

	/**
	 * Free of Population and upkeep, with Strength 1, so a Warparty's Strength
	 * is how many it holds.
	 */
	static final UnitType RAM = new UnitType("Ram", 1, 0, 1, 0, 1, List.of());

	/**
	 * Draws 0.99 unless told otherwise, so each Building Damaged is the last
	 * of those still undamaged.
	 */
	final GameTest.Rigged luck = new GameTest.Rigged();

	final Game game = new Game(new Ruleset(List.of(RAM), List.of(MARKET, LIBRARY, WALLS, MOAT), List.of()),
			Identities.of("Rome", "Carthage"), luck);

	void act(String nation, Action action) {
		assertThat(game.act(nation, action)).isEqualTo(ActionResult.ACCEPTED);
	}

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	/**
	 * Has Carthage build the given Buildings, all started at once, and ends
	 * Turns until they are all built.
	 */
	void carthageBuilt(String... buildings) {
		for (String building : buildings) {
			act("Carthage", new Action.StartBuilding(building));
		}
		while (!game.viewOf("Carthage").construction().isEmpty()) {
			game.endTurn();
		}
	}

	/**
	 * Has Rome send a Warparty of the given number of Rams to Carthage, and
	 * ends Turns until the Turn it arrives on, leaving that Turn to be ended.
	 */
	void ramsArrived(int rams) {
		act("Rome", new Action.TrainUnits("Ram", rams));
		game.endTurn();
		act("Rome", new Action.SendWarparty("Carthage", Map.of("Ram", rams)));
		while (game.viewOf("Rome").warparties().getLast().turnsLeft() > 0) {
			game.endTurn();
		}
	}

	/**
	 * Carthage's Population as the Battle ends, had it lost none: what it has
	 * now plus the 2 it grows by each Turn.
	 */
	int carthagePopulationUnharmed() {
		return game.viewOf("Carthage").population() + 2;
	}

	@Test
	void aBattleAtANationWhoseDefensiveBuildingsAllStillStandDamagesNoOtherBuildings() {
		carthageBuilt("Walls", "Market");
		ramsArrived(6);

		game.endTurn();

		assertThat(game.viewOf("Carthage").damage()).containsExactly(BuildingDamage.defensive("Walls", 0, 6, 9, 1, 15, 1));
		assertThat(report("Carthage")).noneMatch(line -> line.contains("Market"));
	}

	@Test
	void atANationBreachedByTheBattleHalfTheLeftoverStrengthDamagesABuildingPerFiveTheRestGoingToPopulation() {
		carthageBuilt("Moat", "Market");
		carthageBuilt("Library");
		ramsArrived(16);
		int population = carthagePopulationUnharmed();

		game.endTurn();

		// 12 left once the Moat is down: 6 Damage one Building, the 1 unused
		// joining the other 6 against Population.
		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.damage()).containsExactly(BuildingDamage.defensive("Moat", 1, 0, 0, 0, 10, 1),
				BuildingDamage.nonDefensive("Library", 1, 10, 2));
		assertThat(carthage.population()).isEqualTo(population - 7);
		assertThat(report("Carthage")).containsSequence(
				"Caesar of Rome's Warparty wore your Defense down from 4 to 0, destroying Moat ×1.",
				"Caesar of Rome's Warparty damaged your Library ×1.",
				"Caesar of Rome's Warparty cost your Nation 7 Population.");
		assertThat(report("Rome")).containsSequence("Your Warparty wore Carthage's Defense down by 4, destroying Moat ×1.",
				"Your Warparty damaged Carthage's Library ×1.", "Your Warparty cost Carthage 7 Population.");
	}

	@Test
	void theOddOneOfTheLeftoverStrengthGoesToPopulationAndEachBuildingDamagedIsPickedByLuck() {
		carthageBuilt("Moat", "Market");
		carthageBuilt("Library");
		ramsArrived(25);
		int population = carthagePopulationUnharmed();
		luck.then(0.5, 0.5, 0);

		game.endTurn();

		// 21 left: 10 Damage two Buildings, 11 go to Population.
		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.damage()).containsExactly(BuildingDamage.defensive("Moat", 1, 0, 0, 0, 10, 1),
				BuildingDamage.nonDefensive("Market", 1, 10, 1), BuildingDamage.nonDefensive("Library", 1, 10, 2));
		assertThat(carthage.population()).isEqualTo(population - 11);
		assertThat(report("Carthage")).contains("Caesar of Rome's Warparty damaged your Market ×1 and Library ×1.");
		assertThat(report("Rome")).contains("Your Warparty damaged Carthage's Market ×1 and Library ×1.");
	}

	@Test
	void strengthLeftOnceNoUndamagedNonDefensiveBuildingIsLeftGoesToPopulation() {
		carthageBuilt("Moat", "Market");
		ramsArrived(24);
		int population = carthagePopulationUnharmed();

		game.endTurn();

		// 20 left: 5 of 10 Damage the only Market, the other 5 unused.
		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.damage()).contains(BuildingDamage.nonDefensive("Market", 1, 10, 1));
		assertThat(carthage.population()).isEqualTo(population - 15);
	}

	@Test
	void allLeftoverStrengthGoesToPopulationAtABreachedNationWhoseOtherBuildingsAreAllDamaged() {
		carthageBuilt("Market");
		ramsArrived(10);
		game.endTurn();
		assertThat(game.viewOf("Carthage").damage()).containsExactly(BuildingDamage.nonDefensive("Market", 1, 10, 1));
		ramsArrived(4);
		int population = carthagePopulationUnharmed();

		game.endTurn();

		assertThat(game.viewOf("Carthage").population()).isEqualTo(population - 8);
		assertThat(report("Carthage")).noneMatch(line -> line.contains("damaged your"));
	}

	@Test
	void aNationWithNoDefensiveBuildingsIsBreached() {
		carthageBuilt("Market");
		ramsArrived(10);
		int population = carthagePopulationUnharmed();

		game.endTurn();

		NationView carthage = game.viewOf("Carthage");
		assertThat(carthage.damage()).containsExactly(BuildingDamage.nonDefensive("Market", 1, 10, 1));
		assertThat(carthage.population()).isEqualTo(population - 10);
		assertThat(report("Carthage")).containsSequence("Caesar of Rome's Warparty damaged your Market ×1.",
				"Caesar of Rome's Warparty cost your Nation 10 Population, doubled as you had no Army or Defense.");
	}

	@Test
	void aNationWithADefensiveBuildingDestroyedInAnEarlierBattleAndNotYetRebuiltIsBreached() {
		carthageBuilt("Moat");
		ramsArrived(4);
		game.endTurn();
		assertThat(game.viewOf("Carthage").damage()).containsExactly(BuildingDamage.defensive("Moat", 1, 0, 0, 0, 10, 1));
		carthageBuilt("Market");
		ramsArrived(10);

		game.endTurn();

		assertThat(game.viewOf("Carthage").damage()).contains(BuildingDamage.nonDefensive("Market", 1, 10, 1));
		assertThat(report("Carthage")).contains("Caesar of Rome's Warparty damaged your Market ×1.");
	}

	@Test
	void aBattleThatLeavesNoStrengthOnceDefenseIsDownDamagesNoOtherBuildings() {
		carthageBuilt("Moat", "Market");
		ramsArrived(4);

		game.endTurn();

		assertThat(game.viewOf("Carthage").damage()).containsExactly(BuildingDamage.defensive("Moat", 1, 0, 0, 0, 10, 1));
	}

}
