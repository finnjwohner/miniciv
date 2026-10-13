package com.miniciv.limits;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One token bucket per key, such as a client IP: each holds up to a capacity
 * of tokens, starts full, refills gradually from empty to full over a period,
 * and a request is allowed only if it can take a token. A bucket full and
 * unused for {@link #IDLE} is forgotten by {@link #forgetIdle()}, since a new
 * one would start full anyway, so the buckets cannot grow without bound.
 */
public class TokenBuckets {

	static final Duration IDLE = Duration.ofMinutes(5);

	/**
	 * The tokens a bucket held at a moment.
	 */
	private record Bucket(double tokens, Instant at) {
	}

	private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

	private final InstantSource clock;

	private final int capacity;

	private final Duration period;

	public TokenBuckets(InstantSource clock, int capacity, Duration period) {
		this.clock = clock;
		this.capacity = capacity;
		this.period = period;
	}

	/**
	 * Takes a token from the key's bucket, returning whether there was one.
	 */
	public boolean tryTake(String key) {
		AtomicBoolean taken = new AtomicBoolean();
		buckets.compute(key, (k, bucket) -> {
			Instant now = clock.instant();
			double tokens = bucket == null ? capacity : tokensAt(bucket, now);
			if (tokens >= 1) {
				taken.set(true);
				tokens--;
			}
			return new Bucket(tokens, now);
		});
		return taken.get();
	}

	public void forget(String key) {
		buckets.remove(key);
	}

	/**
	 * Forgets every bucket that has been full for {@link #IDLE}.
	 */
	public void forgetIdle() {
		Instant forgettable = clock.instant().minus(IDLE);
		buckets.values().removeIf(bucket -> !fullAt(bucket).isAfter(forgettable));
	}

	int size() {
		return buckets.size();
	}

	private double tokensAt(Bucket bucket, Instant now) {
		double refilled = (double) Duration.between(bucket.at(), now).toNanos() / period.toNanos() * capacity;
		return Math.min(capacity, bucket.tokens() + refilled);
	}

	private Instant fullAt(Bucket bucket) {
		return bucket.at().plusNanos((long) ((capacity - bucket.tokens()) / capacity * period.toNanos()));
	}

}
