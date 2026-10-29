package br.mestrado.frauddrift.report;

import br.mestrado.frauddrift.metrics.MonthMetrics;

/**
 * Uma linha do arquivo de resultados: estratégia + seed + métricas do mês.
 */
public record ResultRow(String strategy, long seed, MonthMetrics metrics, int driftAlarmsInMonth) {
}
