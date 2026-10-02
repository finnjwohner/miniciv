package com.miniciv.engine;

/**
 * One Nation on the Map, as another Nation knows it: its name, whether it is
 * still in the Game, was conquered or was removed, and what that other Nation
 * has learned of its hidden state.
 *
 * @param facts what the Nation whose Map it is has learned of this one; nothing
 *        of its own Nation, which it sees in full elsewhere
 */
public record MapNation(String name, Standing standing, Facts facts) {

	/**
	 * A Nation on the Map with nothing learned of it.
	 */
	public MapNation(String name, Standing standing) {
		this(name, standing, Facts.NONE);
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
