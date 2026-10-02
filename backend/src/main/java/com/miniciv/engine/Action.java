package com.miniciv.engine;

import java.util.Map;

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
	 * Pays the Gold now to repair a defensive Building type's damaged Building
	 * and rebuild its destroyed ones, restoring all the Defense they lost.
	 */
	record RepairBuilding(String building) implements Action {
	}

	/**
	 * Pays the Gold and Population for a number of Units of one type now and
	 * starts training them.
	 */
	record TrainUnits(String unit, int count) implements Action {
	}

	/**
	 * Sends Units from the Army to another Nation as a Warparty.
	 *
	 * @param units how many of each Unit type to send
	 */
	record SendWarparty(String target, Map<String, Integer> units) implements Action {
	}

}
