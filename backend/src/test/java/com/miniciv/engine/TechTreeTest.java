package com.miniciv.engine;

import java.util.List;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.EVEN_LUCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TechTreeTest {

	static final UnitType WARRIOR = new UnitType("Warrior", 10, 1, 1, 1, 3, List.of());

	static final UnitType HORSEMAN = new UnitType("Horseman", 20, 2, 2, 2, 4, List.of());

	static final BuildingType FARM = new BuildingType("Farm", 30, 2, 1, 0, 0, 0, 0);

	static final BuildingType WALLS = new BuildingType("Walls", 30, 2, 0, 0, 0, 0, 10);

	static final Technology MASONRY = new Technology("Masonry", 6, List.of(), List.of("Walls"));

	static final Technology RIDING = new Technology("Riding", 9, List.of(), List.of());

	static final Technology CHIVALRY = new Technology("Chivalry", 3, List.of("Masonry", "Riding"), List.of("Horseman"));

	static final Ruleset RULESET = new Ruleset(List.of(WARRIOR, HORSEMAN), List.of(FARM, WALLS),
			List.of(MASONRY, RIDING, CHIVALRY));

	static final Ruleset CHAIN = new Ruleset(List.of(WARRIOR), List.of(FARM),
			List.of(new Technology("A", 1, List.of("B"), List.of()), new Technology("B", 1, List.of("C"), List.of()),
					new Technology("C", 1, List.of(), List.of())));

	Game game = new Game(RULESET, Identities.of("Rome", "Carthage"), EVEN_LUCK);

	ActionResult unlock(String technology) {
		return game.act("Rome", new Action.UnlockTechnology(technology));
	}

	/**
	 * Ends Turns until Rome has gained at least the given Research.
	 */
	void researchAtLeast(int research) {
		while (game.viewOf("Rome").research() < research) {
			game.endTurn();
		}
	}

	@Test
	void aPlayerUnlocksATechnologyWithNoPrerequisitesByPayingItsResearchAtOnce() {
		researchAtLeast(6);
		int research = game.viewOf("Rome").research();

		assertThat(unlock("Masonry")).isEqualTo(ActionResult.ACCEPTED);

		NationView rome = game.viewOf("Rome");
		assertThat(rome.research()).isEqualTo(research - 6);
		assertThat(rome.techTree()).extracting(TechTreeEntry::name, TechTreeEntry::standing)
			.containsExactly(tuple("Masonry", TechTreeEntry.Standing.UNLOCKED),
					tuple("Riding", TechTreeEntry.Standing.AVAILABLE),
					tuple("Chivalry", TechTreeEntry.Standing.LOCKED));
	}

	@Test
	void unlockingIsRejectedWithAReasonWhileSomePrerequisitesAreNotUnlocked() {
		researchAtLeast(9);
		unlock("Masonry");
		int research = game.viewOf("Rome").research();

		assertThat(unlock("Chivalry"))
			.isEqualTo(ActionResult.rejected("Chivalry requires Riding to be unlocked first."));

		assertThat(game.viewOf("Rome").research()).isEqualTo(research);
	}

	@Test
	void unlockingIsRejectedWithAReasonWithoutEnoughResearch() {
		assertThat(unlock("Riding"))
			.isEqualTo(ActionResult.rejected("Riding costs 9 Research, but you have only 0."));
		assertThat(game.viewOf("Rome").techTree()).extracting(TechTreeEntry::standing)
			.doesNotContain(TechTreeEntry.Standing.UNLOCKED);
	}

	@Test
	void aTechnologyCanBeUnlockedOnlyOnce() {
		researchAtLeast(12);
		unlock("Masonry");

		assertThat(unlock("Masonry")).isEqualTo(ActionResult.rejected("You have already unlocked Masonry."));
	}

	@Test
	void unlockingAnUnknownTechnologyIsRejected() {
		assertThat(unlock("Writing")).isEqualTo(ActionResult.rejected("There is no Technology called Writing."));
	}

	@Test
	void aBuildingATechnologyUnlocksCannotBeStartedUntilItIsUnlocked() {
		assertThat(game.viewOf("Rome").availableBuildings()).containsExactly(FARM);
		assertThat(game.act("Rome", new Action.StartBuilding("Walls")))
			.isEqualTo(ActionResult.rejected("Walls requires Masonry to be unlocked first."));
		assertThat(game.viewOf("Rome").gold()).isEqualTo(50);

		researchAtLeast(6);
		unlock("Masonry");

		assertThat(game.viewOf("Rome").availableBuildings()).containsExactly(FARM, WALLS);
		assertThat(game.act("Rome", new Action.StartBuilding("Walls"))).isEqualTo(ActionResult.ACCEPTED);
	}

	@Test
	void aUnitTypeATechnologyUnlocksCannotBeTrainedUntilItIsUnlocked() {
		assertThat(game.viewOf("Rome").availableUnits()).containsExactly(WARRIOR);
		assertThat(game.act("Rome", new Action.TrainUnits("Horseman", 1)))
			.isEqualTo(ActionResult.rejected("Horseman requires Chivalry to be unlocked first."));

		researchAtLeast(18);
		unlock("Masonry");
		unlock("Riding");
		unlock("Chivalry");

		assertThat(game.viewOf("Rome").availableUnits()).containsExactly(WARRIOR, HORSEMAN);
		assertThat(game.act("Rome", new Action.TrainUnits("Horseman", 1))).isEqualTo(ActionResult.ACCEPTED);
	}

	@Test
	void aNationSeesOnlyOneStepBeyondWhatItCanUnlockNow() {
		game = new Game(CHAIN, Identities.of("Rome", "Carthage"), EVEN_LUCK);

		assertThat(game.viewOf("Rome").techTree()).extracting(TechTreeEntry::name, TechTreeEntry::standing)
			.containsExactly(tuple(null, TechTreeEntry.Standing.HIDDEN), tuple("B", TechTreeEntry.Standing.LOCKED),
					tuple("C", TechTreeEntry.Standing.AVAILABLE));

		researchAtLeast(1);
		unlock("C");

		assertThat(game.viewOf("Rome").techTree()).extracting(TechTreeEntry::name, TechTreeEntry::standing)
			.containsExactly(tuple("A", TechTreeEntry.Standing.LOCKED), tuple("B", TechTreeEntry.Standing.AVAILABLE),
					tuple("C", TechTreeEntry.Standing.UNLOCKED));
	}

	@Test
	void aHiddenTechnologyIsAnAnonymousPlaceholder() {
		game = new Game(CHAIN, Identities.of("Rome", "Carthage"), EVEN_LUCK);

		assertThat(game.viewOf("Rome").techTree().get(0))
			.isEqualTo(new TechTreeEntry(null, 0, List.of(), List.of(), TechTreeEntry.Standing.HIDDEN));
	}

	@Test
	void aTechnologyWithAnyHiddenPrerequisiteIsHidden() {
		game = new Game(new Ruleset(List.of(WARRIOR, HORSEMAN), List.of(FARM, WALLS), List.of(MASONRY, RIDING, CHIVALRY,
				new Technology("Engineering", 1, List.of("Chivalry"), List.of()),
				new Technology("Siege", 1, List.of("Masonry", "Engineering"), List.of()))),
				Identities.of("Rome", "Carthage"), EVEN_LUCK);
		researchAtLeast(6);
		unlock("Masonry");

		assertThat(game.viewOf("Rome").techTree()).extracting(TechTreeEntry::standing)
			.containsExactly(TechTreeEntry.Standing.UNLOCKED, TechTreeEntry.Standing.AVAILABLE,
					TechTreeEntry.Standing.LOCKED, TechTreeEntry.Standing.HIDDEN, TechTreeEntry.Standing.HIDDEN);
	}

	@Test
	void eachNationUnlocksTechnologiesForItselfAlone() {
		researchAtLeast(6);
		unlock("Masonry");

		assertThat(game.viewOf("Carthage").techTree()).extracting(TechTreeEntry::standing)
			.doesNotContain(TechTreeEntry.Standing.UNLOCKED);
	}

}
