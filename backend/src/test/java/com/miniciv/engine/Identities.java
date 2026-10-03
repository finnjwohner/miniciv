package com.miniciv.engine;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Nations for tests, each with its own Ruler, colour and emblem.
 */
final class Identities {

	private static final Map<String, NationIdentity> KNOWN = Map.of(
			"Rome", new NationIdentity("Rome", "Caesar", "#e2574c", "crown"),
			"Carthage", new NationIdentity("Carthage", "Hannibal", "#5b8def", "moon"),
			"Egypt", new NationIdentity("Egypt", "Cleopatra", "#f2c14e", "sun"));

	private Identities() {
	}

	/**
	 * The Nation of the given name, with a Ruler, colour and emblem of its own.
	 */
	static NationIdentity of(String name) {
		return KNOWN.getOrDefault(name, new NationIdentity(name, "Ruler", "#7bc96f", "star"));
	}

	static List<NationIdentity> of(String... names) {
		return Arrays.stream(names).map(Identities::of).toList();
	}

}
