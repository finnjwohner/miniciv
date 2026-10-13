package com.miniciv.limits;

/**
 * A client IP has made a kind of request too often lately; the message is
 * meant for the Player.
 */
public class TooManyRequests extends RuntimeException {

	TooManyRequests(String message) {
		super(message);
	}

}
