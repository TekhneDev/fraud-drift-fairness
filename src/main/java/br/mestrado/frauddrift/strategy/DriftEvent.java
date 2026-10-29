package br.mestrado.frauddrift.strategy;

/**
 * Registro de um alarme de drift.
 *
 * @param month          mês em que o alarme ocorreu
 * @param streamPosition posição no fluxo de avaliação (permite medir atraso de detecção)
 */
public record DriftEvent(int month, long streamPosition) {
}
