package com.miniciv.engine;

/**
 * Who a Nation is, chosen by its Player in the Room and public to every other
 * Player from the start of the Game.
 *
 * @param name the Nation's name
 * @param ruler the name of the Nation's Ruler, such as "Queen Aster"
 * @param colour the Nation's colour, such as "#e2574c"
 * @param emblem the Nation's emblem, as a pixel icon name such as "crown"
 */
public record NationIdentity(String name, String ruler, String colour, String emblem) {

	/**
	 * The Nation named by its Ruler, such as "Queen Aster of Volantis", for
	 * what it directly did to another Nation.
	 */
	public String ruled() {
		return ruler + " of " + name;
	}

}
