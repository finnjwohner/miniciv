package com.miniciv.engine;

/**
 * Gold and Research a Warparty took from the Nation it fought, or Gold and
 * Captives for a Barbarian Camp's Warparty, carried home to its own Nation or
 * Camp.
 *
 * @param captives the Population a Barbarian Camp's Warparty carries off
 */
public record Plunder(int gold, int research, int captives) {

	static final Plunder NONE = new Plunder(0, 0, 0);

	/**
	 * Gold and Research, without Captives.
	 */
	public Plunder(int gold, int research) {
		this(gold, research, 0);
	}

	boolean isNone() {
		return gold == 0 && research == 0 && captives == 0;
	}

	/**
	 * Such as "12 Gold and 4 Research", for Turn Reports, each amount having
	 * the given effect on the Nation reading it.
	 */
	TurnReport.Line described(TurnReport.Amount.Effect effect) {
		return TurnReport.Line.of(new TurnReport.Amount(Resource.GOLD, gold, effect), " and ",
				new TurnReport.Amount(Resource.RESEARCH, research, effect));
	}

	/**
	 * What a Barbarian Camp's Warparty carries off, such as "12 Gold and 3
	 * Population as Captives", or "12 Gold" if it took no Captives, each
	 * amount having the given effect on the Nation reading it.
	 */
	TurnReport.Line carriedOff(TurnReport.Amount.Effect effect) {
		TurnReport.Amount taken = new TurnReport.Amount(Resource.GOLD, gold, effect);
		return captives == 0 ? TurnReport.Line.of(taken)
				: TurnReport.Line.of(taken, " and ", new TurnReport.Amount(Resource.POPULATION, captives, effect),
						" as Captives");
	}

}
