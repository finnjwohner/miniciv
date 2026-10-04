package com.miniciv.engine;

/**
 * Gold and Research a Warparty took from the Nation it fought, carried home
 * to its own Nation.
 */
public record Plunder(int gold, int research) {

	static final Plunder NONE = new Plunder(0, 0);

	boolean isNone() {
		return gold == 0 && research == 0;
	}

	/**
	 * Such as "12 Gold and 4 Research", for Turn Reports, each amount having
	 * the given effect on the Nation reading it.
	 */
	TurnReport.Line described(TurnReport.Amount.Effect effect) {
		return TurnReport.Line.of(new TurnReport.Amount(Resource.GOLD, gold, effect), " and ",
				new TurnReport.Amount(Resource.RESEARCH, research, effect));
	}

}
