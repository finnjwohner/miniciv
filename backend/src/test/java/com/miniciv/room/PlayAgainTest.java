package com.miniciv.room;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Players returning to their Room after the Game ends, driven through the
 * Rooms the server's handlers call.
 */
@SpringBootTest
class PlayAgainTest {

	@Autowired
	Rooms rooms;

	String code;

	Room.Joined rome;

	Room.Joined carthage;

	@BeforeEach
	void endAGameBetweenRomeAndCarthage() {
		code = rooms.create().code();
		rome = rooms.join(code, "Rome");
		carthage = rooms.join(code, "Carthage");
		rooms.identify(code, rome.token(), "Caesar", "#5b8def", "sun");
		rooms.identify(code, carthage.token(), "Hannibal", "#e2574c", "moon");
		rooms.ready(code, rome.token(), true);
		rooms.ready(code, carthage.token(), true);
		rooms.message(code, rome.token(), "Carthage", "Peace?");
		rooms.leave(code, rome.token());
		assertThat(view().game().winner()).isEqualTo("Carthage");
	}

	Room.View view() {
		return rooms.find(code).orElseThrow().view();
	}

	@Test
	void theFirstPlayerToPressItReopensTheRoomWithTheirNationKeptAndNotReady() {
		assertThat(rooms.playAgain(code, carthage.token()).accepted()).isTrue();

		Room.View view = view();
		assertThat(view.game()).isNull();
		assertThat(view.players()).singleElement().satisfies(p -> {
			assertThat(p.nationName()).isEqualTo("Carthage");
			assertThat(p.ruler()).isEqualTo("Hannibal");
			assertThat(p.colour()).isEqualTo("#e2574c");
			assertThat(p.emblem()).isEqualTo("moon");
			assertThat(p.ready()).isFalse();
		});
	}

	@Test
	void playersWhoHaveNotPressedItAreNotSeatedAndStillSeeTheEndedGame() {
		rooms.playAgain(code, carthage.token());

		Room.View view = view();
		assertThat(view.previous().game().winner()).isEqualTo("Carthage");
		assertThat(view.previous().players()).extracting(Room.PlayerView::nationName).containsExactly("Rome");
		assertThat(rooms.find(code).orElseThrow().messagesOf(rome.playerId()).messages()).hasSize(1);
		assertThat(rooms.ready(code, rome.token(), true).accepted()).isFalse();
	}

	@Test
	void newPlayersCanJoinTheReopenedRoom() {
		rooms.playAgain(code, carthage.token());

		rooms.join(code, "Egypt");

		assertThat(view().players()).extracting(Room.PlayerView::nationName).containsExactly("Carthage", "Egypt");
	}

	@Test
	void aNewPlayerCannotTakeTheNameOfOneYetToPressIt() {
		rooms.playAgain(code, carthage.token());

		assertThatExceptionOfType(JoinRefused.class).isThrownBy(() -> rooms.join(code, "Rome"));
	}

	@Test
	void anotherGameStartsWithTheReadySeatedPlayersWithoutWaitingForThoseWhoHaveNotPressedIt() {
		rooms.playAgain(code, carthage.token());
		Room.Joined egypt = rooms.join(code, "Egypt");
		rooms.identify(code, egypt.token(), "Cleopatra", "#7bc96f", "star");
		rooms.ready(code, carthage.token(), true);
		rooms.ready(code, egypt.token(), true);

		Room.View view = view();
		assertThat(view.game().turn()).isEqualTo(1);
		assertThat(view.game().nations()).containsExactly("Carthage", "Egypt");
		assertThat(view.game().winner()).isNull();
		assertThat(view.previous()).isNull();
	}

	@Test
	void theOldGameIsDiscardedOnceEveryPlayerHasPressedItOrLeft() {
		rooms.playAgain(code, carthage.token());
		assertThat(view().previous()).isNotNull();

		rooms.leave(code, rome.token());

		assertThat(view().previous()).isNull();
	}

	@Test
	void aPlayerWhoPressesItLaterJoinsTheOthersWithTheirNationKept() {
		rooms.playAgain(code, carthage.token());

		assertThat(rooms.playAgain(code, rome.token()).accepted()).isTrue();

		Room.View view = view();
		assertThat(view.previous()).isNull();
		assertThat(view.players()).extracting(Room.PlayerView::nationName).containsExactly("Carthage", "Rome");
		assertThat(view.players().getLast().ruler()).isEqualTo("Caesar");
	}

	@Test
	void aPlayerComingBackFindsAFreeColourIfAnotherPlayerTookTheirs() {
		rooms.playAgain(code, carthage.token());
		Room.Joined egypt = rooms.join(code, "Egypt");
		rooms.identify(code, egypt.token(), "Cleopatra", "#5b8def", "star");

		rooms.playAgain(code, rome.token());

		assertThat(view().players().getLast().colour()).isNotEqualTo("#5b8def");
		assertThat(view().players().getLast().emblem()).isEqualTo("sun");
	}

	@Test
	void theOldGamesMessagesAreGoneInTheNewOne() {
		rooms.playAgain(code, carthage.token());

		assertThat(rooms.find(code).orElseThrow().messagesOf(carthage.playerId()).messages()).isEmpty();
	}

	@Test
	void itCannotBePressedWhileTheGameGoesOn() {
		String other = rooms.create().code();
		Room.Joined a = rooms.join(other, "Rome");
		Room.Joined b = rooms.join(other, "Carthage");
		rooms.identify(other, a.token(), "Caesar", "#5b8def", "sun");
		rooms.identify(other, b.token(), "Hannibal", "#e2574c", "moon");
		rooms.ready(other, a.token(), true);
		rooms.ready(other, b.token(), true);

		assertThat(rooms.playAgain(other, a.token()).accepted()).isFalse();
	}

}
