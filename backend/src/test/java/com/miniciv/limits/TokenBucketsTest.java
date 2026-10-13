package com.miniciv.limits;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketsTest {

	Instant now = Instant.parse("2026-01-01T00:00:00Z");

	TokenBuckets buckets = new TokenBuckets(() -> now, 3, Duration.ofMinutes(1));

	void advance(Duration duration) {
		now = now.plus(duration);
	}

	@Test
	void aKeyMayTakeAsManyAsTheCapacityAtOnceThenNoMore() {
		assertThat(buckets.tryTake("a")).isTrue();
		assertThat(buckets.tryTake("a")).isTrue();
		assertThat(buckets.tryTake("a")).isTrue();

		assertThat(buckets.tryTake("a")).isFalse();
	}

	@Test
	void eachKeyHasItsOwnBucket() {
		for (int i = 0; i < 3; i++) {
			buckets.tryTake("a");
		}

		assertThat(buckets.tryTake("b")).isTrue();
	}

	@Test
	void anEmptiedBucketRefillsGraduallyOverThePeriod() {
		for (int i = 0; i < 3; i++) {
			buckets.tryTake("a");
		}

		advance(Duration.ofSeconds(19));
		assertThat(buckets.tryTake("a")).isFalse();
		advance(Duration.ofSeconds(1));
		assertThat(buckets.tryTake("a")).isTrue();
		assertThat(buckets.tryTake("a")).isFalse();
	}

	@Test
	void aBucketNeverFillsBeyondItsCapacity() {
		buckets.tryTake("a");
		advance(Duration.ofHours(1));

		for (int i = 0; i < 3; i++) {
			assertThat(buckets.tryTake("a")).isTrue();
		}
		assertThat(buckets.tryTake("a")).isFalse();
	}

	@Test
	void aBucketFullAndUnusedForFiveMinutesIsForgotten() {
		buckets.tryTake("a");
		buckets.tryTake("b");
		buckets.tryTake("b");
		buckets.tryTake("b");
		// "a" is full again after 20 s, "b" after 60 s.
		advance(Duration.ofSeconds(20).plus(Duration.ofMinutes(5)));

		buckets.forgetIdle();

		assertThat(buckets.size()).isEqualTo(1);
		advance(Duration.ofSeconds(40));
		buckets.forgetIdle();
		assertThat(buckets.size()).isZero();
	}

	@Test
	void aForgottenKeyStartsAgainWithAFullBucket() {
		for (int i = 0; i < 3; i++) {
			buckets.tryTake("a");
		}

		buckets.forget("a");

		assertThat(buckets.size()).isZero();
		assertThat(buckets.tryTake("a")).isTrue();
	}

}
