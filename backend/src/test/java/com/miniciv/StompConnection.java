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
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * A browser's STOMP connection to the server, as seen by a test.
 */
class StompConnection implements AutoCloseable {

	private final StompSession session;

	StompConnection(int port) throws Exception {
		WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
		client.setMessageConverter(new JacksonJsonMessageConverter());
		this.session = client.connectAsync("ws://localhost:" + port + "/ws", new StompSessionHandlerAdapter() {
		}).get(5, TimeUnit.SECONDS);
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

	@Override
	public void close() {
		session.disconnect();
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

	}

}
