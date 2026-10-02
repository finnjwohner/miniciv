package com.miniciv.engine;

/**
 * Something one Nation learned of another's hidden state.
 *
 * @param turn the Turn it was learned, as the Turn resolved
 */
public record Fact<T>(T value, int turn) {
}
