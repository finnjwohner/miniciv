package com.miniciv.limits;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Refuses to upgrade a request to a {@code /ws} session while as many sessions
 * are open as the limits allow, overall or from its client IP. Each session
 * holds a slot from its handshake until it ends.
 */
@Component
public class SessionLimits implements HandshakeInterceptor {

	/**
	 * Where a handshake's slot is kept: in the request, until the handshake is
	 * done, and in the session's attributes after.
	 */
	private static final String SLOT = SessionLimits.class.getName() + ".slot";

	/**
	 * One open session, or one being opened, from a client IP; compared by
	 * identity, so that it is only ever freed once.
	 */
	private static final class Slot {

		private final String ip;

		private Slot(String ip) {
			this.ip = ip;
		}

	}

	private final Limits limits;

	/**
	 * The slots held, by client IP; an IP holding none is not kept.
	 */
	private final Map<String, Integer> held = new HashMap<>();

	private final Set<Slot> open = Collections.newSetFromMap(new IdentityHashMap<>());

	SessionLimits(Limits limits) {
		this.limits = limits;
	}

	@Override
	public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
			Map<String, Object> attributes) {
		HttpServletRequest servlet = ((ServletServerHttpRequest) request).getServletRequest();
		String ip = servlet.getRemoteAddr();
		Slot slot;
		synchronized (this) {
			if (open.size() >= limits.sessions()) {
				response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
				return false;
			}
			if (held.getOrDefault(ip, 0) >= limits.sessionsPerIp()) {
				response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
				return false;
			}
			slot = new Slot(ip);
			open.add(slot);
			held.merge(ip, 1, Integer::sum);
		}
		servlet.setAttribute(SLOT, slot);
		attributes.put(SLOT, slot);
		return true;
	}

	/**
	 * Frees the slot of a handshake that did not open a session, such as one
	 * refused by a later interceptor.
	 */
	@Override
	public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
			Exception exception) {
		Object slot = ((ServletServerHttpRequest) request).getServletRequest().getAttribute(SLOT);
		int status = ((ServletServerHttpResponse) response).getServletResponse().getStatus();
		if (slot != null && (exception != null || status != HttpStatus.SWITCHING_PROTOCOLS.value())) {
			free((Slot) slot);
		}
	}

	/**
	 * Frees the slot of a session that ended; may be told more than once.
	 */
	@EventListener
	void disconnected(SessionDisconnectEvent event) {
		Map<String, Object> attributes = SimpMessageHeaderAccessor.getSessionAttributes(event.getMessage().getHeaders());
		if (attributes != null && attributes.get(SLOT) instanceof Slot slot) {
			free(slot);
		}
	}

	private synchronized void free(Slot slot) {
		if (open.remove(slot)) {
			held.computeIfPresent(slot.ip, (ip, count) -> count == 1 ? null : count - 1);
		}
	}

}
