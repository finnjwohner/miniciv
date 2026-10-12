package com.miniciv.room;

import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Room codes: three words joined by hyphens, such as {@code brave-otter-lamp},
 * easy to say over voice chat. The words are short, common and easy to spell,
 * with no homophones among them.
 */
final class RoomCodes {

	private static final List<String> WORDS = List.of("able", "acorn", "amber", "apple", "arch", "atlas", "badge",
			"baker", "barn", "basil", "beach", "bean", "bear", "bell", "birch", "blade", "boat", "bold", "bone",
			"book", "brave", "bread", "brick", "bridge", "brook", "cabin", "cake", "calm", "camp", "candle",
			"canoe", "cedar", "chair", "chalk", "cherry", "chill", "cider", "clay", "cliff", "cloud", "clover",
			"coast", "coin", "copper", "coral", "cork", "crab", "crane", "crisp", "crown", "daisy", "dawn", "deer",
			"denim", "desk", "dove", "dragon", "drum", "dune", "eagle", "ember", "fable", "falcon", "farm",
			"fern", "field", "finch", "fish", "flame", "flint", "forest", "fox", "frost", "garden", "ghost",
			"giant", "ginger", "glass", "globe", "goat", "gold", "grape", "grass", "green", "grove", "gull",
			"hammer", "harbor", "hawk", "hazel", "heron", "hill", "honey", "horse", "house", "iris", "iron",
			"island", "ivory", "jade", "jelly", "jolly", "kettle", "kind", "kite", "knight", "lake", "lamp",
			"lantern", "lemon", "light", "lilac", "lion", "lucky", "maple", "marble", "meadow", "melon", "mint",
			"moon", "moss", "mouse", "mule", "nest", "noble", "north", "oak", "ocean", "olive", "onion", "orange",
			"otter", "owl", "paint", "panda", "pear", "pebble", "pepper", "pine", "plum", "pond", "pony", "quail",
			"quick", "quiet", "rabbit", "raven", "reed", "river", "robin", "rocket", "rose", "ruby", "sage",
			"salmon", "sandy", "sheep", "shell", "silk", "silver", "sky", "snow", "spark", "spoon", "squid",
			"star", "stone", "storm", "sugar", "swan", "tiger", "toast", "tulip", "turtle", "velvet", "violet",
			"walnut", "whale", "willow", "wolf", "wren", "yarn", "zebra");

	private static final int WORDS_PER_CODE = 3;

	private RoomCodes() {
	}

	static String random(RandomGenerator random) {
		StringBuilder code = new StringBuilder();
		for (int i = 0; i < WORDS_PER_CODE; i++) {
			if (i > 0) {
				code.append('-');
			}
			code.append(WORDS.get(random.nextInt(WORDS.size())));
		}
		return code.toString();
	}

	/**
	 * What a typed code is compared by: its letters, lower-cased, so that case
	 * and the separators between words, if any, do not matter.
	 */
	static String key(String typed) {
		return typed.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
	}

}
