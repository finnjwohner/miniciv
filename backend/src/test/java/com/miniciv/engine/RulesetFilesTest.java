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
	void everyShippedDilemmaChoiceCanTurnOutMoreThanOneWay() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.events()).filteredOn(Event::dilemma)
			.flatExtracting(Event::choices)
			.allSatisfy(choice -> assertThat(choice.outcomes()).as(choice.name()).hasSizeGreaterThanOrEqualTo(2));
	}

	@Test
	void theShippedBankAddsHalfAGoldPerPopulation() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.building("Bank")).map(BuildingType::goldPerPopulation).contains(0.5);
	}

	@Test
	void theShippedMarketAndLibraryAddFlatGoldAndResearchAndTheGranaryThreePopulation() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.building("Market")).contains(new BuildingType("Market", 40, 2, 0, 3, 0, 0, 0));
		assertThat(ruleset.building("Library")).contains(new BuildingType("Library", 50, 3, 0, 0, 0, 2, 0));
		assertThat(ruleset.building("Granary")).contains(new BuildingType("Granary", 60, 3, 3, 0, 0, 0, 0));
		assertThat(ruleset.building("Farm")).map(BuildingType::goldPerTurn).contains(0);
		assertThat(ruleset.building("Farm")).map(BuildingType::researchPerTurn).contains(0);
		assertThat(ruleset.unlockedBy("Library")).map(Technology::name).contains("Writing");
		assertThat(ruleset.unlockedBy("Spy")).map(Technology::name).contains("Writing");
		assertThat(ruleset.unlockedBy("Market")).isEmpty();
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
		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> RulesetFiles.load("rulesets/misspelt-research-field"))
			.withMessageContaining("rulesets/misspelt-research-field/buildings.json")
			.withMessageContaining("researchPerTurns");
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
	void aChoiceWhoseOutcomeChancesDoNotTotalOneFailsToLoad() {
		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> RulesetFiles.load("rulesets/outcome-chances"))
			.withMessageContaining("Event Bandits's choice Hunt them down has outcome chances totalling 0.9, not 1");
	}

	@Test
	void aMalformedOutcomeFailsToLoadNamingTheFile() {
		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> RulesetFiles.load("rulesets/malformed-outcome"))
			.withMessageContaining("rulesets/malformed-outcome/events.json")
			.withMessageContaining("odds");
	}

	@Test
	void aMissingFileFailsToLoadNamingTheFile() {
		assertThatExceptionOfType(InvalidRuleset.class).isThrownBy(() -> RulesetFiles.load("rulesets/nowhere"))
			.withMessageContaining("rulesets/nowhere/units.json");
	}

}
