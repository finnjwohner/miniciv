package com.miniciv.engine;

/**
 * The Gold or Research a Spy stole, which reaches its Nation only once the Spy
 * has carried it home; a Nation that catches the Spy on the way seizes it.
 *
 * @param resource what was stolen: Gold or Research
 * @param amount how much, at least 1
 */
public record Haul(Resource resource, int amount) {
}
