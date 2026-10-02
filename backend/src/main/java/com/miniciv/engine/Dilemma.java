package com.miniciv.engine;

import java.util.List;

/**
 * A Dilemma that happened to a Nation and awaits its Player's choice until
 * the Turn ends.
 *
 * @param name the Event's name, which the Player answers it by
 * @param text how it was announced
 * @param choices what the Player may choose, in the order listed
 */
public record Dilemma(String name, String text, List<Event.Choice> choices) {

	public Dilemma {
		choices = List.copyOf(choices);
	}

}
