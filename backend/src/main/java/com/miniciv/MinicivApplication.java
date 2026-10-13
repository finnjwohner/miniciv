package com.miniciv;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Random;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MinicivApplication {

	public static void main(String[] args) {
		SpringApplication.run(MinicivApplication.class, args);
	}

	/**
	 * The server's time, injected so tests can control it.
	 */
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	/**
	 * The luck each new Game draws on, seeded afresh, injected so tests can
	 * control it.
	 */
	@Bean
	Supplier<RandomGenerator> luck() {
		SecureRandom seeds = new SecureRandom();
		return () -> new Random(seeds.nextLong());
	}

}
