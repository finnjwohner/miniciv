package com.miniciv.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Two Warparties from different Nations that met by chance while abroad as
 * the last Turn resolved. Both Players see the other's Units and choose this
 * Turn whether to fight; if either chooses to, they fight as this Turn ends.
 *
 * @param first the Warparty of the Nation that joined the Game first
 * @param second the other Nation's Warparty
 */
public record Encounter(Side first, Side second) {

	/**
	 * One of the Warparties that met, as it was when they met.
	 *
	 * @param nation the Nation that sent it
	 * @param warparty its id among that Nation's Warparties
	 * @param units how many of each Unit type it holds
	 * @param choice what its Player chose, so far
	 */
	public record Side(String nation, int warparty, Map<String, Integer> units, Choice choice) {

		public Side {
			units = Collections.unmodifiableMap(new LinkedHashMap<>(units));
		}

		boolean isFor(String nation, int warparty) {
			return this.nation.equals(nation) && this.warparty == warparty;
		}

		Side chosen(Choice choice) {
			return new Side(nation, warparty, units, choice);
		}

	}

	public enum Choice {

		/**
		 * The Player has not chosen yet; unchosen as the Turn ends, the
		 * Warparty does not fight unless the other's Player chose to.
		 */
		NONE,

		/**
		 * The Player chose to fight, so the Warparties fight as the Turn ends.
		 */
		FIGHT,

		/**
		 * The Player chose not to fight; they fight only if the other's Player
		 * chose to.
		 */
		PASS

	}

	/**
	 * Whether the given Nation sent one of the Warparties.
	 */
	boolean involves(String nation) {
		return first.nation().equals(nation) || second.nation().equals(nation);
	}

	/**
	 * The side of the given Nation's Warparty with the given id, if it is one
	 * of them.
	 */
	Optional<Side> sideOf(String nation, int warparty) {
		return first.isFor(nation, warparty) ? Optional.of(first)
				: second.isFor(nation, warparty) ? Optional.of(second) : Optional.empty();
	}

	/**
	 * The side facing the given one.
	 */
	Side facing(Side side) {
		return side == first ? second : first;
	}

	/**
	 * This Encounter with the given side's choice made.
	 */
	Encounter chosen(Side side, Choice choice) {
		return side == first ? new Encounter(first.chosen(choice), second) : new Encounter(first, second.chosen(choice));
	}

	/**
	 * Whether either Player chose to fight.
	 */
	boolean fought() {
		return first.choice() == Choice.FIGHT || second.choice() == Choice.FIGHT;
	}

}
