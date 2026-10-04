package com.miniciv.engine;

/**
 * Units of one type in training, started together.
 *
 * @param readyOnTurn the Turn at whose start they join the Army
 */
public record Training(String unit, int count, int readyOnTurn) implements Work {
}
