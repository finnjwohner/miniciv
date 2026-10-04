package com.miniciv.engine;

/**
 * The Gold a Nation will gain or lose as this Turn resolves, given its state
 * now, if no Event, Dilemma, Battle, Plunder or Tribute changes it.
 *
 * @param income the Gold its Population and Buildings will earn
 * @param upkeep the Gold its Units will cost, after any that cannot be paid
 *        for desert
 * @param lastingGold how much its active Lasting Event will change its Gold
 *        by, or 0 if it has none
 */
public record GoldForecast(int income, int upkeep, int lastingGold) {

	/**
	 * A forecast with no Lasting Event.
	 */
	public GoldForecast(int income, int upkeep) {
		this(income, upkeep, 0);
	}

	/**
	 * How much the Nation's Gold will change by.
	 */
	public int change() {
		return income + lastingGold - upkeep;
	}

}
