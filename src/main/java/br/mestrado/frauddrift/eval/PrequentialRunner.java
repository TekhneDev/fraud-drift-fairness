package br.mestrado.frauddrift.eval;

import br.mestrado.frauddrift.data.ExperimentData;
import br.mestrado.frauddrift.features.FeatureEncoder;
import br.mestrado.frauddrift.strategy.AdaptationStrategy;
import br.mestrado.frauddrift.strategy.DriftEvent;

import java.util.Arrays;
import java.util.List;

/**
 * Avaliação prequential ("predict, then train"): cada exemplo é primeiro previsto e só depois
 * o seu rótulo é entregue à estratégia. Assim toda predição é feita sobre um exemplo inédito,
 * sem precisar separar treino e teste de forma artificial em um fluxo.
 */
public final class PrequentialRunner {

    /** Resultado de uma execução. */
    public record RunResult(MonthlyScores scores, List<DriftEvent> driftEvents, long elapsedMillis) {
    }

    private PrequentialRunner() {
    }

    /**
     * @param strategy           estratégia ainda não inicializada
     * @param data               dados
     * @param encoder            encoder ajustado no aquecimento
     * @param streamOrder        ordem das linhas no fluxo (todas as linhas, aquecimento incluído)
     * @param firstEvalMonth     primeiro mês avaliado (meses anteriores = aquecimento)
     * @return predições por mês e alarmes de drift
     */
    public static RunResult run(AdaptationStrategy strategy, ExperimentData data, FeatureEncoder encoder,
                                int[] streamOrder, int firstEvalMonth) {
        long start = System.nanoTime();
        int[] warmupRows = Arrays.stream(streamOrder).filter(r -> data.month(r) < firstEvalMonth).toArray();
        strategy.initialize(warmupRows);

        MonthlyScores scores = new MonthlyScores();
        int currentMonth = -1;
        long position = 0;
        for (int row : streamOrder) {
            int month = data.month(row);
            if (month < firstEvalMonth) {
                continue;
            }
            if (month != currentMonth) {
                currentMonth = month;
                strategy.onMonthStart(month);
            }
            double[] features = encoder.encode(row);
            double probability = strategy.score(features);          // 1) testa
            int label = data.label(row);
            scores.add(month, probability, label, data.isProtected(row));
            strategy.onLabel(row, features, label, probability, month, position); // 2) treina
            position++;
        }
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        return new RunResult(scores, strategy.driftEvents(), elapsed);
    }
}
