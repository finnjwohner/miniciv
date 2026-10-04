package com.miniciv.engine;

/**
 * A Building under construction and how many more Turns it needs.
 */
public record Construction(String building, int turnsLeft) implements Work {
}
