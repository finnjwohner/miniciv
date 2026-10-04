package com.miniciv.engine;

/**
 * Something a Nation has in progress: a Building under construction, a Repair
 * or Rebuild, or Units in training.
 */
public sealed interface Work permits Construction, Repair, Training {
}
