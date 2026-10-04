package com.miniciv.engine;

/**
 * The harm a Nation's Buildings of one type have taken and that is not yet
 * being repaired or rebuilt.
 * <p>
 * Defensive Buildings lose Defense one at a time: one that has lost all of its
 * Defense is destroyed, and the next is damaged. Any other Building is either
 * Damaged or not, and is never destroyed, so its fields for Defense, the
 * destroyed ones and Rebuilding are null.
 *
 * @param building the Building type
 * @param damaged how many of them are Damaged but still standing; for a
 *        defensive type, 1 at most
 * @param defenseLost for a defensive type, the Defense lost by the one Damaged
 *        but still standing, or 0 if none is
 * @param repairGold the Gold Repairing one Damaged one costs, or 0 if none is
 * @param repairTurns how many Turns Repairing one Damaged one takes, or 0 if
 *        none is
 * @param destroyed for a defensive type, how many of them are destroyed
 * @param rebuildGold for a defensive type, the Gold Rebuilding a destroyed one
 *        costs
 * @param rebuildTurns for a defensive type, how many Turns Rebuilding a
 *        destroyed one takes
 */
public record BuildingDamage(String building, int damaged, Integer defenseLost, int repairGold, int repairTurns,
		Integer destroyed, Integer rebuildGold, Integer rebuildTurns) {

	/**
	 * The Defense a defensive Building type has lost.
	 *
	 * @param defenseLost the Defense lost by the one Damaged but still
	 *        standing, or 0 if none is
	 */
	public static BuildingDamage defensive(String building, int destroyed, int defenseLost, int repairGold,
			int repairTurns, int rebuildGold, int rebuildTurns) {
		return new BuildingDamage(building, defenseLost > 0 ? 1 : 0, defenseLost, repairGold, repairTurns, destroyed,
				rebuildGold, rebuildTurns);
	}

	/**
	 * Damaged Buildings of a type that raises no Defense.
	 */
	public static BuildingDamage nonDefensive(String building, int damaged, int repairGold, int repairTurns) {
		return new BuildingDamage(building, damaged, null, repairGold, repairTurns, null, null, null);
	}

}
