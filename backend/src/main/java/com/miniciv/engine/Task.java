package com.miniciv.engine;

/**
 * What some of a Nation's Scouts are set to do; Scouts on no Task are Idle.
 *
 * @param id the number the Task is told apart by among the Nation's Tasks
 * @param kind what the Scouts do
 * @param target the Nation or Barbarian Camp the Task is about, or null for
 * a Task about no one, such as a Locate Task searching the wilds
 * @param scouts how many Scouts are on the Task
 */
public record Task(int id, Kind kind, String target, int scouts) {

	public enum Kind {

		/**
		 * Search for a Nation or Barbarian Camp not yet Located until it is
		 * Located, or, with no target, the wilds for any Camp until one is.
		 */
		LOCATE,

		/**
		 * Watch for every other Nation's Warparties abroad, with exactly one
		 * Scout, until cancelled.
		 */
		WATCH,

		/**
		 * Hunt every other Nation's travelling Spies, until cancelled.
		 */
		HUNT

	}

	/**
	 * This Task with the given number of Scouts.
	 */
	Task withScouts(int scouts) {
		return new Task(id, kind, target, scouts);
	}

}
