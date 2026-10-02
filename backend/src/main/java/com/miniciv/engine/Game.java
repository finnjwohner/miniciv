package com.miniciv.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * One match of miniciv. The engine knows only Nations and Turns: no time,
 * sockets or Players. Everything random draws from the seeded random source,
 * so a Game replays identically from the same seed.
 */
public class Game {

	private static final int BASE_GOLD = 50;

	private static final int BASE_POPULATION = 10;

	private static final int BASE_RESEARCH = 0;

	private static final int GOLD_PER_TURN = 10;

	private static final int POPULATION_PER_TURN = 2;

	private static final int RESEARCH_PER_TURN = 3;

	/**
	 * How many Turns a Warparty takes to reach another Nation, and to come
	 * back.
	 */
	private static final int TRAVEL_TURNS = 3;

	/**
	 * How much Gold a Warparty's surviving Strength lets it plunder.
	 */
	private static final int PLUNDER_GOLD_PER_STRENGTH = 3;

	/**
	 * How much Research a Warparty's surviving Strength lets it plunder.
	 */
	private static final int PLUNDER_RESEARCH_PER_STRENGTH = 1;

	/**
	 * How much Population a Warparty's Strength left once Defense is down
	 * reduces its target's by.
	 */
	private static final int POPULATION_PER_STRENGTH = 1;

	/**
	 * How much more Population a Warparty reduces its target's by when the
	 * target's Army was wiped out, or it had neither Army nor Defense.
	 */
	private static final int EXPOSED_POPULATION_MULTIPLIER = 2;

	/**
	 * The share of a conquered Nation's Gold and Research its conqueror seizes.
	 */
	private static final double CONQUEST_SHARE = 0.75;

	private final Ruleset ruleset;

	private final Map<String, Nation> nations = new LinkedHashMap<>();

	private final RandomGenerator random;

	private int turn = 1;

	/**
	 * The Nations conquered so far, in the order they were conquered.
	 */
	private final List<String> conquered = new ArrayList<>();

	/**
	 * The last Nation standing, or null while the Game goes on.
	 */
	private String winner;

	public Game(Ruleset ruleset, List<String> nations, RandomGenerator random) {
		this.ruleset = ruleset;
		for (String name : nations) {
			this.nations.put(name, new Nation(name, BASE_GOLD, BASE_POPULATION, BASE_RESEARCH));
		}
		this.random = random;
	}

	public int turn() {
		return turn;
	}

	/**
	 * Every Nation that took part in the Game, conquered or not.
	 */
	public List<String> nations() {
		return List.copyOf(nations.keySet());
	}

	/**
	 * The Nations conquered so far, in the order they were conquered.
	 */
	public List<String> conquered() {
		return List.copyOf(conquered);
	}

	/**
	 * The Nation that won the Game, once it is the only one left.
	 */
	public Optional<String> winner() {
		return Optional.ofNullable(winner);
	}

	/**
	 * The Nations still in the Game.
	 */
	private List<Nation> remaining() {
		return nations.values().stream().filter(nation -> !nation.conquered()).toList();
	}

	/**
	 * Resolves the Turn for every Nation still in the Game at once, then starts
	 * the next Turn. Once the Game is won, nothing happens any more.
	 */
	public void endTurn() {
		if (winner != null) {
			return;
		}
		List<Nation> inPlay = remaining();
		Map<String, List<String>> reports = new LinkedHashMap<>();
		inPlay.forEach(nation -> reports.put(nation.name(), new ArrayList<>()));
		for (Nation nation : inPlay) {
			List<String> report = reports.get(nation.name());
			for (String completed : nation.progressConstruction()) {
				report.add("Your " + completed + " has been built.");
			}
			for (Repair finished : nation.progressRepairs()) {
				report.add("Your " + finished.building() + " has been " + (finished.rebuild() ? "rebuilt." : "repaired."));
			}
			Map<String, Integer> trained = nation.progressTraining(turn + 1);
			if (!trained.isEmpty()) {
				report.add(listed(trained) + " finished training and joined your Army.");
			}
		}
		for (Nation nation : inPlay) {
			for (Warparty returned : nation.travelHome()) {
				String plunder = returned.plunder().isNone() ? "" : " with " + returned.plunder().described() + ",";
				reports.get(nation.name())
					.add("Your Warparty came home from " + returned.target() + plunder + " and "
							+ listed(returned.units()) + " rejoined your Army.");
			}
		}
		for (Nation attacker : inPlay) {
			// An attacker conquered earlier in this Turn no longer fights.
			if (attacker.conquered()) {
				continue;
			}
			for (Nation.BattleFought fought : attacker.fightBattles(warparty -> fight(attacker, warparty),
					TRAVEL_TURNS)) {
				reportBattle(attacker.name(), fought, reports);
			}
		}
		for (Nation nation : remaining()) {
			for (Warparty arrived : nation.travelOut()) {
				reports.get(nation.name())
					.add("Your Warparty of " + listed(arrived.units()) + " arrived at " + arrived.target()
							+ "; the Battle will be fought as this Turn ends.");
				reports.get(arrived.target())
					.add("A Warparty from " + nation.name()
							+ " arrived at your Nation; the Battle will be fought as this Turn ends.");
			}
		}
		for (Nation nation : remaining()) {
			List<String> report = reports.get(nation.name());
			int buildingPopulation = nation.populationFromBuildings(ruleset);
			int buildingGold = nation.goldFromBuildings(ruleset);
			nation.gain(GOLD_PER_TURN + buildingGold, POPULATION_PER_TURN + buildingPopulation, RESEARCH_PER_TURN);
			report.add("Your Nation gained " + GOLD_PER_TURN + " Gold, " + POPULATION_PER_TURN + " Population and "
					+ RESEARCH_PER_TURN + " Research.");
			buildingsAddedLine(buildingPopulation, buildingGold).ifPresent(report::add);
			for (Nation.Desertion desertion : nation.desertUntilUpkeepIsAffordable(ruleset)) {
				String from = desertion.warparty() == null ? "your Army"
						: "your Warparty sent to " + desertion.warparty().target();
				report.add("You could not pay your Units' upkeep, so " + listed(desertion.units()) + " deserted from "
						+ from + ".");
			}
			int upkeep = nation.payUpkeep(ruleset);
			if (upkeep > 0) {
				report.add("Your Units' upkeep cost " + upkeep + " Gold.");
			}
		}
		List<Nation> left = remaining();
		if (left.size() == 1) {
			winner = left.getFirst().name();
			nations.values().forEach(Nation::changed);
			reports.forEach((nation, report) -> report
				.add(nation.equals(winner) ? "You have won the Game." : winner + " has won the Game."));
		}
		inPlay.forEach(nation -> nation.turnReport(new TurnReport(turn, reports.get(nation.name()))));
		turn++;
	}

	/**
	 * Fights a Warparty's Battle against the Army of the Nation it arrived at.
	 * The Warparty's surviving Strength, rounded down, wears the Nation's
	 * Defense down, and what is left once Defense is 0 reduces its Population,
	 * more so if its Army was wiped out or it had neither Army nor Defense. A
	 * Nation left with neither Population nor Army is conquered, unless the
	 * Warparty was wiped out too; otherwise the survivors take plunder scaled
	 * by their remaining Strength, rounded down.
	 */
	private Nation.BattleFought fight(Nation attacker, Warparty warparty) {
		Nation defender = nation(warparty.target());
		Battle battle = Battle.fight(warparty.units(), defender.army(), ruleset, random);
		defender.lose(battle.armyLost());
		double strength = battle.warpartySurvivingStrength(ruleset);
		int wholeStrength = (int) Math.floor(strength);
		Nation.DefenseWorn defenseWorn = defender.wearDefense(wholeStrength, ruleset);
		int strengthLeft = wholeStrength - defenseWorn.worn();
		Exposure exposure = Exposure.of(battle, defenseWorn);
		int multiplier = exposure == Exposure.DEFENDED ? 1 : EXPOSED_POPULATION_MULTIPLIER;
		int populationLost = defender.losePopulation(strengthLeft * POPULATION_PER_STRENGTH * multiplier);
		if (!battle.warpartySurvivors().isEmpty() && defender.hasNeitherPopulationNorArmy()) {
			return new Nation.BattleFought(warparty, battle, Plunder.NONE, defenseWorn, populationLost, exposure,
					conquer(attacker, defender));
		}
		Plunder plunder = defender.plundered((int) Math.floor(strength * PLUNDER_GOLD_PER_STRENGTH),
				(int) Math.floor(strength * PLUNDER_RESEARCH_PER_STRENGTH));
		return new Nation.BattleFought(warparty, battle, plunder, defenseWorn, populationLost, exposure, null);
	}

	/**
	 * Takes a Nation out of the Game: its conqueror seizes a large share of its
	 * Gold and Research, and every other Warparty heading to it turns back
	 * home.
	 */
	private Nation.Conquest conquer(Nation conqueror, Nation conquered) {
		Plunder seized = conquered.conquer(conqueror.name(), CONQUEST_SHARE);
		conqueror.seize(seized);
		this.conquered.add(conquered.name());
		Map<String, List<Warparty>> turnedBack = new LinkedHashMap<>();
		for (Nation nation : remaining()) {
			List<Warparty> turned = nation.turnBackFrom(conquered.name(), TRAVEL_TURNS);
			if (!turned.isEmpty()) {
				turnedBack.put(nation.name(), turned);
			}
		}
		return new Nation.Conquest(seized, turnedBack);
	}

	private void reportBattle(String attacker, Nation.BattleFought fought, Map<String, List<String>> reports) {
		Battle battle = fought.battle();
		String defender = fought.warparty().target();
		List<String> attackerReport = reports.get(attacker);
		List<String> defenderReport = reports.get(defender);
		String warparty = listed(battle.warparty());
		if (battle.army().isEmpty()) {
			attackerReport.add("Your Warparty of " + warparty + " met no Army at " + defender + ".");
			defenderReport.add(attacker + "'s Warparty of " + warparty + " met no Army at your Nation.");
		}
		else {
			String army = listed(battle.army());
			attackerReport.add("Your Warparty of " + warparty + " fought " + defender + "'s Army of " + army
					+ ". You lost " + lost(battle.warpartyLost()) + " and " + defender + " lost "
					+ lost(battle.armyLost()) + ".");
			defenderReport.add("Your Army of " + army + " fought " + attacker + "'s Warparty of " + warparty
					+ ". You lost " + lost(battle.armyLost()) + " and " + attacker + " lost "
					+ lost(battle.warpartyLost()) + ".");
		}
		if (battle.warpartySurvivors().isEmpty()) {
			attackerReport.add("Your Warparty sent to " + defender + " was destroyed.");
			defenderReport.add(attacker + "'s Warparty was destroyed.");
			return;
		}
		Nation.DefenseWorn defenseWorn = fought.defenseWorn();
		if (defenseWorn.worn() > 0) {
			attackerReport.add("Your Warparty wore " + defender + "'s Defense down by " + defenseWorn.worn() + ", "
					+ buildingsHarmed(defenseWorn) + ".");
			defenderReport.add(attacker + "'s Warparty wore your Defense down from " + defenseWorn.before() + " to "
					+ defenseWorn.after() + ", " + buildingsHarmed(defenseWorn) + ".");
		}
		if (fought.populationLost() > 0) {
			String attackerReason = switch (fought.exposure()) {
				case DEFENDED -> "";
				case ARMY_WIPED_OUT -> ", doubled as " + defender + "'s Army was wiped out";
				case UNDEFENDED -> ", doubled as " + defender + " had no Army or Defense";
			};
			String defenderReason = switch (fought.exposure()) {
				case DEFENDED -> "";
				case ARMY_WIPED_OUT -> ", doubled as your Army was wiped out";
				case UNDEFENDED -> ", doubled as you had no Army or Defense";
			};
			attackerReport.add("Your Warparty reduced " + defender + "'s Population by " + fought.populationLost()
					+ attackerReason + ".");
			defenderReport.add(attacker + "'s Warparty reduced your Population by " + fought.populationLost()
					+ defenderReason + ".");
		}
		Nation.Conquest conquest = fought.conquest();
		if (conquest != null) {
			String seized = conquest.seized().described();
			attackerReport.add("Your Warparty conquered " + defender + ", seizing " + seized + ", and is heading home.");
			defenderReport.add(attacker + "'s Warparty conquered your Nation, seizing " + seized
					+ ". You are knocked out of the Game.");
			reports.forEach((nation, report) -> {
				if (!nation.equals(attacker) && !nation(nation).conquered()) {
					report.add(attacker + " conquered " + defender + ".");
				}
			});
			conquest.turnedBack()
				.forEach((nation, turned) -> turned.forEach(back -> reports.get(nation)
					.add("Your Warparty of " + listed(back.units()) + " heading to " + defender
							+ " turned back home, as " + defender + " was conquered.")));
			return;
		}
		Plunder plunder = fought.plunder();
		if (plunder.isNone()) {
			attackerReport.add("Your Warparty took nothing from " + defender + " and is heading home.");
			defenderReport.add(attacker + "'s Warparty took nothing from you and is heading home.");
		}
		else {
			attackerReport.add("Your Warparty took " + plunder.described() + " from " + defender + " and is heading home.");
			defenderReport.add(attacker + "'s Warparty took " + plunder.described() + " from you and is heading home.");
		}
	}

	/**
	 * Such as "destroying Walls ×1 and damaging Moat".
	 */
	private static String buildingsHarmed(Nation.DefenseWorn defenseWorn) {
		List<String> harmed = new ArrayList<>();
		if (!defenseWorn.destroyed().isEmpty()) {
			harmed.add("destroying " + listed(defenseWorn.destroyed()));
		}
		if (!defenseWorn.damaged().isEmpty()) {
			harmed.add("damaging " + joined(defenseWorn.damaged()));
		}
		return String.join(" and ", harmed);
	}

	private static String lost(Map<String, Integer> units) {
		return units.isEmpty() ? "no Units" : listed(units);
	}

	/**
	 * Applies a Nation's Action at once, or rejects it with a reason for its
	 * Player.
	 */
	public ActionResult act(String nation, Action action) {
		Nation actor = nation(nation);
		if (actor.conquered()) {
			return ActionResult.rejected("Your Nation has been conquered.");
		}
		if (winner != null) {
			return ActionResult.rejected("The Game is over.");
		}
		return switch (action) {
			case Action.StartBuilding start -> ruleset.building(start.building())
				.map(actor::startBuilding)
				.orElseGet(() -> noSuchBuilding(start.building()));
			case Action.RepairBuilding repair -> ruleset.building(repair.building())
				.map(actor::repair)
				.orElseGet(() -> noSuchBuilding(repair.building()));
			case Action.RebuildBuilding rebuild -> ruleset.building(rebuild.building())
				.map(actor::rebuild)
				.orElseGet(() -> noSuchBuilding(rebuild.building()));
			case Action.TrainUnits train -> {
				if (train.count() < 1) {
					yield ActionResult.rejected("Train at least one Unit.");
				}
				yield ruleset.unit(train.unit())
					.map(unit -> actor.trainUnits(unit, train.count(), turn))
					.orElseGet(() -> ActionResult
						.rejected("There is no Unit called " + train.unit() + " that you can train."));
			}
			case Action.SendWarparty send -> {
				if (send.target().equals(nation)) {
					yield ActionResult.rejected("A Warparty must be sent to another Nation.");
				}
				if (!nations.containsKey(send.target())) {
					yield ActionResult.rejected("There is no Nation called " + send.target() + ".");
				}
				if (nation(send.target()).conquered()) {
					yield ActionResult.rejected(send.target() + " has been conquered.");
				}
				if (send.units().isEmpty()
						|| send.units().values().stream().anyMatch(count -> count == null || count < 1)) {
					yield ActionResult.rejected("Choose at least one Unit to send.");
				}
				yield actor.sendWarparty(send.target(), send.units(), TRAVEL_TURNS);
			}
		};
	}

	private static ActionResult noSuchBuilding(String building) {
		return ActionResult.rejected("There is no Building called " + building + ".");
	}

	private static Optional<String> buildingsAddedLine(int population, int gold) {
		List<String> added = new ArrayList<>();
		if (population > 0) {
			added.add(population + " Population");
		}
		if (gold > 0) {
			added.add(gold + " Gold");
		}
		return added.isEmpty() ? Optional.empty()
				: Optional.of("Your Buildings added " + String.join(" and ", added) + ".");
	}

	/**
	 * Units counted by type, such as "Warrior ×2 and Archer ×1".
	 */
	private static String listed(Map<String, Integer> units) {
		return joined(units.entrySet().stream().map(e -> e.getKey() + " ×" + e.getValue()).toList());
	}

	/**
	 * Such as "Walls, Moat and Palisade".
	 */
	private static String joined(List<String> items) {
		if (items.size() == 1) {
			return items.getFirst();
		}
		return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.getLast();
	}

	public NationView viewOf(String nation) {
		Nation own = nation(nation);
		List<String> others = remaining().stream().map(Nation::name).filter(name -> !name.equals(nation)).toList();
		return own.view(turn, others, TRAVEL_TURNS, ruleset, winner);
	}

	private Nation nation(String name) {
		Nation nation = nations.get(name);
		if (nation == null) {
			throw new IllegalArgumentException("There is no Nation called " + name + " in this Game");
		}
		return nation;
	}

}
