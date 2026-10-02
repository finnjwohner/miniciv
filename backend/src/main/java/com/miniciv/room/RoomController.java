package com.miniciv.room;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.miniciv.engine.Action;
import com.miniciv.engine.ActionResult;
import com.miniciv.engine.NationView;

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

	record DoneRequest(String token, int turn) {
	}

	record StartBuildingRequest(String token, String building) {
	}

	record RepairBuildingRequest(String token, String building) {
	}

	record RebuildBuildingRequest(String token, String building) {
	}

	record TrainUnitsRequest(String token, String unit, int count) {
	}

	record SendWarpartyRequest(String token, String target, Map<String, Integer> units) {
	}

	record AnswerDilemmaRequest(String token, String dilemma, String choice) {
	}

	/**
	 * Why an Action was not accepted, for the Player who sent it.
	 */
	record Rejection(String reason) {
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

	/**
	 * Sends a Player their own Nation's view as it is now, once the Game has
	 * started; later views arrive on {@code /user/queue/game}.
	 */
	@SubscribeMapping("/rooms/{code}/nation")
	NationView nation(@DestinationVariable String code, Principal player) {
		if (player == null) {
			throw new IllegalArgumentException("Connect with a Player token to see a Nation");
		}
		return rooms.find(code).orElseThrow().nationViewOf(player.getName());
	}

	@MessageMapping("/rooms/{code}/ready")
	void ready(@DestinationVariable String code, ReadyRequest request) {
		rooms.ready(code, request.token(), request.ready());
	}

	/**
	 * Starts constructing a Building; the Player's changed view arrives on
	 * {@code /user/queue/game}, or the reason it was rejected on
	 * {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/buildings")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection startBuilding(@DestinationVariable String code, StartBuildingRequest request) {
		ActionResult result = rooms.act(code, request.token(), new Action.StartBuilding(request.building()));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Starts repairing a defensive Building type's damaged Building; the
	 * Player's changed view arrives on {@code /user/queue/game}, or the reason
	 * it was rejected on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/repairs")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection repairBuilding(@DestinationVariable String code, RepairBuildingRequest request) {
		ActionResult result = rooms.act(code, request.token(), new Action.RepairBuilding(request.building()));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Starts rebuilding one of a defensive Building type's destroyed Buildings;
	 * the Player's changed view arrives on {@code /user/queue/game}, or the
	 * reason it was rejected on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/rebuilds")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection rebuildBuilding(@DestinationVariable String code, RebuildBuildingRequest request) {
		ActionResult result = rooms.act(code, request.token(), new Action.RebuildBuilding(request.building()));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Starts training a number of Units of one type; the Player's changed view
	 * arrives on {@code /user/queue/game}, or the reason it was rejected on
	 * {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/units")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection trainUnits(@DestinationVariable String code, TrainUnitsRequest request) {
		ActionResult result = rooms.act(code, request.token(), new Action.TrainUnits(request.unit(), request.count()));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Sends Units from the Army to another Nation as a Warparty; the Player's
	 * changed view arrives on {@code /user/queue/game}, or the reason it was
	 * rejected on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/warparties")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection sendWarparty(@DestinationVariable String code, SendWarpartyRequest request) {
		Map<String, Integer> units = request.units() == null ? Map.of() : request.units();
		ActionResult result = rooms.act(code, request.token(),
				new Action.SendWarparty(String.valueOf(request.target()), units));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Makes a choice for one of the Nation's Dilemmas, applying it at once; the
	 * Player's changed view arrives on {@code /user/queue/game}, or the reason
	 * it was rejected on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/dilemmas")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection answerDilemma(@DestinationVariable String code, AnswerDilemmaRequest request) {
		ActionResult result = rooms.act(code, request.token(),
				new Action.AnswerDilemma(String.valueOf(request.dilemma()), String.valueOf(request.choice())));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	@MessageMapping("/rooms/{code}/done")
	void done(@DestinationVariable String code, DoneRequest request) {
		rooms.done(code, request.token(), request.turn());
	}

}
