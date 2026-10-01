package com.miniciv.engine;

import java.util.List;

/**
 * One match of miniciv. The engine knows only Nations and Turns: no time,
 * sockets or Players.
 */
public class Game {

	private final List<String> nations;

	private final int turn = 1;

	public Game(List<String> nations) {
		this.nations = List.copyOf(nations);
	}

	public int turn() {
		return turn;
	}

	public List<String> nations() {
		return nations;
	}

}
