package com.miniciv.engine;

/**
 * How exposed a Nation's Population was to a Warparty once its Defense was
 * down: more so if its Army was wiped out in the Battle, or if it had neither
 * Army nor Defense to begin with.
 */
enum Exposure {

	DEFENDED, ARMY_WIPED_OUT, UNDEFENDED;

	static Exposure of(Battle battle, Nation.DefenseWorn defenseWorn) {
		if (battle.army().isEmpty()) {
			return defenseWorn.before() == 0 ? UNDEFENDED : DEFENDED;
		}
		return battle.armySurvivors().isEmpty() ? ARMY_WIPED_OUT : DEFENDED;
	}

}
