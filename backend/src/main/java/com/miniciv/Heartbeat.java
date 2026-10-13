package com.miniciv;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The STOMP heartbeat intervals, set by the {@code miniciv.heartbeat.*} keys;
 * see {@code docs/hosting.md}. The frontend's values in {@code useRoom.ts} must
 * match.
 *
 * @param serverMs how often the server sends each browser a heartbeat, in ms
 * @param clientMs how often the server expects a heartbeat from each browser, in ms
 */
@ConfigurationProperties("miniciv.heartbeat")
record Heartbeat(long serverMs, long clientMs) {
}
