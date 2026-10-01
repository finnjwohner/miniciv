package com.miniciv.engine;

import java.util.List;

/**
 * What one Nation's Player may see on a Turn: their own state, the other
 * Nations' names and nothing more about them, and the Turn Report of the Turn
 * before, which is null on the first Turn.
 */
public record NationView(int turn, String nation, int gold, int population, int research, List<String> otherNations,
		TurnReport turnReport) {

	public NationView {
		otherNations = List.copyOf(otherNations);
	}

}
