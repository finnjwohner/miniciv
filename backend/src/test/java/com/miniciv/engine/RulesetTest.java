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

	static Event event(String name, Event.Subject subject, double chance, Event.Conditions when, Event.Effects effects) {
		return new Event(name, subject, chance, when, effects, "Something happened");
	}

	@Test
	void anEventMustHappenToANationOrAWarpartyWithAChanceAboveZeroAndAtMostOne() {
		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(),
					List.of(event("Plague", null, 0.1, null, null),
							event("Good harvest", Event.Subject.NATION, 0, null, null),
							event("Dust storm", Event.Subject.WARPARTY, 1.5, null, null))))
			.withMessageContaining("Event Plague must happen to a Nation or a Warparty")
			.withMessageContaining("Event Good harvest must have a chance above 0 and at most 1")
			.withMessageContaining("Event Dust storm must have a chance above 0 and at most 1");
	}

	@Test
	void everyEventHasAUniqueNameAndATextToAnnounceIt() {
		Event blank = new Event(" ", Event.Subject.NATION, 0.1, null, null, "Something happened");
		Event untold = new Event("Plague", Event.Subject.NATION, 0.1, null, null, "");
		Event plague = event("Plague", Event.Subject.NATION, 0.1, null, null);

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(blank, untold, plague)))
			.withMessageContaining("An Event has no name")
			.withMessageContaining("Event Plague has no text to announce it")
			.withMessageContaining("There is more than one Event called Plague");
	}

	@Test
	void anEventsConditionsAndEffectsMustSuitWhatItHappensTo() {
		Event storm = event("Dust storm", Event.Subject.WARPARTY, 0.1, new Event.Conditions(null, 10, 8, null),
				new Event.Effects(5, -1, 2, 3, 1));
		Event harvest = event("Good harvest", Event.Subject.NATION, 0.1, new Event.Conditions(null, null, null, 4),
				new Event.Effects(0, 4, 0, 0, 2));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(storm, harvest)))
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot need populationAtLeast")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot need approachingUnitsAtLeast")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot change gold")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot change population")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot change research")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot change damageDefense")
			.withMessageContaining("Event Good harvest happens to a Nation, so it cannot need unitsAtLeast")
			.withMessageContaining("Event Good harvest happens to a Nation, so it cannot change travelTurns");
	}

	@Test
	void anEventCannotNeedLessThanZeroOrDamageDefenseByLessThanZero() {
		Event quake = event("Earthquake", Event.Subject.NATION, 0.1, new Event.Conditions(-1, -2, -3, null),
				new Event.Effects(0, 0, 0, -4, 0));
		Event storm = event("Dust storm", Event.Subject.WARPARTY, 0.1, new Event.Conditions(null, null, null, -1),
				new Event.Effects(0, 0, 0, 0, 1));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(quake, storm)))
			.withMessageContaining("Event Earthquake cannot need turnAtLeast below 0")
			.withMessageContaining("Event Earthquake cannot need populationAtLeast below 0")
			.withMessageContaining("Event Earthquake cannot need approachingUnitsAtLeast below 0")
			.withMessageContaining("Event Earthquake cannot damage Defense by less than 0")
			.withMessageContaining("Event Dust storm cannot need unitsAtLeast below 0");
	}

}
