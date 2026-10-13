package com.miniciv.limits;

import java.time.Clock;
import java.time.Duration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * How often each client IP may create and join Rooms.
 */
@Component
public class RequestRates {

	private final TokenBuckets roomCreations;

	private final TokenBuckets joins;

	RequestRates(Limits limits, Clock clock) {
		this.roomCreations = new TokenBuckets(clock, limits.roomCreationsPerMinute(), Duration.ofMinutes(1));
		this.joins = new TokenBuckets(clock, limits.joinsPerMinute(), Duration.ofMinutes(1));
	}

	public void createRoom(String ip) {
		if (!roomCreations.tryTake(ip)) {
			throw new TooManyRequests("You've created several Rooms just now. Wait a minute and try again.");
		}
	}

	/**
	 * Counts any attempt to join, so that no one can guess room codes by brute
	 * force.
	 */
	public void join(String ip) {
		if (!joins.tryTake(ip)) {
			throw new TooManyRequests("You've tried to join several Rooms just now. Wait a minute and try again.");
		}
	}

	/**
	 * Forgets the client IPs that have not created or joined a Room for a
	 * while.
	 */
	@Scheduled(fixedRate = 60_000)
	public void forgetIdle() {
		roomCreations.forgetIdle();
		joins.forgetIdle();
	}

}
