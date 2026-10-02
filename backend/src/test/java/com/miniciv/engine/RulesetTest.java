package com.miniciv.engine;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class RulesetTest {

	static UnitType unit(String name, String... counters) {
		return new UnitType(name, 10, 1, 1, 1, 2, List.of(counters));
	}

	@Test
	void aUnitTypeCannotCounterAnUnknownUnitType() {
		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(unit("Warrior", "Lancer")), List.of(), List.of()))
			.withMessageContaining("Unit type Warrior counters Lancer, which is not a Unit type");
	}

	@Test
	void aTechnologyCannotRequireAnUnknownTechnology() {
		Technology mathematics = new Technology("Mathematics", 20, List.of("Writing"), List.of());

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(mathematics)))
			.withMessageContaining("Technology Mathematics requires Writing, which is not a Technology");
	}

	@Test
	void aTechnologyCannotUnlockSomethingThatIsNeitherAUnitNorABuildingType() {
		Technology masonry = new Technology("Masonry", 10, List.of(), List.of("Walls"));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(unit("Warrior")), List.of(), List.of(masonry)))
			.withMessageContaining("Technology Masonry unlocks Walls, which is neither a Unit nor a Building type");
	}

	@Test
	void twoUnitOrBuildingTypesCannotShareAName() {
		BuildingType farm = new BuildingType("Farm", 30, 2, 1, 0, 0);

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(unit("Farm")), List.of(farm), List.of()))
			.withMessageContaining("There is more than one Unit or Building type called Farm");
	}

	@Test
	void everyUnitBuildingAndTechnologyHasAName() {
		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(unit(" ")), List.of(new BuildingType(null, 30, 2, 1, 0, 0)),
					List.of(new Technology("", 10, List.of(), List.of()))))
			.withMessageContaining("A Unit type has no name")
			.withMessageContaining("A Building type has no name")
			.withMessageContaining("A Technology has no name");
	}

	@Test
	void buildingsAndUnitsTakeAtLeastOneTurnAndCostOrAddNothingNegative() {
		BuildingType farm = new BuildingType("Farm", -5, 0, 1, 0, -1);
		UnitType warrior = new UnitType("Warrior", 10, -1, 0, 1, 2, List.of());

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(warrior), List.of(farm), List.of()))
			.withMessageContaining("Building type Farm must take at least 1 Turn to construct")
			.withMessageContaining("Building type Farm cannot cost less than 0 Gold")
			.withMessageContaining("Building type Farm cannot add less than 0 Defense")
			.withMessageContaining("Unit type Warrior must take at least 1 Turn to train")
			.withMessageContaining("Unit type Warrior cannot cost less than 0 Population");
	}

}
