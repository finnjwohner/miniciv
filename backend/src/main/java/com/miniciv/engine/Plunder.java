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
	 * Such as "12 Gold and 4 Research", for Turn Reports.
	 */
	String described() {
		return gold + " Gold and " + research + " Research";
	}

}
