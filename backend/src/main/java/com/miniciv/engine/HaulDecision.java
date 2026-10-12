package com.miniciv.engine;

/**
 * A Haul a Nation seized from another Nation's Spy it caught carrying it home,
 * or Plunder its Warparty took from another it wiped out in an Encounter,
 * which its Player chooses, until the Turn ends, to keep or send back to the
 * Nation it was stolen from. Either way it arrives 3 Turns after the Turn ends,
 * and nothing can intercept it on the way.
 *
 * @param id the number the decision is told apart by among the Nation's
 * @param stolenFrom the Nation the Haul was stolen from
 * @param haul what the Spy or Warparty carried
 * @param choice what the Player chose so far; unanswered, the Haul is kept
 * @param plunder whether it is Plunder taken in an Encounter rather than a
 *        Haul seized from a caught Spy
 */
public record HaulDecision(int id, String stolenFrom, Haul haul, Choice choice, boolean plunder) {

	public enum Choice {

		/**
		 * Not chosen yet; the Haul is kept if the Turn ends so.
		 */
		NONE,

		/**
		 * Keep the Haul.
		 */
		KEEP,

		/**
		 * Send the Haul back to the Nation it was stolen from.
		 */
		RETURN

	}

	/**
	 * This decision with the given choice.
	 */
	HaulDecision chosen(Choice choice) {
		return new HaulDecision(id, stolenFrom, haul, choice, plunder);
	}

	/**
	 * Whether the Haul is kept: chosen so, or not chosen at all.
	 */
	boolean kept() {
		return choice != Choice.RETURN;
	}

}
