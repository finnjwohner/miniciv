package com.miniciv.engine;

/**
 * A Barbarian Camp still standing on the Map, as a Nation that has heard of it
 * knows it: its name, Ruler, colour and emblem, and what Locating it showed. A
 * destroyed Camp vanishes from the Map.
 *
 * @param located what Locating the Camp showed, or null while it is not
 *        Located
 */
public record MapCamp(String name, String ruler, String colour, String emblem, CampFacts located) {

	MapCamp(NationIdentity identity, CampFacts located) {
		this(identity.name(), identity.ruler(), identity.colour(), identity.emblem(), located);
	}

}
