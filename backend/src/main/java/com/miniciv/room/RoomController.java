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

	record IdentityRequest(String token, String ruler, String colour, String emblem) {
	}

	record DoneRequest(String token, int turn) {
	}

	record StartBuildingRequest(String token, String building) {
	}

	record RepairBuildingRequest(String token, String building) {
	}

	record RebuildBuildingRequest(String token, String building) {
	}

	record SellBuildingRequest(String token, String building) {
	}

	record TrainUnitsRequest(String token, String unit, int count) {
	}

	record SendWarpartyRequest(String token, String target, Map<String, Integer> units) {
	}

	record SendSpyRequest(String token, String target) {
	}

	record SetMissionRequest(String token, int spy, String mission) {
	}

	record SetTaskRequest(String token, String kind, String target, int scouts) {
	}

	record CancelTaskRequest(String token, int task) {
	}

	record RecallWarpartyRequest(String token, int warparty) {
	}

	record OfferTributeRequest(String token, String nation, int warparty, int gold, int population) {
	}

	record AnswerTributeRequest(String token, int warparty, boolean accept) {
	}

	record AnswerEncounterRequest(String token, int warparty, boolean fight) {
	}

	record AnswerDilemmaRequest(String token, String dilemma, String choice) {
	}

	record AnswerHaulRequest(String token, int haul, boolean keep) {
	}

	record UnlockTechnologyRequest(String token, String technology) {
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

	/**
	 * Marks the Player ready or unready; the changed Room arrives on
	 * {@code /topic/rooms/{code}}, or the reason it was rejected on
	 * {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/ready")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection ready(@DestinationVariable String code, ReadyRequest request) {
		ActionResult result = rooms.ready(code, request.token(), request.ready());
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Names the Player's Ruler and sets their Nation's colour and emblem; the
	 * changed Room arrives on {@code /topic/rooms/{code}}, or the reason it was
	 * rejected on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/identity")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection identify(@DestinationVariable String code, IdentityRequest request) {
		ActionResult result = rooms.identify(code, request.token(), request.ruler(), request.colour(),
				request.emblem());
		return result.accepted() ? null : new Rejection(result.reason());
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
	 * Sells one of a Building type's standing Buildings for Gold; the Player's
	 * changed view arrives on {@code /user/queue/game}, or the reason it was
	 * rejected on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/sales")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection sellBuilding(@DestinationVariable String code, SellBuildingRequest request) {
		ActionResult result = rooms.act(code, request.token(), new Action.SellBuilding(request.building()));
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
	 * Sends a Spy from the Army to another Nation; the Player's changed view
	 * arrives on {@code /user/queue/game}, or the reason it was rejected on
	 * {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/spies")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection sendSpy(@DestinationVariable String code, SendSpyRequest request) {
		ActionResult result = rooms.act(code, request.token(), new Action.SendSpy(String.valueOf(request.target())));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Sets one of the Nation's Spies in place to a Mission; the Player's
	 * changed view arrives on {@code /user/queue/game}, or the reason it was
	 * rejected on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/spies/missions")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection setMission(@DestinationVariable String code, SetMissionRequest request) {
		ActionResult result = rooms.act(code, request.token(),
				new Action.SetMission(request.spy(), String.valueOf(request.mission())));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Sets some of the Nation's Idle Scouts to a Task; the Player's changed
	 * view arrives on {@code /user/queue/game}, or the reason it was rejected
	 * on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/tasks")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection setTask(@DestinationVariable String code, SetTaskRequest request) {
		ActionResult result = rooms.act(code, request.token(), new Action.SetTask(String.valueOf(request.kind()),
				String.valueOf(request.target()), request.scouts()));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Ends one of the Nation's Tasks, its Scouts going Idle; the Player's
	 * changed view arrives on {@code /user/queue/game}, or the reason it was
	 * rejected on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/tasks/cancellations")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection cancelTask(@DestinationVariable String code, CancelTaskRequest request) {
		ActionResult result = rooms.act(code, request.token(), new Action.CancelTask(request.task()));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Recalls one of the Nation's Warparties, turning it back home at once; the
	 * Player's changed view arrives on {@code /user/queue/game}, or the reason
	 * it was rejected on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/recalls")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection recallWarparty(@DestinationVariable String code, RecallWarpartyRequest request) {
		ActionResult result = rooms.act(code, request.token(), new Action.RecallWarparty(request.warparty()));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Offers Tribute against another Nation's Warparty that arrived at the
	 * Nation; both Players' changed views arrive on {@code /user/queue/game},
	 * or the reason it was rejected on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/tributes")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection offerTribute(@DestinationVariable String code, OfferTributeRequest request) {
		ActionResult result = rooms.act(code, request.token(), new Action.OfferTribute(String.valueOf(request.nation()),
				request.warparty(), request.gold(), request.population()));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Accepts or refuses the Tribute offered for one of the Nation's
	 * Warparties; both Players' changed views arrive on
	 * {@code /user/queue/game}, or the reason it was rejected on
	 * {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/tributes/answers")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection answerTribute(@DestinationVariable String code, AnswerTributeRequest request) {
		ActionResult result = rooms.act(code, request.token(),
				new Action.AnswerTribute(request.warparty(), request.accept()));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Chooses whether one of the Nation's Warparties fights the other Nation's
	 * Warparty it met; both Players' changed views arrive on
	 * {@code /user/queue/game}, or the reason it was rejected on
	 * {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/encounters/answers")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection answerEncounter(@DestinationVariable String code, AnswerEncounterRequest request) {
		ActionResult result = rooms.act(code, request.token(),
				new Action.AnswerEncounter(request.warparty(), request.fight()));
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

	/**
	 * Chooses whether to keep a Haul seized from a caught Spy or send it back;
	 * the Player's changed view arrives on {@code /user/queue/game}, or the
	 * reason it was rejected on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/hauls")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection answerHaul(@DestinationVariable String code, AnswerHaulRequest request) {
		ActionResult result = rooms.act(code, request.token(), new Action.AnswerHaul(request.haul(), request.keep()));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	/**
	 * Unlocks a Technology, paying its Research at once; the Player's changed
	 * view arrives on {@code /user/queue/game}, or the reason it was rejected
	 * on {@code /user/queue/rejections}.
	 */
	@MessageMapping("/rooms/{code}/technologies")
	@SendToUser(destinations = "/queue/rejections", broadcast = false)
	Rejection unlockTechnology(@DestinationVariable String code, UnlockTechnologyRequest request) {
		ActionResult result = rooms.act(code, request.token(),
				new Action.UnlockTechnology(String.valueOf(request.technology())));
		return result.accepted() ? null : new Rejection(result.reason());
	}

	@MessageMapping("/rooms/{code}/done")
	void done(@DestinationVariable String code, DoneRequest request) {
		rooms.done(code, request.token(), request.turn());
	}

}
