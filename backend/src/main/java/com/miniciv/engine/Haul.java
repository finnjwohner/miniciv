package com.miniciv.engine;

/**
 * The Gold or Research a Spy stole, which reaches its Nation only once the Spy
 * has carried it home; a Nation that catches the Spy on the way seizes it. A
 * Warparty that wipes out another in an Encounter takes the Plunder it carried
 * the same way, each resource as a Haul of its own.
 *
 * @param resource what was stolen: Gold or Research, or Population for
 *        Captives taken from a Barbarian Camp's Warparty
 * @param amount how much, at least 1
 */
public record Haul(Resource resource, int amount) {
}
