package com.miniciv.engine;

/**
 * The Defense a Nation's defensive Buildings of one type have lost and are not
 * yet being repaired or rebuilt for. They lose it one at a time: one that has
 * lost all of its Defense is destroyed, and the next is damaged.
 *
 * @param building the defensive Building type
 * @param destroyed how many of them are destroyed
 * @param damaged the Defense lost by the one damaged but still standing, or 0
 *        if none is
 * @param repairGold the Gold repairing the damaged one costs
 * @param repairTurns how many Turns repairing the damaged one takes
 * @param rebuildGold the Gold rebuilding a destroyed one costs
 * @param rebuildTurns how many Turns rebuilding a destroyed one takes
 */
public record BuildingDamage(String building, int destroyed, int damaged, int repairGold, int repairTurns,
		int rebuildGold, int rebuildTurns) {
}
