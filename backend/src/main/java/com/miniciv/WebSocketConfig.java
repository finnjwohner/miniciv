package com.miniciv;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.miniciv.room.PlayerIdentification;

/**
 * Browsers connect over STOMP at {@code /ws}. They send to {@code /app/...},
 * receive Room-wide broadcasts from {@code /topic/...} and their Player's
 * private messages from {@code /user/queue/...}.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final PlayerIdentification playerIdentification;

	WebSocketConfig(PlayerIdentification playerIdentification) {
		this.playerIdentification = playerIdentification;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws");
		// Handle each browser's frames in the order sent, e.g. a subscribe before a ready.
		registry.setPreserveReceiveOrder(true);
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(playerIdentification);
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic", "/queue");
		registry.setApplicationDestinationPrefixes("/app");
		// Room updates must reach each browser in the order they happened.
		registry.setPreservePublishOrder(true);
	}

}
