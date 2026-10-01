package com.miniciv.room;

import java.security.Principal;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Identifies a browser's STOMP connection as a Player by the token it sends
 * when connecting, so the server can send that Player private messages on
 * {@code /user/queue/...}. A connection without a token can still follow Rooms.
 */
@Component
public class PlayerIdentification implements ChannelInterceptor {

	private final ObjectProvider<Rooms> rooms;

	PlayerIdentification(ObjectProvider<Rooms> rooms) {
		this.rooms = rooms;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
		if (stomp == null) {
			return message;
		}
		if (StompCommand.CONNECT.equals(stomp.getCommand())) {
			String token = stomp.getFirstNativeHeader("token");
			if (token != null) {
				String playerId = rooms.getObject()
					.playerIdFor(token)
					.orElseThrow(() -> new MessageDeliveryException("No Player has that token"));
				Principal player = () -> playerId;
				stomp.setUser(player);
			}
		}
		return message;
	}

}
