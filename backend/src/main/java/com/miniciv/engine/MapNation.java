package com.miniciv.engine;

/**
 * One Nation on the Map, as every other Nation knows it: its name, and whether
 * it is still in the Game, was conquered or was removed.
 */
public record MapNation(String name, Standing standing) {

	public enum Standing {
		/** Neither conquered nor removed. */
		IN_GAME,
		/** Conquered, its Player knocked out of the Game. */
		CONQUERED,
		/** Removed, its Player having been disconnected for too long. */
		REMOVED
	}

}
