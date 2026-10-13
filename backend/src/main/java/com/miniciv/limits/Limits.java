package com.miniciv.limits;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The abuse limits, set by the {@code miniciv.limits.*} keys; see
 * {@code docs/hosting.md}.
 *
 * @param rooms                  how many Rooms may be live at once
 * @param roomCreationsPerMinute how many Rooms one client IP may create a minute
 * @param joinsPerMinute         how many times one client IP may try to join a Room a minute
 * @param sessions               how many {@code /ws} sessions may be open at once
 * @param sessionsPerIp          how many {@code /ws} sessions one client IP may have open at once
 * @param framesPerSecond        how many STOMP frames one session may send a second
 * @param messagesPerGame        how many Messages a Player may send in a Game
 * @param nationNameLength       how many characters a Nation's name may have
 */
@ConfigurationProperties("miniciv.limits")
public record Limits(int rooms, int roomCreationsPerMinute, int joinsPerMinute, int sessions, int sessionsPerIp,
		int framesPerSecond, int messagesPerGame, int nationNameLength) {
}
