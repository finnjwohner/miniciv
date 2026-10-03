package com.miniciv.engine;

import java.util.List;

/**
 * A Dilemma that happened to a Nation, which its Player may answer until the
 * Turn ends; the choice made, or else the worst, applies then. The Player is
 * never shown what a choice may lead to, nor the odds.
 *
 * @param name the Event's name, which the Player answers it by
 * @param text how it was announced
 * @param choices what the Player may choose, in the order listed
 * @param chosen the name of the choice the Player made, or null while they
 *        have not chosen
 */
public record Dilemma(String name, String text, List<Choice> choices, String chosen) {

	public Dilemma {
		choices = List.copyOf(choices);
	}

	/**
	 * One of the choices a Dilemma offers, as its Player sees it.
	 *
	 * @param name what the Player picks it by
	 * @param worst whether it is the one that applies if the Player does not
	 *        choose before the Turn ends
	 */
	public record Choice(String name, boolean worst) {
	}

}
