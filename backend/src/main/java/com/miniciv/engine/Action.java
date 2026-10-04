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
	 * Pays now to repair one of a Building type's Damaged Buildings, which
	 * takes some Turns.
	 */
	record RepairBuilding(String building) implements Action {
	}

	/**
	 * Pays now to rebuild one of a defensive Building type's destroyed
	 * Buildings, which takes as long as constructing it.
	 */
	record RebuildBuilding(String building) implements Action {
	}

	/**
	 * Gives up one of the Nation's standing Buildings of a type at once for
	 * Gold: an undamaged one if it has any, for half its Gold, or else a
	 * Damaged one, for a quarter.
	 */
	record SellBuilding(String building) implements Action {
	}

	/**
	 * Pays the Gold and Population for a number of Units of one type now and
	 * starts training them.
	 */
	record TrainUnits(String unit, int count) implements Action {
	}

	/**
	 * Sends Units from the Army to another Nation it has Located, as a Warparty.
	 *
	 * @param units how many of each Unit type to send
	 */
	record SendWarparty(String target, Map<String, Integer> units) implements Action {
	}

	/**
	 * Sends a Spy from the Army to another Nation it has Located, where it
	 * stays and now and then reports its hidden state.
	 */
	record SendSpy(String target) implements Action {
	}

	/**
	 * Sets some of the Nation's Idle Scouts to a Task.
	 *
	 * @param kind the name of a {@link Task.Kind}, such as LOCATE
	 * @param target the Nation the Task is about
	 * @param scouts how many Idle Scouts to set to it
	 */
	record SetTask(String kind, String target, int scouts) implements Action {
	}

	/**
	 * Ends one of the Nation's Tasks, its Scouts going Idle.
	 *
	 * @param task the Task's id
	 */
	record CancelTask(int task) implements Action {
	}

	/**
	 * Recalls one of the Nation's Warparties not yet heading home: it turns back
	 * at once, taking as long to get home as it has travelled.
	 *
	 * @param warparty the Warparty's id
	 */
	record RecallWarparty(int warparty) implements Action {
	}

	/**
	 * Offers Tribute to the Nation whose Warparty arrived at this one, in
	 * return for Recalling it; the offer reaches that Nation's Player at once.
	 *
	 * @param nation the Nation that sent the Warparty
	 * @param warparty the Warparty's id among that Nation's Warparties
	 */
	record OfferTribute(String nation, int warparty, int gold, int population) implements Action {
	}

	/**
	 * Accepts or refuses the Tribute offered for one of the Nation's
	 * Warparties. Accepting takes the Tribute at once and Recalls the
	 * Warparty, so there is no Battle.
	 *
	 * @param warparty the Warparty's id
	 */
	record AnswerTribute(int warparty, boolean accept) implements Action {
	}

	/**
	 * Chooses whether one of the Nation's Warparties fights the other Nation's
	 * Warparty it met; if either Player chooses to, they fight as the Turn
	 * ends.
	 *
	 * @param warparty the Warparty's id
	 */
	record AnswerEncounter(int warparty, boolean fight) implements Action {
	}

	/**
	 * Makes a choice for one of the Nation's Dilemmas, which applies as the
	 * Turn ends.
	 *
	 * @param dilemma the Dilemma's name
	 * @param choice the name of the choice made
	 */
	record AnswerDilemma(String dilemma, String choice) implements Action {
	}

	/**
	 * Pays a Technology's Research now and unlocks it, once its prerequisites
	 * are unlocked.
	 */
	record UnlockTechnology(String technology) implements Action {
	}

}
