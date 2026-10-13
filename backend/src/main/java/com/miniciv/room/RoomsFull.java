package com.miniciv.room;

/**
 * As many Rooms are live as the server allows, so no more can be created for
 * now; the message is meant for the Player.
 */
public class RoomsFull extends RuntimeException {

	RoomsFull() {
		super("miniciv is full right now, try again shortly");
	}

}
