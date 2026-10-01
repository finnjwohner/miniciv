package com.miniciv;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
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

}
