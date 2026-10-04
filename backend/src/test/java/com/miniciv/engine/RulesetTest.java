package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.ScoutEventsTest.scoutsLost;
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
	void aMissionMustTakeATurnReportSomethingAndHaveAName() {
		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(),
					List.of(new Mission("Report Gold", 0, Mission.FactKind.GOLD), new Mission("Loiter", 2, null),
							new Mission(" ", 2, Mission.FactKind.ARMY))))
			.withMessageContaining("Mission Report Gold must take at least 1 Turn.")
			.withMessageContaining("Mission Loiter reports nothing.")
			.withMessageContaining("A Mission has no name.");
	}

	@Test
	void noTwoMissionsShareAName() {
		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(),
					List.of(new Mission("Report Gold", 2, Mission.FactKind.GOLD),
							new Mission("Report Gold", 3, Mission.FactKind.GOLD))))
			.withMessageContaining("There is more than one Mission called Report Gold.");
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
	void onlyOneTechnologyCanUnlockAUnitOrBuildingType() {
		Technology bronze = new Technology("Bronze Working", 10, List.of(), List.of("Warrior"));
		Technology iron = new Technology("Iron Working", 20, List.of(), List.of("Warrior"));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(unit("Warrior")), List.of(), List.of(bronze, iron)))
			.withMessageContaining("Warrior is unlocked by more than one Technology");
	}

	@Test
	void aTechnologyCannotRequireItselfEvenThroughOthers() {
		Technology writing = new Technology("Writing", 10, List.of("Philosophy"), List.of());
		Technology philosophy = new Technology("Philosophy", 20, List.of("Writing"), List.of());
		Technology mysticism = new Technology("Mysticism", 20, List.of("Mysticism"), List.of());

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(writing, philosophy, mysticism)))
			.withMessageContaining("Technology Writing can never be unlocked, as it requires itself")
			.withMessageContaining("Technology Philosophy can never be unlocked, as it requires itself")
			.withMessageContaining("Technology Mysticism can never be unlocked, as it requires itself");
	}

	@Test
	void twoUnitOrBuildingTypesCannotShareAName() {
		BuildingType farm = new BuildingType("Farm", 30, 2, 1, 0, 0, 0, 0);

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(unit("Farm")), List.of(farm), List.of()))
			.withMessageContaining("There is more than one Unit or Building type called Farm");
	}

	@Test
	void everyUnitBuildingAndTechnologyHasAName() {
		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(unit(" ")), List.of(new BuildingType(null, 30, 2, 1, 0, 0, 0, 0)),
					List.of(new Technology("", 10, List.of(), List.of()))))
			.withMessageContaining("A Unit type has no name")
			.withMessageContaining("A Building type has no name")
			.withMessageContaining("A Technology has no name");
	}

	@Test
	void buildingsAndUnitsTakeAtLeastOneTurnAndCostOrAddNothingNegative() {
		BuildingType farm = new BuildingType("Farm", -5, 0, 1, -3, 0, -2, -1);
		UnitType warrior = new UnitType("Warrior", 10, -1, 0, 1, 2, List.of());

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(warrior), List.of(farm), List.of()))
			.withMessageContaining("Building type Farm must take at least 1 Turn to construct")
			.withMessageContaining("Building type Farm cannot cost less than 0 Gold")
			.withMessageContaining("Building type Farm cannot add less than 0 Gold per Turn")
			.withMessageContaining("Building type Farm cannot add less than 0 Research per Turn")
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
			.withMessageContaining("Event Plague must happen to a Nation, a Warparty or Scouts")
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
		Event storm = event("Dust storm", Event.Subject.WARPARTY, 0.1, new Event.Conditions(null, 10, 8, null, 30, 5, 2),
				new Event.Effects(5, -1, 2, 3, 1));
		Event harvest = event("Good harvest", Event.Subject.NATION, 0.1, new Event.Conditions(null, null, null, 4, null, null, null),
				new Event.Effects(0, 4, 0, 0, 2));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(storm, harvest)))
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot need populationAtLeast")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot need approachingUnitsAtLeast")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot need goldAtLeast")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot need researchAtLeast")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot need defenseAtLeast")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot change gold")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot change population")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot change research")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot change damageDefense")
			.withMessageContaining("Event Good harvest happens to a Nation, so it cannot need unitsAtLeast")
			.withMessageContaining("Event Good harvest happens to a Nation, so it cannot change travelTurns");
	}

	@Test
	void anEventCannotNeedLessThanZeroOrDamageDefenseByLessThanZero() {
		Event quake = event("Earthquake", Event.Subject.NATION, 0.1, new Event.Conditions(-1, -2, -3, null, -4, -5, -6),
				new Event.Effects(0, 0, 0, -4, 0));
		Event storm = event("Dust storm", Event.Subject.WARPARTY, 0.1, new Event.Conditions(null, null, null, -1, null, null, null),
				new Event.Effects(0, 0, 0, 0, 1));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(quake, storm)))
			.withMessageContaining("Event Earthquake cannot need turnAtLeast below 0")
			.withMessageContaining("Event Earthquake cannot need populationAtLeast below 0")
			.withMessageContaining("Event Earthquake cannot need approachingUnitsAtLeast below 0")
			.withMessageContaining("Event Earthquake cannot need goldAtLeast below 0")
			.withMessageContaining("Event Earthquake cannot need researchAtLeast below 0")
			.withMessageContaining("Event Earthquake cannot need defenseAtLeast below 0")
			.withMessageContaining("Event Earthquake cannot damage Defense by less than 0")
			.withMessageContaining("Event Dust storm cannot need unitsAtLeast below 0");
	}

	static Event dilemma(String name, Event.Subject subject, Event.Effects effects, Event.Choice... choices) {
		return new Event(name, subject, 0.1, null, effects, "Something happened", List.of(choices));
	}

	static Event.Choice choice(String name, boolean worst) {
		return new Event.Choice(name, null, worst, "You chose");
	}

	@Test
	void aDilemmaOffersTwoOrThreeChoicesOneOfThemTheWorst() {
		Event single = dilemma("Flood", Event.Subject.NATION, null, choice("Flee", true));
		Event crowded = dilemma("Fire", Event.Subject.NATION, null, choice("Flee", true), choice("Fight", false),
				choice("Pray", false), choice("Wait", false));
		Event noWorst = dilemma("Famine", Event.Subject.NATION, null, choice("Ration", false), choice("Import", false));
		Event twoWorst = dilemma("Drought", Event.Subject.NATION, null, choice("Dig", true), choice("Pray", true));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(single, crowded, noWorst, twoWorst)))
			.withMessageContaining("Event Flood must offer two or three choices")
			.withMessageContaining("Event Fire must offer two or three choices")
			.withMessageContaining("Event Famine must mark exactly one of its choices as the worst")
			.withMessageContaining("Event Drought must mark exactly one of its choices as the worst");
	}

	@Test
	void aDilemmasChoicesHaveUniqueNamesAndTextAndSuitANation() {
		Event.Choice blank = new Event.Choice(" ", null, false, "You chose");
		Event.Choice untold = new Event.Choice("Fight", new Event.Effects(0, 0, 0, -1, 2), false, "");
		Event fire = dilemma("Fire", Event.Subject.NATION, null, choice("Flee", true), blank, untold);
		Event twice = dilemma("Flood", Event.Subject.NATION, null, choice("Flee", true), choice("Flee", false));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(fire, twice)))
			.withMessageContaining("Event Fire has a choice with no name")
			.withMessageContaining("Event Fire's choice Fight has no text to describe it")
			.withMessageContaining("Event Fire's choice Fight cannot change travelTurns")
			.withMessageContaining("Event Fire's choice Fight cannot damage Defense by less than 0")
			.withMessageContaining("Event Flood offers more than one choice called Flee");
	}

	@Test
	void aDilemmaHappensToANationAndHasNoEffectsOfItsOwn() {
		Event ambush = dilemma("Ambush", Event.Subject.WARPARTY, null, choice("Fight", true), choice("Flee", false));
		Event flood = dilemma("Flood", Event.Subject.NATION, new Event.Effects(0, -2, 0, 0, 0), choice("Flee", true),
				choice("Dig", false));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(ambush, flood)))
			.withMessageContaining("Event Ambush offers choices, so it must happen to a Nation")
			.withMessageContaining("Event Flood offers choices, so its effects belong to its choices");
	}

	@Test
	void aScoutEventHasTheConditionsAndEffectsOfANation() {
		Event blizzard = event("Blizzard", Event.Subject.SCOUTS, 0.1,
				new Event.Conditions(2, 10, 1, 4, 30, 5, 2), new Event.Effects(5, -1, 2, 3, 1, null, 1, null, 1));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(blizzard)))
			.withMessageContaining("Event Blizzard happens to Scouts, so it cannot need unitsAtLeast")
			.withMessageContaining("Event Blizzard happens to Scouts, so it cannot change travelTurns")
			.withMessageNotContaining("populationAtLeast")
			.withMessageNotContaining("gold")
			.withMessageNotContaining("workTurns")
			.withMessageNotContaining("scoutsLost");
	}

	@Test
	void onlyAScoutEventCanLoseScoutsAndNeverLessThanZero() {
		Event famine = event("Famine", Event.Subject.NATION, 0.1, null, scoutsLost(1));
		Event storm = event("Dust storm", Event.Subject.WARPARTY, 0.1, null, scoutsLost(1));
		Event thaw = event("Thaw", Event.Subject.SCOUTS, 0.1, null, scoutsLost(-1));
		Event bandits = dilemma("Bandits", Event.Subject.NATION, null, choice("Flee", true),
				new Event.Choice("Hunt", scoutsLost(1), false, "You hunted"),
				new Event.Choice("Track", false,
						List.of(new Event.Outcome(1, scoutsLost(1), "Your trackers vanished"))));
		Event avalanche = dilemma("Avalanche", Event.Subject.SCOUTS, null, choice("Wait", true),
				new Event.Choice("Climb", scoutsLost(-2), false, "You climbed"),
				new Event.Choice("Dig", false, List.of(new Event.Outcome(1, scoutsLost(-1), "You dug"))));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(),
					List.of(famine, storm, thaw, bandits, avalanche)))
			.withMessageContaining("Event Famine happens to a Nation, so it cannot change scoutsLost")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot change scoutsLost")
			.withMessageContaining("Event Thaw cannot lose less than 0 Scouts")
			.withMessageContaining("Event Bandits's choice Hunt cannot lose Scouts, as Event Bandits happens to a Nation")
			.withMessageContaining(
					"Event Bandits's choice Track has an outcome that cannot lose Scouts, as Event Bandits happens to a Nation")
			.withMessageContaining("Event Avalanche's choice Climb cannot lose less than 0 Scouts")
			.withMessageContaining("Event Avalanche's choice Dig has an outcome that cannot lose less than 0 Scouts");
	}

	@Test
	void aDilemmaCanHappenToScouts() {
		Event lostTrail = dilemma("Lost trail", Event.Subject.SCOUTS, null, choice("Wait", true),
				new Event.Choice("Press on", scoutsLost(1), false, "You pressed on"));

		new Ruleset(List.of(), List.of(), List.of(), List.of(lostTrail));
	}

	@Test
	void aLastingEventLastsAtLeastOneTurnChangesSomethingAndNeverHappensToAWarparty() {
		Event drought = event("Drought", Event.Subject.NATION, 0.1, null,
				LastingEventsTest.lasting(new Event.Lasting(0, 0, 0, 0, true)));
		Event lull = event("Lull", Event.Subject.SCOUTS, 0.1, null,
				LastingEventsTest.lasting(new Event.Lasting(2, 0, 0, 0, false)));
		Event fog = event("Fog", Event.Subject.WARPARTY, 0.1, null,
				LastingEventsTest.lasting(new Event.Lasting(2, -1, 0, 0, false)));
		Event locusts = dilemma("Locusts", Event.Subject.NATION, null, choice("Pray", true),
				new Event.Choice("Burn", LastingEventsTest.lasting(new Event.Lasting(-1, 0, 0, 0, true)), false, "You burned"),
				new Event.Choice("Flee", false, List.of(new Event.Outcome(1,
						LastingEventsTest.lasting(new Event.Lasting(2, 0, 0, 0, false)), "You fled"))));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(drought, lull, fog, locusts)))
			.withMessageContaining("Event Drought must begin a Lasting Event of at least 1 Turn")
			.withMessageContaining("Event Lull begins a Lasting Event that changes nothing")
			.withMessageContaining("Event Fog happens to a Warparty, so it cannot begin a Lasting Event")
			.withMessageContaining("Event Locusts's choice Burn must begin a Lasting Event of at least 1 Turn")
			.withMessageContaining(
					"Event Locusts's choice Flee has an outcome that begins a Lasting Event that changes nothing");
	}

	static Event.Outcome outcome(double chance) {
		return new Event.Outcome(chance, null, "It turned out so");
	}

	@Test
	void aChoicesOutcomeChancesMustTotalOne() {
		Event flood = dilemma("Flood", Event.Subject.NATION, null, choice("Flee", true),
				new Event.Choice("Dig", false, List.of(outcome(0.5), outcome(0.4))),
				new Event.Choice("Pray", false, List.of(outcome(0.7), outcome(0.4))));
		Event fire = dilemma("Fire", Event.Subject.NATION, null, choice("Flee", true),
				new Event.Choice("Fight", false, List.of(outcome(0.1), outcome(0.2), outcome(0.7))));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(flood, fire)))
			.withMessageContaining("Event Flood's choice Dig has outcome chances totalling 0.9, not 1")
			.withMessageContaining("Event Flood's choice Pray has outcome chances totalling 1.1, not 1")
			.withMessageNotContaining("Fire");
	}

	@Test
	void aChoicesOutcomesHaveAChanceAndTextAndSuitANation() {
		Event.Choice dig = new Event.Choice("Dig", false,
				List.of(new Event.Outcome(0, null, "It turned out so"), new Event.Outcome(1.5, null, " "),
						new Event.Outcome(-0.5, new Event.Effects(0, 0, 0, -1, 2), "It went wrong")));
		Event flood = dilemma("Flood", Event.Subject.NATION, null, choice("Flee", true), dig);

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(flood)))
			.withMessageContaining("Event Flood's choice Dig has an outcome without a chance above 0 and at most 1")
			.withMessageContaining("Event Flood's choice Dig has an outcome with no text to describe it")
			.withMessageContaining("Event Flood's choice Dig has an outcome that changes travelTurns")
			.withMessageContaining("Event Flood's choice Dig has an outcome that damages Defense by less than 0");
	}

	@Test
	void aChoiceWithOutcomesHasNoEffectsOrTextOfItsOwn() {
		Event.Choice dig = new Event.Choice("Dig", new Event.Effects(0, -1, 0, 0, 0), false, "You dug",
				List.of(outcome(1)));
		Event flood = dilemma("Flood", Event.Subject.NATION, null, choice("Flee", true), dig);

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(flood)))
			.withMessageContaining("Event Flood's choice Dig has outcomes, so its effects belong to its outcomes")
			.withMessageContaining("Event Flood's choice Dig has outcomes, so its text belongs to its outcomes");
	}

	static final UnitType SCOUT = new UnitType("Scout", 5, 1, 1, 1, 0, List.of(), true);

	static final UnitType SPY = new UnitType("Spy", 10, 1, 1, 1, 0.1, List.of(), false, true);

	static Event.Effects units(Map<String, Integer> units) {
		return new Event.Effects(0, 0, 0, 0, 0, units, 0, null);
	}

	@Test
	void anEventCannotGainOrLoseAnUnknownUnitTypeOrNoneOfOne() {
		Event volunteers = event("Volunteers", Event.Subject.NATION, 0.1, null,
				units(Map.of("Lancer", 2, "Warrior", 0)));
		Event.Choice hire = new Event.Choice("Hire", units(Map.of("Pikeman", 1)), false, "You hired them");
		Event.Choice train = new Event.Choice("Train", false,
				List.of(new Event.Outcome(1, units(Map.of("Slinger", -1)), "It turned out so")));
		Event mercenaries = dilemma("Mercenaries", Event.Subject.NATION, null, choice("Refuse", true), hire, train);

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(unit("Warrior")), List.of(), List.of(), List.of(volunteers, mercenaries)))
			.withMessageContaining("Event Volunteers gains or loses Lancer, which is not a Unit type")
			.withMessageContaining("Event Volunteers cannot gain or lose 0 Warrior")
			.withMessageContaining("Event Mercenaries's choice Hire gains or loses Pikeman, which is not a Unit type")
			.withMessageContaining("Event Mercenaries's choice Train has an outcome that gains or loses Slinger, "
					+ "which is not a Unit type");
	}

	@Test
	void anEventsNewEffectsMustSuitWhatItHappensTo() {
		Event scouting = event("Scouting", Event.Subject.NATION, 0.1, null, units(Map.of("Scout", 1)));
		Event storm = event("Storm", Event.Subject.WARPARTY, 0.1, null,
				new Event.Effects(0, 0, 0, 0, 0, Map.of("Spy", 1), 2, Event.SpyCaught.OWN));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(SCOUT, SPY), List.of(), List.of(), List.of(scouting, storm)))
			.withMessageContaining("Event Scouting cannot gain or lose Scout, as Scouts are never in the Army")
			.withMessageContaining("Event Storm happens to a Warparty, so it cannot gain or lose Spy")
			.withMessageContaining("Event Storm happens to a Warparty, so it cannot change workTurns")
			.withMessageContaining("Event Storm happens to a Warparty, so it cannot change spyCaught");
	}

	@Test
	void anEventDamagesNoLessThanZeroBuildingsAndNeverAWarpartys() {
		Event fire = event("Fire", Event.Subject.NATION, 0.1, null, DamagedBuildingsTest.damageBuildings(-1));
		Event storm = event("Dust storm", Event.Subject.WARPARTY, 0.1, null, DamagedBuildingsTest.damageBuildings(1));
		Event riot = dilemma("Riot", Event.Subject.NATION, null, choice("Wait", true),
				new Event.Choice("Disperse", DamagedBuildingsTest.damageBuildings(-2), false, "You dispersed them"),
				new Event.Choice("Parley", false,
						List.of(new Event.Outcome(1, DamagedBuildingsTest.damageBuildings(-1), "You parleyed"))));
		Event flood = dilemma("Flood", Event.Subject.NATION, null, choice("Flee", true),
				new Event.Choice("Dig", DamagedBuildingsTest.damageBuildings(2), false, "You dug"));

		assertThatExceptionOfType(InvalidRuleset.class)
			.isThrownBy(() -> new Ruleset(List.of(), List.of(), List.of(), List.of(fire, storm, riot, flood)))
			.withMessageContaining("Event Fire cannot damage less than 0 Buildings")
			.withMessageContaining("Event Dust storm happens to a Warparty, so it cannot change damageBuildings")
			.withMessageContaining("Event Riot's choice Disperse cannot damage less than 0 Buildings")
			.withMessageContaining("Event Riot's choice Parley has an outcome that damages less than 0 Buildings")
			.withMessageNotContaining("Flood");
	}

}
