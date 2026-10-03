package com.miniciv.engine;

/**
 * A Spy a Nation has sent abroad: travelling to its target, staying there and
 * now and then reporting a snapshot of its hidden state, or heading home once
 * its target is out of the Game.
 *
 * @param id the number telling it apart from the Nation's other Spies,
 * counting up in the order sent
 * @param unit the Spy's Unit type
 * @param target the Nation the Spy was sent to
 * @param homeward whether it is heading home, its target being out of the Game
 * @param turnsLeft how many more Turns must resolve before it arrives where it
 * is heading; 0 once it is in place at its target
 * @param turnsTravelled how many Turns it travelled toward its target
 */
public record Spy(int id, String unit, String target, boolean homeward, int turnsLeft, int turnsTravelled) {

	/**
	 * How many Turns a Spy takes to reach another Nation.
	 */
	static final int TRAVEL_TURNS = 3;

	/**
	 * Whether it has arrived at its target and stays there.
	 */
	boolean inPlace() {
		return !homeward && turnsLeft == 0;
	}

	/**
	 * This Spy one Turn closer to where it is heading.
	 */
	Spy travelled() {
		return new Spy(id, unit, target, homeward, turnsLeft - 1, homeward ? turnsTravelled : turnsTravelled + 1);
	}

	/**
	 * This Spy turning back home: the trip home takes as long as it travelled
	 * toward its target, and at least one Turn.
	 */
	Spy turnedBack() {
		return new Spy(id, unit, target, true, Math.max(1, turnsTravelled), turnsTravelled);
	}

}
