package com.miniciv.engine;

/**
 * A Barbarian Camp on the Map, as a Nation that has heard of it knows it: its
 * name, Ruler, colour and emblem, whether it was destroyed, and what Locating
 * it showed.
 *
 * @param located what Locating the Camp showed, or null while it is not
 *        Located
 */
public record MapCamp(String name, String ruler, String colour, String emblem, boolean destroyed,
		CampFacts located) {

	MapCamp(NationIdentity identity, boolean destroyed, CampFacts located) {
		this(identity.name(), identity.ruler(), identity.colour(), identity.emblem(), destroyed, located);
	}

}
