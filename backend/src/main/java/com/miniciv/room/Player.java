package com.miniciv.room;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A person taking part in a Room, and later its Game. The token is secret to
 * the Player's browser; the id is public.
 */
class Player {

	private final String id = UUID.randomUUID().toString();

	private final String token = UUID.randomUUID().toString();

	private final String nationName;

	/**
	 * The name of the Nation's Ruler; empty until the Player names them.
	 */
	private String ruler = "";

	private String colour;

	private String emblem;

	private boolean ready;

	private boolean done;

	/**
	 * The ids of the Player's open connections.
	 */
	private final Set<String> connections = new HashSet<>();

	/**
	 * How long the Player was disconnected from the Game before their current
	 * disconnection, if any.
	 */
	private Duration disconnectedBefore = Duration.ZERO;

	/**
	 * When the Player's current disconnection from the Game began, or null if
	 * none is being counted.
	 */
	private Instant disconnectedSince;

	Player(String nationName, String colour, String emblem) {
		this.nationName = nationName;
		this.colour = colour;
		this.emblem = emblem;
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

	String ruler() {
		return ruler;
	}

	String colour() {
		return colour;
	}

	String emblem() {
		return emblem;
	}

	/**
	 * Names the Nation's Ruler and sets its colour and emblem.
	 */
	void identify(String ruler, String colour, String emblem) {
		this.ruler = ruler;
		this.colour = colour;
		this.emblem = emblem;
	}

	boolean ready() {
		return ready;
	}

	void ready(boolean ready) {
		this.ready = ready;
	}

	/**
	 * Whether the Player has finished their decisions for the current Turn.
	 */
	boolean done() {
		return done;
	}

	void done(boolean done) {
		this.done = done;
	}

	/**
	 * Records one of the Player's connections, by id, opening.
	 */
	void connected(String connection) {
		connections.add(connection);
	}

	/**
	 * Records one of the Player's connections, by id, closing.
	 */
	void disconnected(String connection) {
		connections.remove(connection);
	}

	/**
	 * Whether the Player has any connection open.
	 */
	boolean connected() {
		return !connections.isEmpty();
	}

	/**
	 * Starts or stops counting the Player's disconnection from the Game.
	 */
	void countDisconnection(boolean counting, Instant now) {
		if (counting && disconnectedSince == null) {
			disconnectedSince = now;
		}
		else if (!counting && disconnectedSince != null) {
			disconnectedBefore = disconnectedBefore.plus(Duration.between(disconnectedSince, now));
			disconnectedSince = null;
		}
	}

	/**
	 * How long the Player has been disconnected from the Game in total.
	 */
	Duration disconnectedFor(Instant now) {
		return disconnectedSince == null ? disconnectedBefore
				: disconnectedBefore.plus(Duration.between(disconnectedSince, now));
	}

}
