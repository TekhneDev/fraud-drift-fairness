package br.mestrado.frauddrift.metrics;

import br.mestrado.frauddrift.eval.MonthlyScores;

import java.util.ArrayList;
import java.util.List;

/**
 * Converte as predições de cada mês em {@link MonthMetrics}.
 */
public final class MetricsCalculator {

    private MetricsCalculator() {
    }

    /**
     * @param scores    predições por mês
     * @param targetFpr FPR do ponto de operação (ex.: 0.05)
     * @return uma linha por mês, em ordem crescente
     */
    public static List<MonthMetrics> compute(MonthlyScores scores, double targetFpr) {
        List<MonthMetrics> result = new ArrayList<>();
        double previousThreshold = Double.NaN; // não há limiar implantado no primeiro mês avaliado
        for (MonthlyScores.Month month : scores.months()) {
            double[] s = month.scores();
            int[] y = month.labels();
            boolean[] g = month.protectedGroup();

            double oracleThreshold = BinaryMetrics.thresholdAtFpr(s, y, targetFpr);
            BinaryMetrics.Rates overall = BinaryMetrics.rates(s, y, oracleThreshold);
            BinaryMetrics.Rates prot = BinaryMetrics.rates(s, y, oracleThreshold, g, true);
            BinaryMetrics.Rates unprot = BinaryMetrics.rates(s, y, oracleThreshold, g, false);

            Deployed deployed = deployed(s, y, g, previousThreshold);

            result.add(new MonthMetrics(
                    month.month(),
                    month.size(),
                    overall.positives(),
                    BinaryMetrics.auc(s, y),
                    overall.tpr(),
                    FairnessMetrics.parityRatio(prot.fpr(), unprot.fpr()),
                    FairnessMetrics.parityRatio(prot.tpr(), unprot.tpr()),
                    previousThreshold,
                    deployed.tpr, deployed.fpr, deployed.fprRatio, deployed.tprRatio,
                    prot.fpr(), unprot.fpr()));
            previousThreshold = oracleThreshold;
        }
        return result;
    }

    private record Deployed(double tpr, double fpr, double fprRatio, double tprRatio) {
        static final Deployed UNDEFINED = new Deployed(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    private static Deployed deployed(double[] s, int[] y, boolean[] g, double threshold) {
        if (Double.isNaN(threshold)) {
            return Deployed.UNDEFINED;
        }
        BinaryMetrics.Rates all = BinaryMetrics.rates(s, y, threshold);
        BinaryMetrics.Rates prot = BinaryMetrics.rates(s, y, threshold, g, true);
        BinaryMetrics.Rates unprot = BinaryMetrics.rates(s, y, threshold, g, false);
        return new Deployed(all.tpr(), all.fpr(),
                FairnessMetrics.parityRatio(prot.fpr(), unprot.fpr()),
                FairnessMetrics.parityRatio(prot.tpr(), unprot.tpr()));
    }
}
