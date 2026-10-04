package com.miniciv.engine;

import java.util.List;
import java.util.Map;

/**
 * What one Nation's Player may see on a Turn: their own state, the Map of
 * every Nation that took part, by name and whether it is still in the Game,
 * and of the others nothing more than their Spies reported, and the Turn
 * Report of the Turn before, which is null on the first Turn. A conquered
 * Nation's Player keeps the Turn Report of the Turn it was conquered. The
 * version rises with every change to the Nation, so a browser can ignore a view
 * older than one it already has.
 *
 * @param map every Nation that took part in the Game, in the order they
 *        joined, by name and whether it is still in the Game, was conquered
 *        or was removed, with the facts this Nation's Spies reported of it
 * @param availableBuildings the Buildings the Nation may start
 * @param buildings how many of each Building the Nation has completed
 * @param construction the Buildings under construction, in the order started
 * @param repairs the Buildings being repaired or rebuilt, in the order
 *        started
 * @param defense the Nation's Defense, raised by its defensive Buildings
 * @param damage the Buildings Damaged or destroyed, by type in the order
 *        first completed, not counting those being repaired or rebuilt
 * @param sellValues the Gold Selling one Building of each type would return
 *        now, for the types the Nation may Sell one of, in the order first
 *        completed
 * @param availableUnits the Unit types the Nation may train
 * @param army how many of each Unit type the Nation has at home, never
 *        counting its Scouts
 * @param scouts how many Scouts the Nation has roaming the wilds, Idle or
 *        on Tasks
 * @param idleScouts how many of its Scouts are on no Task
 * @param tasks the Tasks its Scouts are set to, in the order set
 * @param located the Nations it has Located, and so may send Spies and
 *        Warparties to, in the order Located
 * @param training the Units in training, in the order started
 * @param warparties the Nation's Warparties abroad, in the order sent
 * @param spies the Nation's Spies abroad, in the order sent, each with the
 *        Mission it is on and the Turns left on it
 * @param missions the Missions its Spies in place may be set to, in the
 *        ruleset's order
 * @param travelBrackets how many Turns a Warparty takes to reach another
 *        Nation, by its Strength, from the weakest Warparties up
 * @param spyTravelTurns how many Turns a Spy takes to reach another Nation
 * @param upkeep the Gold the Nation's Units cost each Turn, at home and abroad
 * @param goldForecast the Gold the Nation will gain or lose as this Turn
 *        resolves, if nothing but its income, upkeep and Lasting Event
 *        changes it
 * @param lastingEvent the Lasting Event active for the Nation, or null if
 *        none is
 * @param arrivals the other Nations' Warparties that arrived at this one and
 *        wait for the Battle as this Turn ends, taking the Nations in order
 *        and each one's Warparties in the order sent
 * @param tributes the Tribute this Nation offered or was offered this Turn,
 *        in the order offered
 * @param encounters the Encounters this Nation's Warparties had as the last
 *        Turn resolved, with what each Player chose so far, in the order they
 *        met
 * @param techTree every Technology in the Tech Tree, in the ruleset's order,
 *        and whether the Nation has unlocked it, may unlock it or must first
 *        unlock its prerequisites, or only that it is there if it is hidden
 * @param dilemmas the Dilemmas awaiting the Player's choice before this Turn
 *        ends, in the order they happened
 * @param conqueredBy the Nation or Nations that conquered this one, such as
 *        "Rome and Egypt", knocking its Player out of the Game, or null if
 *        none has
 * @param winner the Nation that won the Game, or null while it goes on
 */
public record NationView(int version, int turn, String nation, int gold, int population, int research,
		List<MapNation> map, List<BuildingType> availableBuildings, Map<String, Integer> buildings,
		List<Construction> construction, List<Repair> repairs, int defense, List<BuildingDamage> damage,
		Map<String, Integer> sellValues, List<UnitType> availableUnits,
		Map<String, Integer> army, int scouts, int idleScouts, List<Task> tasks, List<String> located,
		List<Training> training, List<Warparty> warparties, List<Spy> spies, List<Mission> missions,
		List<TravelBracket> travelBrackets,
		int spyTravelTurns, int upkeep, GoldForecast goldForecast, LastingEvent lastingEvent,
		List<ArrivedWarparty> arrivals,
		List<Tribute> tributes, List<Encounter> encounters, List<TechTreeEntry> techTree,
		TurnReport turnReport,
		List<Dilemma> dilemmas, String conqueredBy, String winner) {

	public NationView {
		map = List.copyOf(map);
		availableBuildings = List.copyOf(availableBuildings);
		construction = List.copyOf(construction);
		repairs = List.copyOf(repairs);
		damage = List.copyOf(damage);
		availableUnits = List.copyOf(availableUnits);
		tasks = List.copyOf(tasks);
		located = List.copyOf(located);
		training = List.copyOf(training);
		warparties = List.copyOf(warparties);
		spies = List.copyOf(spies);
		missions = List.copyOf(missions);
		travelBrackets = List.copyOf(travelBrackets);
		arrivals = List.copyOf(arrivals);
		tributes = List.copyOf(tributes);
		encounters = List.copyOf(encounters);
		techTree = List.copyOf(techTree);
		dilemmas = List.copyOf(dilemmas);
	}

	/**
	 * The other Nations still in the Game, in the order they joined.
	 */
	public List<String> otherNations() {
		return map.stream()
			.filter(other -> other.standing() == MapNation.Standing.IN_GAME && !other.name().equals(nation))
			.map(MapNation::name)
			.toList();
	}

}
