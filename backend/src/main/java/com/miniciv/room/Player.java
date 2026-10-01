package com.miniciv.room;

import java.util.UUID;

/**
 * A person taking part in a Room, and later its Game. The token is secret to
 * the Player's browser; the id is public.
 */
class Player {

	private final String id = UUID.randomUUID().toString();

	private final String token = UUID.randomUUID().toString();

	private final String nationName;

	private boolean ready;

	Player(String nationName) {
		this.nationName = nationName;
	}

	String id() {
		return id;
	}

	String token() {
		return token;
	}

	String nationName() {
		return nationName;
	}

	boolean ready() {
		return ready;
	}

	void ready(boolean ready) {
		this.ready = ready;
	}

}
