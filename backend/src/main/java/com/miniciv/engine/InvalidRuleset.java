package com.miniciv.engine;

/**
 * The game content does not make sense; the message lists every problem found.
 */
public class InvalidRuleset extends RuntimeException {

	InvalidRuleset(String message) {
		super(message);
	}

	InvalidRuleset(String message, Throwable cause) {
		super(message, cause);
	}

}
