package com.miniciv.engine;

/**
 * A defensive Building being repaired or rebuilt, and how many more Turns it
 * needs. Once done, it restores the Defense paid for, or as much of it as its
 * Building type has lost by then.
 *
 * @param rebuild whether a destroyed Building is being rebuilt, rather than a
 *        damaged one repaired
 * @param defense the Defense it restores
 */
public record Repair(String building, boolean rebuild, int defense, int turnsLeft) implements Work {
}
