package com.miniciv.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Units a Nation has sent abroad: travelling to their target, waiting there
 * for the Battle, or heading home from it.
 *
 * @param id the number telling it apart from the Nation's other Warparties,
 * counting up in the order sent
 * @param target the Nation the Warparty was sent to
 * @param units how many of each Unit type it holds
 * @param strength its Units' Strength added up, without counters
 * @param homeward whether it has fought at its target and is heading home
 * @param turnsLeft how many more Turns must resolve before it arrives where it
 * is heading; 0 once it has arrived at its target and waits for the Battle
 * @param turnsTravelled how many Turns it travelled toward its target, delays
 * included
 * @param plunder what it took from its target and is carrying home
 * @param strengthSent its Strength when it was sent, before any losses on the
 * way
 */
public record Warparty(int id, String target, Map<String, Integer> units, double strength, boolean homeward,
		int turnsLeft, int turnsTravelled, Plunder plunder, double strengthSent) {

	public Warparty {
		units = Collections.unmodifiableMap(new LinkedHashMap<>(units));
	}

	/**
	 * A Warparty that has lost no Strength since it was sent.
	 */
	public Warparty(int id, String target, Map<String, Integer> units, double strength, boolean homeward,
			int turnsLeft, int turnsTravelled, Plunder plunder) {
		this(id, target, units, strength, homeward, turnsLeft, turnsTravelled, plunder, strength);
	}

	/**
	 * A Warparty carrying no plunder that has lost no Strength since it was
	 * sent.
	 */
	public Warparty(int id, String target, Map<String, Integer> units, double strength, boolean homeward,
			int turnsLeft, int turnsTravelled) {
		this(id, target, units, strength, homeward, turnsLeft, turnsTravelled, Plunder.NONE);
	}

	/**
	 * A Warparty setting off for its target, taking as many Turns as the
	 * bracket for its Strength.
	 */
	static Warparty sent(int id, String target, Map<String, Integer> units, Ruleset ruleset) {
		double strength = ruleset.strengthOf(units);
		return new Warparty(id, target, units, strength, false, TravelBracket.turnsOut(strength), 0);
	}

	/**
	 * Whether it has arrived at its target and waits for the Battle.
	 */
	boolean awaitingBattle() {
		return !homeward && turnsLeft == 0;
	}

	/**
	 * Whether it is travelling to the given Nation and has not yet arrived.
	 */
	boolean approaching(String nation) {
		return !homeward && turnsLeft > 0 && target.equals(nation);
	}

	/**
	 * This Warparty one Turn closer to where it is heading.
	 */
	Warparty travelled() {
		return new Warparty(id, target, units, strength, homeward, turnsLeft - 1, homeward ? turnsTravelled : turnsTravelled + 1, plunder,
				strengthSent);
	}

	/**
	 * This Warparty, having fought at its target, setting off home with its
	 * surviving Units and its plunder, taking as many Turns as the bracket for
	 * its surviving Strength and more for its haul.
	 *
	 * @param haul what it carries home, whether plundered or seized
	 */
	Warparty headingHome(Map<String, Integer> survivors, Plunder plunder, Plunder haul, Ruleset ruleset) {
		double survivingStrength = ruleset.strengthOf(survivors);
		return new Warparty(id, target, survivors, survivingStrength, true,
				TravelBracket.turnsHome(survivingStrength, haul), turnsTravelled, plunder, strengthSent);
	}

	/**
	 * This Warparty, before it has fought at its target, turning back home: the
	 * trip home takes as long as it has travelled so far, less in proportion to
	 * how much faster the bracket for its Strength now travels than the one for
	 * its Strength when sent, rounded up, and at least one Turn.
	 */
	Warparty turnedBack() {
		int turnsHome = (int) Math.ceil((double) turnsTravelled * TravelBracket.turnsOut(strength)
				/ TravelBracket.turnsOut(strengthSent));
		return new Warparty(id, target, units, strength, true, Math.max(1, Math.min(turnsTravelled, turnsHome)),
				turnsTravelled, plunder, strengthSent);
	}

	/**
	 * How many Units it holds, of every type.
	 */
	int unitCount() {
		return units.values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * This Warparty with its trip changed by the given number of Turns, still
	 * taking at least one more.
	 */
	Warparty delayed(int turns) {
		return new Warparty(id, target, units, strength, homeward, Math.max(1, turnsLeft + turns), turnsTravelled, plunder,
				strengthSent);
	}

	/**
	 * This Warparty holding only the given Units.
	 */
	Warparty with(Map<String, Integer> units, Ruleset ruleset) {
		return new Warparty(id, target, units, ruleset.strengthOf(units), homeward, turnsLeft, turnsTravelled, plunder,
				strengthSent);
	}

}
