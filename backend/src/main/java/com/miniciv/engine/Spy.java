package com.miniciv.engine;

/**
 * A Spy a Nation has sent abroad: travelling to its target, staying there,
 * Idle or on a Mission, or heading home once its target is out of the Game or
 * it was Unmasked.
 *
 * @param id the number telling it apart from the Nation's other Spies,
 * counting up in the order sent
 * @param unit the Spy's Unit type
 * @param target the Nation the Spy was sent to
 * @param homeward whether it is heading home, its target being out of the Game
 * or it having been Unmasked
 * @param turnsLeft how many more Turns must resolve before it arrives where it
 * is heading; 0 once it is in place at its target
 * @param turnsTravelled how many Turns it travelled toward its target
 * @param mission the name of the Mission it is on, or null while it is Idle
 * @param missionTurnsLeft how many more Turns must resolve before its Mission
 * ends; 0 while it is Idle
 */
public record Spy(int id, String unit, String target, boolean homeward, int turnsLeft, int turnsTravelled,
		String mission, int missionTurnsLeft) {

	/**
	 * How many Turns a Spy takes to reach another Nation.
	 */
	static final int TRAVEL_TURNS = 3;

	/**
	 * A Spy just sent to a target, Idle.
	 */
	static Spy sent(int id, String unit, String target) {
		return new Spy(id, unit, target, false, TRAVEL_TURNS, 0, null, 0);
	}

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
		return new Spy(id, unit, target, homeward, turnsLeft - 1, homeward ? turnsTravelled : turnsTravelled + 1, mission,
				missionTurnsLeft);
	}

	/**
	 * This Spy turning back home, giving up any Mission: the trip home takes
	 * as long as it travelled toward its target, and at least one Turn.
	 */
	Spy turnedBack() {
		return new Spy(id, unit, target, true, Math.max(1, turnsTravelled), turnsTravelled, null, 0);
	}

	/**
	 * This Spy starting a Mission from scratch, giving up any other.
	 */
	Spy onMission(Mission mission) {
		return new Spy(id, unit, target, homeward, turnsLeft, turnsTravelled, mission.name(), mission.turns());
	}

	/**
	 * This Spy one Turn further into its Mission, Idle once the Mission ends.
	 */
	Spy workedOnMission() {
		return missionTurnsLeft > 1
				? new Spy(id, unit, target, homeward, turnsLeft, turnsTravelled, mission, missionTurnsLeft - 1)
				: new Spy(id, unit, target, homeward, turnsLeft, turnsTravelled, null, 0);
	}

}
