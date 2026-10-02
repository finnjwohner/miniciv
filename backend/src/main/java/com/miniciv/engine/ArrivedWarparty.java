package com.miniciv.engine;

/**
 * Another Nation's Warparty that has arrived at a Nation and waits there for
 * the Battle, as that Nation sees it: who sent it, and the number to offer it
 * Tribute by, but not its Units.
 *
 * @param nation the Nation that sent it
 * @param warparty its id among that Nation's Warparties
 */
public record ArrivedWarparty(String nation, int warparty) {
}
