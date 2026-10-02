package com.miniciv.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * The fight between a Warparty and the Army of the Nation it arrived at.
 *
 * @param warparty how many of each Unit type the Warparty held
 * @param army how many of each Unit type the Army held
 * @param warpartyLost how many of each Unit type the Warparty lost
 * @param armyLost how many of each Unit type the Army lost
 */
record Battle(Map<String, Integer> warparty, Map<String, Integer> army, Map<String, Integer> warpartyLost,
		Map<String, Integer> armyLost) {

	/**
	 * How much a Unit's Strength is multiplied against a Unit type it counters.
	 */
	private static final double COUNTER_MULTIPLIER = 2;

	/**
	 * How far luck can move each side's Strength up or down, as a share of it.
	 */
	private static final double LUCK = 0.1;

	Battle {
		warparty = Collections.unmodifiableMap(new LinkedHashMap<>(warparty));
		army = Collections.unmodifiableMap(new LinkedHashMap<>(army));
		warpartyLost = Collections.unmodifiableMap(new LinkedHashMap<>(warpartyLost));
		armyLost = Collections.unmodifiableMap(new LinkedHashMap<>(armyLost));
	}

	/**
	 * Fights a Battle. Each side's Strength is the sum of its Units' Strength,
	 * each multiplied by how many of the other side's Units it counters, and
	 * then moved a little by luck, drawn for the Warparty and then the Army.
	 * Each side then loses a share of its Units that grows with the
	 * square of how much stronger the other side was: half each in an even
	 * fight, and all of the weaker side's once the other is about 1.4 times as
	 * strong. Units without Strength take no part.
	 */
	static Battle fight(Map<String, Integer> warparty, Map<String, Integer> army, Ruleset ruleset,
			RandomGenerator random) {
		Map<String, Integer> warpartyFighting = withStrength(warparty, ruleset);
		Map<String, Integer> armyFighting = withStrength(army, ruleset);
		double warpartyStrength = strength(warpartyFighting, armyFighting, ruleset) * luck(random);
		double armyStrength = strength(armyFighting, warpartyFighting, ruleset) * luck(random);
		return new Battle(warparty, army, losses(warpartyFighting, lossShare(warpartyStrength, armyStrength)),
				losses(armyFighting, lossShare(armyStrength, warpartyStrength)));
	}

	Map<String, Integer> warpartySurvivors() {
		return survivors(warparty, warpartyLost);
	}

	/**
	 * The Warparty's surviving Units' Strength, without counters or luck.
	 */
	double warpartySurvivingStrength(Ruleset ruleset) {
		return warpartySurvivors().entrySet()
			.stream()
			.mapToDouble(s -> ruleset.unit(s.getKey()).orElseThrow().strength() * s.getValue())
			.sum();
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
