package com.miniciv;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The abuse limits on {@code /ws} sessions, set low. Each connection comes from
 * the client IP it names in X-Forwarded-For, as it does through Caddy.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "server.forward-headers-strategy=native", "miniciv.limits.sessions=3",
				"miniciv.limits.sessions-per-ip=2" })
class SessionLimitsTest {

	@LocalServerPort
	int port;

	/**
	 * Every connection a test opens, closed after it.
	 */
	final List<StompConnection> connections = new ArrayList<>();

	@AfterEach
	void disconnect() {
		connections.forEach(StompConnection::close);
	}

	StompConnection connect(String ip) throws Exception {
		StompConnection stomp = StompConnection.fromIp(port, ip);
		connections.add(stomp);
		return stomp;
	}

	/**
	 * Waits until a connection from the client IP is let in again.
	 */
	void awaitConnecting(String ip) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (true) {
			try {
				connect(ip);
				return;
			}
			catch (Exception refused) {
				if (System.nanoTime() > deadline) {
					throw new AssertionError("A connection from " + ip + " was never let in", refused);
				}
				Thread.sleep(20);
			}
		}
	}

	@Test
	void onlySoManySessionsMayBeOpenPerIpAndOverallUntilOneCloses() throws Exception {
		StompConnection first = connect("10.0.0.1");
		connect("10.0.0.1");

		assertThatThrownBy(() -> connect("10.0.0.1")).as("a third session from one IP").isNotNull();
		assertThatCode(() -> connect("10.0.0.2")).doesNotThrowAnyException();
		assertThatThrownBy(() -> connect("10.0.0.3")).as("a fourth session overall").isNotNull();

		first.close();
		awaitConnecting("10.0.0.1");
		assertThatThrownBy(() -> connect("10.0.0.3")).as("a fourth session overall").isNotNull();
	}

}
