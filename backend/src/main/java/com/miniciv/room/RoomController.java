package com.miniciv.room;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Rooms are created and joined over REST; Players then follow the Room over STOMP.
 */
@RestController
class RoomController {

	record CreatedRoom(String code, String inviteLink) {
	}

	record JoinRequest(String nationName) {
	}

	record ReadyRequest(String token, boolean ready) {
	}

	private final Rooms rooms;

	RoomController(Rooms rooms) {
		this.rooms = rooms;
	}

	@PostMapping("/api/rooms")
	@ResponseStatus(HttpStatus.CREATED)
	CreatedRoom create() {
		Room room = rooms.create();
		String inviteLink = ServletUriComponentsBuilder.fromCurrentContextPath()
			.path("/rooms/{code}")
			.buildAndExpand(room.code())
			.toUriString();
		return new CreatedRoom(room.code(), inviteLink);
	}

	@PostMapping("/api/rooms/{code}/players")
	@ResponseStatus(HttpStatus.CREATED)
	Room.Joined join(@PathVariable String code, @RequestBody JoinRequest request) {
		return rooms.join(code, request.nationName());
	}

	@ExceptionHandler
	ProblemDetail refused(JoinRefused refused) {
		HttpStatus status = switch (refused.reason()) {
			case NO_SUCH_ROOM -> HttpStatus.NOT_FOUND;
			case NO_NATION_NAME -> HttpStatus.BAD_REQUEST;
			case ROOM_FULL, GAME_STARTED, NATION_NAME_TAKEN -> HttpStatus.CONFLICT;
		};
		return ProblemDetail.forStatusAndDetail(status, refused.getMessage());
	}

	/**
	 * Sends a new subscriber the Room as it is now; later changes arrive on
	 * {@code /topic/rooms/{code}}.
	 */
	@SubscribeMapping("/rooms/{code}")
	Room.View current(@DestinationVariable String code) {
		return rooms.find(code).orElseThrow().view();
	}

	@MessageMapping("/rooms/{code}/ready")
	void ready(@DestinationVariable String code, ReadyRequest request) {
		rooms.ready(code, request.token(), request.ready());
	}

}
