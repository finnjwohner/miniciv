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
		assertThat(ruleset.units()).filteredOn(UnitType::scout).extracting(UnitType::name).containsExactly("Scout");
		assertThat(ruleset.units()).filteredOn(UnitType::spy).extracting(UnitType::name).containsExactly("Spy");
		assertThat(ruleset.buildings()).extracting(BuildingType::name).contains("Farm", "Granary", "Bank");
		assertThat(ruleset.technologies()).extracting(Technology::name)
			.contains("Bronze Working", "Masonry", "Iron Working", "Mathematics");
		assertThat(ruleset.unlockedBy("Walls")).map(Technology::name).contains("Mathematics");
		assertThat(ruleset.unlockedBy("Warrior")).isEmpty();
		assertThat(ruleset.events()).extracting(Event::name)
			.contains("Good harvest", "Plague", "Dust storm", "Rumours of war", "Outbreak");
		assertThat(ruleset.events()).hasSizeGreaterThanOrEqualTo(15);
		assertThat(ruleset.events()).filteredOn(Event::dilemma).hasSizeGreaterThanOrEqualTo(5);
		assertThat(ruleset.events()).filteredOn(event -> event.subject() == Event.Subject.WARPARTY)
			.hasSizeGreaterThanOrEqualTo(3);
	}

	@Test
	void theShippedBankAddsHalfAGoldPerPopulation() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.building("Bank")).map(BuildingType::goldPerPopulation).contains(0.5);
	}

	@Test
	void contentReferringToAnUnknownTypeFailsToLoad() {
		assertThatExceptionOfType(InvalidRuleset.class).isThrownBy(() -> RulesetFiles.load("rulesets/unknown-counter"))
			.withMessageContaining("Unit type Warrior counters Lancer, which is not a Unit type");
	}

	@Test
	void aTechTreeWithAPrerequisiteThatIsNotATechnologyFailsToLoad() {
		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> RulesetFiles.load("rulesets/unknown-prerequisite"))
			.withMessageContaining("Technology Iron Working requires Bronze Working, which is not a Technology");
	}

	@Test
	void aMisspeltFieldFailsToLoadNamingTheFile() {
		assertThatExceptionOfType(InvalidRuleset.class).isThrownBy(() -> RulesetFiles.load("rulesets/misspelt-field"))
			.withMessageContaining("rulesets/misspelt-field/buildings.json")
			.withMessageContaining("populatonPerTurn");
	}

	@Test
	void anEventWithAnUnknownConditionOrSubjectFailsToLoadNamingTheFile() {
		assertThatExceptionOfType(InvalidRuleset.class).isThrownBy(() -> RulesetFiles.load("rulesets/unknown-condition"))
			.withMessageContaining("rulesets/unknown-condition/events.json")
			.withMessageContaining("omensAtLeast");
		assertThatExceptionOfType(InvalidRuleset.class).isThrownBy(() -> RulesetFiles.load("rulesets/unknown-subject"))
			.withMessageContaining("rulesets/unknown-subject/events.json")
			.withMessageContaining("Spy");
	}

	@Test
	void aMissingFileFailsToLoadNamingTheFile() {
		assertThatExceptionOfType(InvalidRuleset.class).isThrownBy(() -> RulesetFiles.load("rulesets/nowhere"))
			.withMessageContaining("rulesets/nowhere/units.json");
	}

}
