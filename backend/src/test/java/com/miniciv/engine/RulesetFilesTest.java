package com.miniciv.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class RulesetFilesTest {

	@Test
	void theRealContentLoadsCleanly() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.units()).extracting(UnitType::name)
			.contains("Warrior", "Archer", "Horseman", "Scout", "Spy");
		assertThat(ruleset.buildings()).extracting(BuildingType::name).contains("Farm", "Granary", "Bank");
		assertThat(ruleset.technologies()).isNotEmpty();
	}

	@Test
	void contentReferringToAnUnknownTypeFailsToLoad() {
		assertThatExceptionOfType(InvalidRuleset.class).isThrownBy(() -> RulesetFiles.load("rulesets/unknown-counter"))
			.withMessageContaining("Unit type Warrior counters Lancer, which is not a Unit type");
	}

	@Test
	void aMisspeltFieldFailsToLoadNamingTheFile() {
		assertThatExceptionOfType(InvalidRuleset.class).isThrownBy(() -> RulesetFiles.load("rulesets/misspelt-field"))
			.withMessageContaining("rulesets/misspelt-field/buildings.json")
			.withMessageContaining("populatonPerTurn");
	}

	@Test
	void aMissingFileFailsToLoadNamingTheFile() {
		assertThatExceptionOfType(InvalidRuleset.class).isThrownBy(() -> RulesetFiles.load("rulesets/nowhere"))
			.withMessageContaining("rulesets/nowhere/units.json");
	}

}
