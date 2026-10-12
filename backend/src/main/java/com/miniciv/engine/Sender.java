package com.miniciv.engine;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Nation or Barbarian Camp, as what sends Warparties: they fight Battles
 * and Encounters alike, whoever sent them.
 */
interface Sender {

	String name();

	/**
	 * The sender named by its Ruler, such as "Caesar of Rome", for what it
	 * directly did to a Nation.
	 */
	String ruled();

	/**
	 * Whether it is a Barbarian Camp rather than a Nation.
	 */
	boolean camp();

	/**
	 * Its Warparties abroad, in the order sent.
	 */
	List<Warparty> warparties();

	/**
	 * Its Warparty abroad with the given number, if any.
	 */
	Optional<Warparty> warparty(int id);

	/**
	 * Its Warparties that have arrived at their target and await the Battle,
	 * in the order sent.
	 */
	default List<Warparty> awaitingBattle() {
		return warparties().stream().filter(Warparty::awaitingBattle).toList();
	}

	/**
	 * Sends one of the Warparties awaiting the Battle home from it with its
	 * surviving Units and its plunder; one with no survivors disappears.
	 *
	 * @param warparty the Warparty, as {@link #awaitingBattle()} gave it
	 * @param haul what it carries home, which sets how long it takes
	 */
	void foughtBattle(Warparty warparty, Map<String, Integer> survivors, Plunder plunder, Plunder haul, Ruleset ruleset);

	/**
	 * Leaves one of the Warparties abroad with the Units that survived
	 * fighting in an Encounter, travelling on as before; one with no survivors
	 * disappears.
	 *
	 * @param warparty the Warparty, as {@link #warparty(int)} gave it
	 */
	void foughtEncounter(Warparty warparty, Map<String, Integer> survivors, Ruleset ruleset);

}
