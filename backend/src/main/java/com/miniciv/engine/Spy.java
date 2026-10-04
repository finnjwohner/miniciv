package com.miniciv.engine;

/**
 * A Spy a Nation has sent abroad: travelling to its target, staying there,
 * Idle, on a Mission or Escaping with what it will steal, or heading home once
 * its target is out of the Game, it was Unmasked, or it is carrying a Haul.
 *
 * @param id the number telling it apart from the Nation's other Spies,
 * counting up in the order sent
 * @param unit the Spy's Unit type
 * @param target the Nation the Spy was sent to
 * @param homeward whether it is heading home, its target being out of the Game,
 * it having been Unmasked or it carrying a Haul
 * @param turnsLeft how many more Turns must resolve before it arrives where it
 * is heading; 0 once it is in place at its target
 * @param turnsTravelled how many Turns it travelled toward its target
 * @param mission the name of the Mission it is on, or of the Steal Mission it
 * succeeded at while Escaping; null while it is Idle
 * @param missionTurnsLeft how many more Turns must resolve before its Mission
 * ends; 0 while it is Idle or Escaping
 * @param escaping whether its Steal Mission succeeded and, until the next Turn
 * has resolved, it is in place to take its Haul, out of anyone's reach
 * @param haul the Haul it took, or null if none
 */
public record Spy(int id, String unit, String target, boolean homeward, int turnsLeft, int turnsTravelled,
		String mission, int missionTurnsLeft, boolean escaping, Haul haul) {

	/**
	 * How many Turns a Spy takes to reach another Nation, and to carry a Haul
	 * home.
	 */
	static final int TRAVEL_TURNS = 3;

	/**
	 * A Spy just sent to a target, Idle.
	 */
	static Spy sent(int id, String unit, String target) {
		return new Spy(id, unit, target, false, TRAVEL_TURNS, 0, null, 0, false, null);
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
				missionTurnsLeft, escaping, haul);
	}

	/**
	 * This Spy turning back home, giving up any Mission and carrying any Haul
	 * it took: the trip home takes as long as it travelled toward its target,
	 * and at least one Turn.
	 */
	Spy turnedBack() {
		return new Spy(id, unit, target, true, Math.max(1, turnsTravelled), turnsTravelled, null, 0, false, haul);
	}

	/**
	 * This Spy starting a Mission from scratch, giving up any other.
	 */
	Spy onMission(Mission mission) {
		return new Spy(id, unit, target, homeward, turnsLeft, turnsTravelled, mission.name(), mission.turns(), false,
				null);
	}

	/**
	 * This Spy one Turn further into its Mission, Idle once the Mission ends.
	 */
	Spy workedOnMission() {
		return missionTurnsLeft > 1
				? new Spy(id, unit, target, homeward, turnsLeft, turnsTravelled, mission, missionTurnsLeft - 1, false, null)
				: idle();
	}

	/**
	 * This Spy Escaping, having succeeded at the given Steal Mission.
	 */
	Spy escaping(Mission stealing) {
		return new Spy(id, unit, target, homeward, turnsLeft, turnsTravelled, stealing.name(), 0, true, null);
	}

	/**
	 * This Spy, Escaping, having taken the given Haul.
	 */
	Spy took(Haul taken) {
		return new Spy(id, unit, target, homeward, turnsLeft, turnsTravelled, mission, missionTurnsLeft, escaping, taken);
	}

	/**
	 * This Spy setting off home carrying its Haul.
	 */
	Spy carryingHaulHome() {
		return new Spy(id, unit, target, true, TRAVEL_TURNS, turnsTravelled, null, 0, false, haul);
	}

	/**
	 * This Spy in place at its target and Idle.
	 */
	Spy idle() {
		return new Spy(id, unit, target, homeward, turnsLeft, turnsTravelled, null, 0, false, null);
	}

}
