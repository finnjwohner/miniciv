package com.miniciv.engine;

import java.util.List;

/**
 * What happened to one Nation as a Turn resolved, told to its Player at the
 * start of the next Turn.
 *
 * @param turn the Turn that resolved
 */
public record TurnReport(int turn, List<String> lines) {

	public TurnReport {
		lines = List.copyOf(lines);
	}

}
