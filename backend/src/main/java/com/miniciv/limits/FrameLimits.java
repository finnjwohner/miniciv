package com.miniciv.limits;

import java.time.Clock;
import java.time.Duration;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Silently drops the STOMP frames a {@code /ws} session sends beyond its rate.
 * A session's bucket starts full, so its first frame, connecting, always gets
 * through; disconnecting is never dropped, so a session is always cleaned up.
 */
@Component
public class FrameLimits implements ChannelInterceptor {

	private final TokenBuckets sessions;

	FrameLimits(Limits limits, Clock clock) {
		this.sessions = new TokenBuckets(clock, limits.framesPerSecond(), Duration.ofSeconds(1));
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		String session = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
		SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
		if (session == null || type == null) {
			return message;
		}
		return switch (type) {
			case DISCONNECT -> {
				sessions.forget(session);
				yield message;
			}
			default -> sessions.tryTake(session) ? message : null;
		};
	}

}
