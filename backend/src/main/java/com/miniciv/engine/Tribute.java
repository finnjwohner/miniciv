package com.miniciv.engine;

/**
 * Gold and Population a defending Nation offered an attacker this Turn in
 * return for Recalling one of its Warparties that arrived there.
 *
 * @param from the defending Nation that offered it
 * @param to the attacking Nation it was offered to
 * @param warparty the id of the attacker's Warparty it was offered for
 * @param answer what the attacker answered, so far
 */
public record Tribute(String from, String to, int warparty, int gold, int population, Answer answer) {

	public enum Answer {

		/**
		 * The attacker has not answered yet; unanswered as the Turn ends, the
		 * offer lapses and the Battle goes ahead.
		 */
		NONE,

		/**
		 * The attacker took the Tribute and Recalled the Warparty.
		 */
		ACCEPTED,

		/**
		 * The attacker refused, so the Battle goes ahead.
		 */
		REFUSED

	}

	/**
	 * Whether it was offered for the given Nation's Warparty with the given id.
	 */
	boolean isFor(String attacker, int id) {
		return to.equals(attacker) && warparty == id;
	}

	/**
	 * Whether the given Nation offered it or was offered it.
	 */
	boolean involves(String nation) {
		return from.equals(nation) || to.equals(nation);
	}

	Tribute answered(Answer answer) {
		return new Tribute(from, to, warparty, gold, population, answer);
	}

	/**
	 * Such as "20 Gold and 3 Population", for Turn Reports, each amount having
	 * the given effect on the Nation reading it.
	 */
	TurnReport.Line described(TurnReport.Amount.Effect effect) {
		TurnReport.Amount goldAmount = new TurnReport.Amount(Resource.GOLD, gold, effect);
		TurnReport.Amount populationAmount = new TurnReport.Amount(Resource.POPULATION, population, effect);
		if (population == 0) {
			return TurnReport.Line.of(goldAmount);
		}
		if (gold == 0) {
			return TurnReport.Line.of(populationAmount);
		}
		return TurnReport.Line.of(goldAmount, " and ", populationAmount);
	}

}
