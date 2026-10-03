package com.miniciv.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

/**
 * The fight between the Warparties that arrived at a Nation on the same Turn,
 * fighting as one, and the Nation's Army.
 *
 * @param warparties how many of each Unit type each Warparty held
 * @param army how many of each Unit type the Army held
 * @param warpartiesLost how many of each Unit type each Warparty lost
 * @param armyLost how many of each Unit type the Army lost
 */
record Battle(List<Map<String, Integer>> warparties, Map<String, Integer> army,
		List<Map<String, Integer>> warpartiesLost, Map<String, Integer> armyLost) {

	/**
	 * How much a Unit's Strength is multiplied against a Unit type it counters.
	 */
	private static final double COUNTER_MULTIPLIER = 2;

	/**
	 * How far luck can move each side's Strength up or down, as a share of it.
	 */
	private static final double LUCK = 0.1;

	Battle {
		warparties = warparties.stream().map(Battle::copied).toList();
		army = copied(army);
		warpartiesLost = warpartiesLost.stream().map(Battle::copied).toList();
		armyLost = copied(armyLost);
	}

	private static Map<String, Integer> copied(Map<String, Integer> units) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(units));
	}

	/**
	 * Fights a Battle. The Warparties fight as one side: its Strength is the
	 * sum of all their Units' Strength, each multiplied by how many of the
	 * Army's Units it counters, and the Army's is multiplied by how many of all
	 * the Warparties' Units it counters; each is then moved a little by luck,
	 * drawn for the Warparties and then the Army. Each side then loses a share
	 * of its Units that grows with the square of how much stronger the other
	 * side was: half each in an even fight, and all of the weaker side's once
	 * the other is about 1.4 times as strong. Every Warparty loses the same
	 * share of its own Units. Units without Strength take no part.
	 *
	 * @param warparties how many of each Unit type each Warparty holds
	 */
	static Battle fight(List<Map<String, Integer>> warparties, Map<String, Integer> army, Ruleset ruleset,
			RandomGenerator random) {
		List<Map<String, Integer>> warpartiesFighting = warparties.stream().map(w -> withStrength(w, ruleset)).toList();
		Map<String, Integer> attacking = new LinkedHashMap<>();
		warpartiesFighting.forEach(w -> w.forEach((unit, count) -> attacking.merge(unit, count, Integer::sum)));
		Map<String, Integer> armyFighting = withStrength(army, ruleset);
		double warpartiesStrength = strength(attacking, armyFighting, ruleset) * luck(random);
		double armyStrength = strength(armyFighting, attacking, ruleset) * luck(random);
		double warpartyShare = lossShare(warpartiesStrength, armyStrength);
		return new Battle(warparties, army, warpartiesFighting.stream().map(w -> losses(w, warpartyShare)).toList(),
				losses(armyFighting, lossShare(armyStrength, warpartiesStrength)));
	}

	/**
	 * The surviving Units of the Warparty at the given index.
	 */
	Map<String, Integer> warpartySurvivors(int warparty) {
		return survivors(warparties.get(warparty), warpartiesLost.get(warparty));
	}

	/**
	 * Whether any of the Warparties' Units survived.
	 */
	boolean anyWarpartySurvived() {
		return IntStream.range(0, warparties.size()).anyMatch(w -> !warpartySurvivors(w).isEmpty());
	}

	Map<String, Integer> armySurvivors() {
		return survivors(army, armyLost);
	}

	/**
	 * The surviving Units' Strength of the Warparty at the given index, without
	 * counters or luck.
	 */
	double warpartySurvivingStrength(int warparty, Ruleset ruleset) {
		return ruleset.strengthOf(warpartySurvivors(warparty));
	}

	/**
	 * The Units that take part in a Battle: those with Strength.
	 */
	private static Map<String, Integer> withStrength(Map<String, Integer> units, Ruleset ruleset) {
		Map<String, Integer> fighting = new LinkedHashMap<>();
		units.forEach((unit, count) -> {
			if (ruleset.unit(unit).orElseThrow().strength() > 0) {
				fighting.put(unit, count);
			}
		});
		return fighting;
	}

	/**
	 * A side's Strength against the other side's Units: each Unit's Strength
	 * is multiplied against the share of the other side's Units it counters.
	 */
	private static double strength(Map<String, Integer> side, Map<String, Integer> other, Ruleset ruleset) {
		int otherCount = count(other);
		double strength = 0;
		for (Map.Entry<String, Integer> units : side.entrySet()) {
			UnitType type = ruleset.unit(units.getKey()).orElseThrow();
			int countered = type.counters().stream().mapToInt(c -> other.getOrDefault(c, 0)).sum();
			double counteredShare = otherCount == 0 ? 0 : (double) countered / otherCount;
			strength += type.strength() * units.getValue() * (1 + (COUNTER_MULTIPLIER - 1) * counteredShare);
		}
		return strength;
	}

	private static double luck(RandomGenerator random) {
		return 1 + LUCK * (2 * random.nextDouble() - 1);
	}

	/**
	 * The share of its Units a side with the given Strength loses against the
	 * other side's.
	 */
	private static double lossShare(double own, double other) {
		if (own == 0) {
			return 0;
		}
		double ratio = other / own;
		return Math.min(1, ratio * ratio / 2);
	}

	/**
	 * The Units a side loses: its whole count times the share, rounded, spread
	 * over its Unit types in proportion to how many of each it has, the
	 * leftover Units going to the types with the largest remainders, and
	 * between equal remainders to the type listed first.
	 */
	private static Map<String, Integer> losses(Map<String, Integer> side, double share) {
		int toLose = (int) Math.round(count(side) * share);
		Map<String, Integer> lost = new LinkedHashMap<>();
		int lostSoFar = 0;
		for (Map.Entry<String, Integer> units : side.entrySet()) {
			int whole = (int) Math.floor(units.getValue() * share);
			lost.put(units.getKey(), whole);
			lostSoFar += whole;
		}
		List<String> byRemainder = new ArrayList<>(side.keySet());
		byRemainder.sort(Comparator.comparingDouble((String unit) -> {
			double exact = side.get(unit) * share;
			return exact - Math.floor(exact);
		}).reversed());
		for (int i = 0; lostSoFar < toLose; i++, lostSoFar++) {
			lost.merge(byRemainder.get(i), 1, Integer::sum);
		}
		lost.values().removeIf(count -> count == 0);
		return lost;
	}

	private static Map<String, Integer> survivors(Map<String, Integer> units, Map<String, Integer> lost) {
		Map<String, Integer> survivors = new LinkedHashMap<>();
		units.forEach((unit, count) -> {
			int left = count - lost.getOrDefault(unit, 0);
			if (left > 0) {
				survivors.put(unit, left);
			}
		});
		return survivors;
	}

	private static int count(Map<String, Integer> units) {
		return units.values().stream().mapToInt(Integer::intValue).sum();
	}

}
