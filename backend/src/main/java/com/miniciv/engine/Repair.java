package com.miniciv.engine;

/**
 * A Building being repaired or rebuilt, and how many more Turns it needs.
 * Once done, a defensive one restores the Defense paid for, or as much of it as
 * its Building type has lost by then, and any other is no longer Damaged.
 *
 * @param rebuild whether a destroyed Building is being rebuilt, rather than a
 *        damaged one repaired
 * @param defense the Defense it restores, or 0 for a non-defensive Building
 */
public record Repair(String building, boolean rebuild, int defense, int turnsLeft) implements Work {
}
