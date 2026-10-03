package com.miniciv.engine;

/**
 * One Nation on the Map, as another Nation knows it: its name, Ruler, colour
 * and emblem, public from the start, whether it is still in the Game, was
 * conquered or was removed, and what that other Nation has learned of its
 * hidden state.
 *
 * @param facts what the Nation whose Map it is has learned of this one; nothing
 *        of its own Nation, which it sees in full elsewhere
 */
public record MapNation(String name, String ruler, String colour, String emblem, Standing standing, Facts facts) {

	/**
	 * A Nation on the Map as its Player chose it to be known.
	 */
	public MapNation(NationIdentity identity, Standing standing, Facts facts) {
		this(identity.name(), identity.ruler(), identity.colour(), identity.emblem(), standing, facts);
	}

	/**
	 * A Nation on the Map with nothing learned of it.
	 */
	public MapNation(NationIdentity identity, Standing standing) {
		this(identity, standing, Facts.NONE);
	}

	public enum Standing {
		/** Neither conquered nor removed. */
		IN_GAME,
		/** Conquered, its Player knocked out of the Game. */
		CONQUERED,
		/** Removed, its Player having been disconnected for too long. */
		REMOVED
	}

}
