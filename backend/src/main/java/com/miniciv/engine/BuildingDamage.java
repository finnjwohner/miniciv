package com.miniciv.engine;

/**
 * The Defense a Nation's defensive Buildings of one type have lost. They lose
 * it one at a time: one that has lost all of its Defense is destroyed, and the
 * next is damaged.
 *
 * @param building the defensive Building type
 * @param destroyed how many of them are destroyed
 * @param damaged whether one more is damaged but still standing
 * @param defenseLost the Defense they have lost in all
 * @param repairGold the Gold it costs to repair the damaged one and rebuild the
 *        destroyed ones
 */
public record BuildingDamage(String building, int destroyed, boolean damaged, int defenseLost, int repairGold) {
}
