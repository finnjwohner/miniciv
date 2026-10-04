package com.miniciv.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static com.miniciv.engine.GameTest.WALLS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Damaged non-defensive Buildings: the Event effect that Damages them, what
 * they stop adding, and Repairing and Selling them.
 */
class DamagedBuildingsTest {

	static final BuildingType MARKET = new BuildingType("Market", 20, 1, 0, 3, 0, 0, 0);

	static final BuildingType LIBRARY = new BuildingType("Library", 20, 3, 0, 0, 0, 2, 0);

	static Event.Effects damageBuildings(int count) {
		return new Event.Effects(0, 0, 0, 0, 0, null, 0, null, 0, null, count);
	}

	/**
	 * Happens to each Nation it can befall when luck below a half is drawn for
	 * it.
	 */
	static Event fire(int count) {
		return new Event("Fire", Event.Subject.NATION, 0.5, null, damageBuildings(count), "A fire swept the city");
	}

	final GameTest.Rigged luck = new GameTest.Rigged();

	Game game;

	void play(Event... events) {
		game = new Game(new Ruleset(SpiesTest.RULESET.units(), List.of(MARKET, LIBRARY, WALLS), List.of(), List.of(events)),
				Identities.of("Rome", "Carthage"), luck);
	}

	/**
	 * {@link #play}, once every Nation has Located every other; see
	 * {@link Locating}.
	 */
	void playLocated(Event... events) {
		game = Locating.everyone(new Ruleset(SpiesTest.RULESET.units(), List.of(MARKET, LIBRARY, WALLS), List.of(), List.of(events),
				SpyMissionsTest.MISSIONS),
				Identities.of("Rome", "Carthage"), luck);
	}

	void act(String nation, Action action) {
		assertThat(game.act(nation, action)).isEqualTo(ActionResult.ACCEPTED);
	}

	List<String> report(String nation) {
		return game.viewOf(nation).turnReport().lines();
	}

	/**
	 * Has Rome build the given Buildings, each taking a Turn, and ends the
	 * Turn they are built on with no Event happening.
	 */
	void romeBuilt(String... buildings) {
		for (String building : buildings) {
			act("Rome", new Action.StartBuilding(building));
		}
		game.endTurn();
	}

	@Test
	void anEventDamagesThatManyUndamagedNonDefensiveBuildingsPickedByLuckAndNamesThem() {
		play(fire(2));
		act("Rome", new Action.StartBuilding("Market"));
		act("Rome", new Action.StartBuilding("Market"));
		game.endTurn();
		luck.then(0, 0.99, 0);

		game.endTurn();

		assertThat(report("Rome")).contains("A fire swept the city: your Market ×2 were damaged.");
		assertThat(game.viewOf("Rome").damage()).containsExactly(BuildingDamage.nonDefensive("Market", 2, 10, 1));
	}

	@Test
	void luckNeverVariesHowManyBuildingsAnEventDamages() {
		play(fire(1));
		romeBuilt("Market", "Market");
		luck.then(0, 0.99);

		game.endTurn();

		assertThat(report("Rome")).contains("A fire swept the city: your Market ×1 was damaged.");
		assertThat(game.viewOf("Rome").damage()).containsExactly(BuildingDamage.nonDefensive("Market", 1, 10, 1));
	}

	@Test
	void anEventDamagesNoMoreBuildingsThanAreUndamagedAndNeverDefensiveOnes() {
		play(fire(5));
		act("Rome", new Action.StartBuilding("Walls"));
		game.endTurn();
		romeBuilt("Market");
		luck.then(0, 0);

		game.endTurn();

		assertThat(report("Rome")).contains("A fire swept the city: your Market ×1 was damaged.");
		assertThat(game.viewOf("Rome").damage()).containsExactly(BuildingDamage.nonDefensive("Market", 1, 10, 1));
		assertThat(game.viewOf("Rome").defense()).isEqualTo(10);
	}

	@Test
	void anEventDamagingBuildingsIsNotRolledForANationWithNoUndamagedNonDefensiveBuildings() {
		play(fire(1));
		romeBuilt("Market");
		luck.then(0, 0);
		game.endTurn();
		act("Carthage", new Action.StartBuilding("Market"));
		// Were it rolled for Rome, whose only Market is Damaged, it would take
		// Rome's draw and Carthage's would miss.
		luck.then(0, 0);

		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.startsWith("A fire"));
		assertThat(report("Carthage")).contains("A fire swept the city: your Market ×1 was damaged.");
		assertThat(game.viewOf("Rome").damage()).containsExactly(BuildingDamage.nonDefensive("Market", 1, 10, 1));
	}

	@Test
	void aDamagedBuildingAddsNothingAndTheGoldForecastAgrees() {
		play(fire(1));
		romeBuilt("Market");
		int population = game.viewOf("Rome").population();
		assertThat(game.viewOf("Rome").goldForecast()).isEqualTo(new GoldForecast(population + 3, 0));
		luck.then(0, 0);
		game.endTurn();

		population = game.viewOf("Rome").population();
		assertThat(game.viewOf("Rome").goldForecast()).isEqualTo(new GoldForecast(population, 0));
		int gold = game.viewOf("Rome").gold();
		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.startsWith("Your Buildings added"));
		assertThat(game.viewOf("Rome").gold()).isEqualTo(gold + population);
	}

	@Test
	void aDamagedBuildingAddsNoResearchOrPopulation() {
		BuildingType farm = new BuildingType("Farm", 10, 1, 2, 0, 0, 0, 0);
		BuildingType library = new BuildingType("Library", 10, 1, 0, 0, 0, 2, 0);
		game = new Game(new Ruleset(SpiesTest.RULESET.units(), List.of(farm, library), List.of(), List.of(fire(2))),
				Identities.of("Rome", "Carthage"), luck);
		romeBuilt("Farm", "Library");
		luck.then(0, 0, 0);
		game.endTurn();
		int population = game.viewOf("Rome").population();
		int research = game.viewOf("Rome").research();

		game.endTurn();

		assertThat(report("Rome")).noneMatch(line -> line.startsWith("Your Buildings added"));
		assertThat(game.viewOf("Rome").population()).isEqualTo(population + 2);
		assertThat(game.viewOf("Rome").research()).isEqualTo(research + 3);
	}

	@Test
	void aDamagedBuildingIsRepairedForHalfItsGoldAndTurnsRoundedUpAndThenAddsItsEffectsAgain() {
		play(fire(1));
		act("Rome", new Action.StartBuilding("Library"));
		for (int turn = 1; turn <= 3; turn++) {
			game.endTurn();
		}
		luck.then(0, 0);
		game.endTurn();
		assertThat(game.viewOf("Rome").damage()).containsExactly(BuildingDamage.nonDefensive("Library", 1, 10, 2));
		int gold = game.viewOf("Rome").gold();

		act("Rome", new Action.RepairBuilding("Library"));

		NationView rome = game.viewOf("Rome");
		assertThat(rome.gold()).isEqualTo(gold - 10);
		assertThat(rome.repairs()).containsExactly(new Repair("Library", false, 0, 2));
		assertThat(rome.damage()).isEmpty();
		int research = rome.research();
		game.endTurn();
		assertThat(game.viewOf("Rome").research()).isEqualTo(research + 3);
		game.endTurn();
		assertThat(report("Rome")).contains("Your Library has been repaired.", "Your Buildings added 2 Research.");
		assertThat(game.viewOf("Rome").research()).isEqualTo(research + 3 + 3 + 2);
		assertThat(game.viewOf("Rome").damage()).isEmpty();
	}

	@Test
	void eachDamagedBuildingIsRepairedOnItsOwn() {
		play(fire(2));
		romeBuilt("Market", "Market");
		luck.then(0, 0, 0);
		game.endTurn();

		act("Rome", new Action.RepairBuilding("Market"));
		assertThat(game.viewOf("Rome").damage()).containsExactly(BuildingDamage.nonDefensive("Market", 1, 10, 1));
		act("Rome", new Action.RepairBuilding("Market"));

		assertThat(game.act("Rome", new Action.RepairBuilding("Market")))
			.isEqualTo(ActionResult.rejected("You have no damaged Market to repair."));
		game.endTurn();
		assertThat(report("Rome")).contains("Your 2 Markets have been repaired.");
		assertThat(game.viewOf("Rome").goldForecast().income()).isEqualTo(game.viewOf("Rome").population() + 6);
	}

	@Test
	void aDamagedBuildingCannotBeRepairedWithoutTheGoldForIt() {
		play(fire(1));
		romeBuilt("Market", "Market");
		luck.then(0, 0);
		game.endTurn();
		while (game.viewOf("Rome").gold() >= 10) {
			act("Rome", new Action.TrainUnits("Warrior", 1));
		}
		int gold = game.viewOf("Rome").gold();

		assertThat(game.act("Rome", new Action.RepairBuilding("Market")))
			.isEqualTo(ActionResult.rejected("Repairing Market costs 10 Gold, but you have only " + gold + "."));
		assertThat(game.act("Rome", new Action.RebuildBuilding("Market")))
			.isEqualTo(ActionResult.rejected("You have no destroyed Market to rebuild."));
	}

	@Test
	void anUndamagedBuildingSellsForHalfItsGoldFirstAndADamagedOneForAQuarter() {
		play(fire(1));
		romeBuilt("Market", "Market");
		luck.then(0, 0);
		game.endTurn();
		assertThat(game.viewOf("Rome").sellValues()).containsExactly(entry("Market", 10));
		int gold = game.viewOf("Rome").gold();

		act("Rome", new Action.SellBuilding("Market"));

		NationView rome = game.viewOf("Rome");
		assertThat(rome.gold()).isEqualTo(gold + 10);
		assertThat(rome.buildings()).containsExactly(entry("Market", 1));
		assertThat(rome.damage()).containsExactly(BuildingDamage.nonDefensive("Market", 1, 10, 1));
		assertThat(rome.sellValues()).containsExactly(entry("Market", 5));

		act("Rome", new Action.SellBuilding("Market"));

		rome = game.viewOf("Rome");
		assertThat(rome.gold()).isEqualTo(gold + 10 + 5);
		assertThat(rome.buildings()).isEmpty();
		assertThat(rome.damage()).isEmpty();
	}

	@Test
	void aDilemmaOutcomeCanDamageBuildings() {
		play(new Event("Riot", Event.Subject.NATION, 0.5, null, null, "A mob gathered in the square",
				List.of(new Event.Choice("Wait", damageBuildings(1), true, "The mob ran riot"),
						new Event.Choice("Pay", new Event.Effects(-5, 0, 0, 0, 0), false, "You paid the mob off"))));
		romeBuilt("Market");
		luck.then(0);
		game.endTurn();
		luck.then(0);

		game.endTurn();

		assertThat(report("Rome")).contains("You did not choose in time what to do about Riot. "
				+ "The mob ran riot: your Market ×1 was damaged.");
		assertThat(game.viewOf("Rome").damage()).containsExactly(BuildingDamage.nonDefensive("Market", 1, 10, 1));
	}

	@Test
	void aSpyReportsWhichOfItsTargetsBuildingsAreDamaged() {
		playLocated(fire(1));
		act("Carthage", new Action.StartBuilding("Market"));
		act("Carthage", new Action.StartBuilding("Market"));
		act("Rome", new Action.TrainUnits("Spy", 1));
		luck.then(0, 0);
		game.endTurn();
		act("Rome", new Action.SendSpy("Carthage"));
		game.endTurn();
		game.endTurn();
		// The Fire misses Carthage's undamaged Market from now on.
		game.endTurn();
		act("Rome", new Action.SetMission(1, "Report Buildings"));
		game.endTurn();
		game.endTurn();

		game.endTurn();

		assertThat(report("Rome")).contains("Your Spy in Carthage reported that Carthage has built Market ×2, "
				+ "with Market ×1 damaged, and its Defense is 0.");
		Facts facts = game.viewOf("Rome")
			.map()
			.stream()
			.filter(nation -> nation.name().equals("Carthage"))
			.findFirst()
			.orElseThrow()
			.facts();
		assertThat(facts.buildings()).isEqualTo(new Fact<>(Map.of("Market", 2), 9));
		assertThat(facts.damaged()).isEqualTo(new Fact<>(Map.of("Market", 1), 9));
	}

	@Test
	void aBattleNeverDamagesNorDestroysANonDefensiveBuilding() {
		playLocated();
		romeBuilt("Market");
		act("Carthage", new Action.TrainUnits("Warrior", 3));
		game.endTurn();
		game.endTurn();
		game.endTurn();
		act("Carthage", new Action.SendWarparty("Rome", Map.of("Warrior", 3)));
		while (game.viewOf("Carthage").warparties().stream().noneMatch(Warparty::awaitingBattle)) {
			game.endTurn();
		}

		game.endTurn();

		assertThat(report("Rome")).anyMatch(line -> line.endsWith("met no Army at your Nation."));
		assertThat(game.viewOf("Rome").damage()).isEmpty();
		assertThat(game.viewOf("Rome").buildings()).containsExactly(entry("Market", 1));
	}

}
