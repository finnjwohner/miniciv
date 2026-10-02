package com.miniciv.engine;

import java.util.List;

/**
 * A Dilemma that happened to a Nation, which its Player may answer until the
 * Turn ends; the choice made, or else the worst, applies then.
 *
 * @param name the Event's name, which the Player answers it by
 * @param text how it was announced
 * @param choices what the Player may choose, in the order listed
 * @param chosen the name of the choice the Player made, or null while they
 *        have not chosen
 */
public record Dilemma(String name, String text, List<Event.Choice> choices, String chosen) {

	public Dilemma {
		choices = List.copyOf(choices);
	}

}
