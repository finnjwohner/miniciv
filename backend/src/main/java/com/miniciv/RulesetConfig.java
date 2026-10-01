package com.miniciv;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.miniciv.engine.Ruleset;
import com.miniciv.engine.RulesetFiles;

/**
 * Loads the game content once, at startup. Content that does not make sense
 * stops the server from starting, with a message saying what is wrong.
 */
@Configuration
class RulesetConfig {

	@Bean
	Ruleset ruleset(@Value("${miniciv.ruleset:ruleset}") String directory) {
		return RulesetFiles.load(directory);
	}

}
