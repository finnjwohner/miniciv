package com.miniciv.room;

/**
 * A Player could not join a Room; the message is meant for that Player.
 */
public class JoinRefused extends RuntimeException {

	public enum Reason {

		NO_SUCH_ROOM, ROOM_FULL, GAME_STARTED, NO_NATION_NAME, NATION_NAME_TOO_LONG, NATION_NAME_TAKEN

	}

	private final Reason reason;

	JoinRefused(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

}
