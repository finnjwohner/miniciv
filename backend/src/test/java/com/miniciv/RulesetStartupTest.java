package com.miniciv;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.miniciv.engine.InvalidRuleset;
import com.miniciv.engine.Ruleset;

import static org.assertj.core.api.Assertions.assertThat;

class RulesetStartupTest {

	ApplicationContextRunner context = new ApplicationContextRunner().withUserConfiguration(RulesetConfig.class);

	@Test
	void theServerStartsWithTheRealContent() {
		context.run(started -> assertThat(started).hasSingleBean(Ruleset.class));
	}

	@Test
	void invalidContentStopsTheServerFromStartingWithAClearError() {
		context.withPropertyValues("miniciv.ruleset=rulesets/unknown-counter")
			.run(started -> assertThat(started).getFailure()
				.rootCause()
				.isInstanceOf(InvalidRuleset.class)
				.hasMessageContaining("Unit type Warrior counters Lancer, which is not a Unit type"));
	}

}
