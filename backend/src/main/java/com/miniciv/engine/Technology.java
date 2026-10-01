package com.miniciv.engine;

import java.util.List;

/**
 * An entry in the Tech Tree, unlocked by spending Research once its
 * prerequisites are unlocked.
 *
 * @param prerequisites the Technologies that must be unlocked first
 * @param unlocks the Unit and Building types it gives access to
 */
public record Technology(String name, int research, List<String> prerequisites, List<String> unlocks) {

	public Technology {
		prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
		unlocks = unlocks == null ? List.of() : List.copyOf(unlocks);
	}

}
