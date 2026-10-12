package com.miniciv.engine;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads a Ruleset from JSON data files on the classpath: {@code units.json},
 * {@code buildings.json}, {@code technologies.json}, {@code events.json} and
 * {@code missions.json} in one directory, and {@code barbarians.json} if
 * Barbarian Camps are to appear. A field left out of an entry counts as 0 or empty; a field the
 * engine does not know, such as a misspelling or a condition or effect outside
 * an Event's fixed set, is an error.
 */
public final class RulesetFiles {

	private static final JsonMapper JSON = JsonMapper.builder()
		.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
		.build();

	private RulesetFiles() {
	}

	/**
	 * Loads and checks the Ruleset in a classpath directory.
	 * @throws InvalidRuleset if a file is missing, cannot be read, or its
	 *         content does not make sense
	 */
	public static Ruleset load(String directory) {
		return new Ruleset(read(directory + "/units.json", new TypeReference<List<UnitType>>() {
		}), read(directory + "/buildings.json", new TypeReference<List<BuildingType>>() {
		}), read(directory + "/technologies.json", new TypeReference<List<Technology>>() {
		}), read(directory + "/events.json", new TypeReference<List<Event>>() {
		}), read(directory + "/missions.json", new TypeReference<List<Mission>>() {
		}), barbarians(directory + "/barbarians.json"));
	}

	/**
	 * What Barbarian Camps are made from, or none if the file is left out.
	 */
	private static Barbarians barbarians(String path) {
		try (InputStream in = RulesetFiles.class.getClassLoader().getResourceAsStream(path)) {
			return in == null ? Barbarians.NONE : JSON.readValue(in, Barbarians.class);
		}
		catch (JacksonException | IOException e) {
			throw new InvalidRuleset("Ruleset file " + path + " could not be read: " + e.getMessage(), e);
		}
	}

	private static <T> List<T> read(String path, TypeReference<List<T>> type) {
		try (InputStream in = RulesetFiles.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new InvalidRuleset("Ruleset file " + path + " is missing from the classpath.");
			}
			return JSON.readValue(in, type);
		}
		catch (JacksonException | IOException e) {
			throw new InvalidRuleset("Ruleset file " + path + " could not be read: " + e.getMessage(), e);
		}
	}

}
