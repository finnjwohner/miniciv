package com.miniciv.engine;

import java.util.List;

/**
 * A Technology in the Tech Tree as one Nation stands with it. A hidden one is
 * an anonymous placeholder, with no name, cost, prerequisites or unlocks.
 *
 * @param research the Research it costs to unlock
 * @param prerequisites the Technologies that must be unlocked first
 * @param unlocks the Unit and Building types it gives access to
 */
public record TechTreeEntry(String name, int research, List<String> prerequisites, List<String> unlocks,
		Standing standing) {

	public TechTreeEntry {
		prerequisites = List.copyOf(prerequisites);
		unlocks = List.copyOf(unlocks);
	}

	static TechTreeEntry hidden() {
		return new TechTreeEntry(null, 0, List.of(), List.of(), Standing.HIDDEN);
	}

	public enum Standing {

		/**
		 * The Nation has unlocked it.
		 */
		UNLOCKED,

		/**
		 * Its prerequisites are unlocked, so the Nation may unlock it once it has
		 * the Research.
		 */
		AVAILABLE,

		/**
		 * Some of its prerequisites are not yet unlocked, but each is unlocked or
		 * available.
		 */
		LOCKED,

		/**
		 * Some of its prerequisites are neither unlocked nor available, so the
		 * Nation knows nothing about it.
		 */
		HIDDEN

	}

}
