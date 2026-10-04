package com.miniciv.engine;

import java.util.List;

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
	void theShippedRulesetHasScoutEventsThatLoseScoutsAndAScoutDilemma() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.events()).filteredOn(event -> event.subject() == Event.Subject.SCOUTS)
			.extracting(Event::name)
			.contains("Blizzard", "Abandoned camp", "Ancient ruins", "Strange tracks");
		assertThat(ruleset.events()).filteredOn(event -> event.name().equals("Blizzard"))
			.extracting(event -> event.effects().scoutsLost())
			.containsExactly(1);
	}

	@Test
	void everyShippedDilemmaChoiceCanTurnOutMoreThanOneWay() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.events()).filteredOn(Event::dilemma)
			.flatExtracting(Event::choices)
			.allSatisfy(choice -> assertThat(choice.outcomes()).as(choice.name()).hasSizeGreaterThanOrEqualTo(2));
	}

	@Test
	void theShippedRulesetHasTheLaterGameEventsAndDilemmas() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.events()).filteredOn(event -> !event.dilemma())
			.extracting(Event::name)
			.contains("Comet sighting", "Bountiful fishing", "Royal wedding", "Gold rush", "Famine", "Great fire",
					"Favourable winds", "Blocked mountain pass");
		assertThat(ruleset.events()).filteredOn(Event::dilemma)
			.extracting(Event::name)
			.contains("Border dispute", "Drought", "Festival petition", "Foreign envoy", "Deserter's secrets",
					"Cracked foundations");
	}

	/**
	 * Rough worth of each effect in Gold, for weighing Dilemma choices against
	 * each other: a Population is worth the Gold it earns over a few Turns,
	 * a Research about three Gold, a point of Defense lost about six
	 * Gold to repair or rebuild, and a Scout lost about twenty Gold to train
	 * again.
	 */
	static double worth(Event.Effects effects) {
		return effects.gold() + 6.0 * effects.population() + 3.0 * effects.research() - 6.0 * effects.damageDefense()
				- 20.0 * effects.scoutsLost();
	}

	static double expectedWorth(Event.Choice choice) {
		return choice.outcomes().stream().mapToDouble(outcome -> outcome.chance() * worth(outcome.effects())).sum();
	}

	@Test
	void everyShippedDilemmasWorstChoiceIsOnBalanceWorseThanEachOtherChoice() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.events()).filteredOn(Event::dilemma).allSatisfy(dilemma -> {
			double worst = expectedWorth(dilemma.worstChoice());
			assertThat(dilemma.choices()).filteredOn(choice -> !choice.worst())
				.allSatisfy(choice -> assertThat(expectedWorth(choice)).as(dilemma.name() + ": " + choice.name())
					.isGreaterThan(worst));
		});
	}

	@Test
	void theShippedRulesetHasNoStragglingWarpartyAsBigWarpartiesAlreadyTravelLonger() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.events()).extracting(Event::name).doesNotContain("Straggling Warparty");
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
	void theShippedTechTreeHasFourteenTechnologies() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.technologies()).hasSize(14);
		assertThat(ruleset.unlockedBy("Harbour")).map(Technology::name).contains("Sailing");
		assertThat(ruleset.unlockedBy("Aqueduct")).map(Technology::name).contains("Construction");
		assertThat(ruleset.unlockedBy("University")).map(Technology::name).contains("Philosophy");
		assertThat(ruleset.unlockedBy("Catapult")).map(Technology::name).contains("Engineering");
		assertThat(ruleset.unlockedBy("Observatory")).map(Technology::name).contains("Astronomy");
		assertThat(ruleset.unlockedBy("Knight")).map(Technology::name).contains("Chivalry");
		assertThat(ruleset.unlockedBy("Crossbowman")).map(Technology::name).contains("Machinery");
		assertThat(ruleset.unlockedBy("Castle")).map(Technology::name).contains("Fortification");
	}

	@Test
	void theShippedHarbourAqueductUniversityObservatoryAndCastleAddWhatTheirTechnologyPromises() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.building("Harbour")).map(BuildingType::goldPerTurn).contains(5);
		assertThat(ruleset.building("Aqueduct")).map(BuildingType::populationPerTurn).contains(4);
		assertThat(ruleset.building("University")).map(BuildingType::researchPerTurn).contains(4);
		assertThat(ruleset.building("Observatory")).map(BuildingType::researchPerTurn).contains(3);
		assertThat(ruleset.building("Castle")).map(BuildingType::defense).contains(25);
	}

	@Test
	void theShippedCatapultCountersNothingButTrainsSlowerAndCostsMoreUpkeepThanTheKnight() {
		Ruleset ruleset = RulesetFiles.load("ruleset");
		UnitType catapult = ruleset.unit("Catapult").orElseThrow();
		UnitType knight = ruleset.unit("Knight").orElseThrow();

		assertThat(catapult.strength()).isEqualTo(6);
		assertThat(catapult.counters()).isEmpty();
		assertThat(catapult.turns()).isGreaterThan(knight.turns());
		assertThat(catapult.upkeep()).isGreaterThan(knight.upkeep());
	}

	@Test
	void theShippedKnightCountersArchersAndTheCrossbowmanCountersKnightsAndHorsemen() {
		Ruleset ruleset = RulesetFiles.load("ruleset");

		assertThat(ruleset.unit("Knight")).contains(
				new UnitType("Knight", 65, 3, 3, 3, 7, List.of("Archer")));
		assertThat(ruleset.unit("Crossbowman")).map(UnitType::strength).contains(5.0);
		assertThat(ruleset.unit("Crossbowman")).map(UnitType::counters).contains(List.of("Knight", "Horseman"));
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

	@Test
	void anEventGainingOrLosingAnUnknownUnitTypeFailsToLoad() {
		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> RulesetFiles.load("rulesets/unknown-effect-unit"))
			.withMessageContaining("Event Volunteers gains or loses Lancer, which is not a Unit type");
	}

	@Test
	void anEventWithAMalformedNewEffectFailsToLoadNamingTheFile() {
		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> RulesetFiles.load("rulesets/malformed-effect"))
			.withMessageContaining("rulesets/malformed-effect/events.json")
			.withMessageContaining("everyone");
	}

}
