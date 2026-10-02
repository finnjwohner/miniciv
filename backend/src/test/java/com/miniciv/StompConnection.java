package com.miniciv;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * A browser's STOMP connection to the server, as seen by a test.
 */
class StompConnection implements AutoCloseable {

	private final StompSession session;

	StompConnection(int port) throws Exception {
		this(port, new StompHeaders());
	}

	/**
	 * Connects as the Player holding the token, as a browser in a Room does.
	 */
	StompConnection(int port, String token) throws Exception {
		this(port, playerHeaders(token));
	}

	private StompConnection(int port, StompHeaders connectHeaders) throws Exception {
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		client.setMessageConverter(new JacksonJsonMessageConverter());
		this.session = client
			.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
					new StompSessionHandlerAdapter() {
					})
			.get(5, TimeUnit.SECONDS);
	}

	private static StompHeaders playerHeaders(String token) {
		StompHeaders headers = new StompHeaders();
		headers.add("token", token);
		return headers;
	}

	/**
	 * Subscribes to a destination; each message received lands in the returned queue.
	 */
	Messages subscribe(String destination) {
		Messages messages = new Messages();
		session.subscribe(destination, new StompFrameHandler() {

			@Override
			public Type getPayloadType(StompHeaders headers) {
				return Map.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				messages.queue.add((Map<?, ?>) payload);
			}

		});
		return messages;
	}

	void send(String destination, Object payload) {
		session.send(destination, payload);
	}

	/**
	 * Disconnects, unless already disconnected.
	 */
	@Override
	public void close() {
		if (session.isConnected()) {
			session.disconnect();
		}
	}

	static class Messages {

		private final BlockingQueue<Map<?, ?>> queue = new LinkedBlockingQueue<>();

		/**
		 * Waits for the next message, failing if none arrives within a few seconds.
		 */
		Map<?, ?> next() throws InterruptedException {
			Map<?, ?> message = queue.poll(5, TimeUnit.SECONDS);
			if (message == null) {
				throw new AssertionError("No message received");
			}
			return message;
		}

		/**
		 * Fails if a message arrives within a short while.
		 */
		void expectNone() throws InterruptedException {
			Map<?, ?> message = queue.poll(500, TimeUnit.MILLISECONDS);
			if (message != null) {
				throw new AssertionError("Unexpected message received: " + message);
			}
		}

	}

}
