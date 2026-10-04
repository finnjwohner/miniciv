package com.miniciv.engine;

/**
 * The resources a Nation owns and spends.
 */
public enum Resource {

	GOLD("Gold"),

	POPULATION("Population"),

	RESEARCH("Research");

	private final String name;

	Resource(String name) {
		this.name = name;
	}

	/**
	 * Such as "Gold", for Turn Reports.
	 */
	String named() {
		return name;
	}

}
