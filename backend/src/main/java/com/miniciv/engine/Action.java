package com.miniciv.engine;

/**
 * Something a Player decides for their Nation during a Turn. It applies at
 * once, so the Player sees straight away what they can still afford.
 */
public sealed interface Action {

	/**
	 * Pays a Building's Gold now and starts constructing it.
	 */
	record StartBuilding(String building) implements Action {
	}

	/**
	 * Pays the Gold and Population for a number of Units of one type now and
	 * starts training them.
	 */
	record TrainUnits(String unit, int count) implements Action {
	}

}
