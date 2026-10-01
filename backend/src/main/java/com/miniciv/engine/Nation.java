package com.miniciv.engine;

import java.util.List;

/**
 * A Nation's state inside the engine.
 */
class Nation {

	private final String name;

	private int gold;

	private int population;

	private int research;

	private TurnReport turnReport;

	Nation(String name, int gold, int population, int research) {
		this.name = name;
		this.gold = gold;
		this.population = population;
		this.research = research;
	}

	void turnReport(TurnReport turnReport) {
		this.turnReport = turnReport;
	}

	NationView view(int turn, List<String> otherNations) {
		return new NationView(turn, name, gold, population, research, otherNations, turnReport);
	}

	void gain(int gold, int population, int research) {
		this.gold += gold;
		this.population += population;
		this.research += research;
	}

}
