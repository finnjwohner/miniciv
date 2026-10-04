package com.miniciv.engine;

/**
 * What some of a Nation's Scouts are set to do; Scouts on no Task are Idle.
 *
 * @param id the number the Task is told apart by among the Nation's Tasks
 * @param kind what the Scouts do
 * @param target the Nation the Task is about
 * @param scouts how many Scouts are on the Task
 */
public record Task(int id, Kind kind, String target, int scouts) {

	public enum Kind {

		/**
		 * Search for a Nation not yet Located until it is Located.
		 */
		LOCATE

	}

	/**
	 * This Task with the given number of Scouts.
	 */
	Task withScouts(int scouts) {
		return new Task(id, kind, target, scouts);
	}

}
