package com.miniciv.engine;

/**
 * Whether an Action was accepted, and if not, why, in words for the Player.
 */
public record ActionResult(boolean accepted, String reason) {

	public static final ActionResult ACCEPTED = new ActionResult(true, null);

	public static ActionResult rejected(String reason) {
		return new ActionResult(false, reason);
	}

}
