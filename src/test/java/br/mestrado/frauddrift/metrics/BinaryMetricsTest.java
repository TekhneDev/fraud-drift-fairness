package br.mestrado.frauddrift.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinaryMetricsTest {

    private static final double EPS = 1e-12;

    @Test
    void aucIsOneForPerfectRanking() {
        assertEquals(1.0, BinaryMetrics.auc(new double[]{0.1, 0.2, 0.8, 0.9}, new int[]{0, 0, 1, 1}), EPS);
    }

    @Test
    void aucIsZeroForInvertedRanking() {
        assertEquals(0.0, BinaryMetrics.auc(new double[]{0.9, 0.8, 0.2, 0.1}, new int[]{0, 0, 1, 1}), EPS);
    }

    @Test
    void aucIsHalfWhenAllScoresTie() {
        assertEquals(0.5, BinaryMetrics.auc(new double[]{0.3, 0.3, 0.3, 0.3}, new int[]{0, 1, 0, 1}), EPS);
    }

    @Test
    void aucMatchesHandComputedPairs() {
        // pares (fraude, legítimo): (0.6,0.4)=1, (0.6,0.7)=0, (0.6,0.2)=1, (0.3,0.4)=0, (0.3,0.7)=0, (0.3,0.2)=1 -> 3/6
        double[] scores = {0.6, 0.4, 0.7, 0.3, 0.2};
        int[] labels = {1, 0, 0, 1, 0};
        assertEquals(0.5, BinaryMetrics.auc(scores, labels), EPS);
    }

    @Test
    void aucIsNaNWithoutBothClasses() {
        assertTrue(Double.isNaN(BinaryMetrics.auc(new double[]{0.1, 0.2}, new int[]{0, 0})));
    }

    @Test
    void thresholdRespectsTargetFpr() {
        // 20 legítimos com scores 0.00..0.19 e 2 fraudes; alvo 10% -> no máximo 2 falsos positivos
        double[] scores = new double[22];
        int[] labels = new int[22];
        for (int i = 0; i < 20; i++) {
            scores[i] = i / 100.0;
        }
        scores[20] = 0.5;
        scores[21] = 0.15;
        labels[20] = 1;
        labels[21] = 1;
        double threshold = BinaryMetrics.thresholdAtFpr(scores, labels, 0.10);
        BinaryMetrics.Rates rates = BinaryMetrics.rates(scores, labels, threshold);
        assertEquals(0.17, threshold, EPS);
        assertEquals(2.0 / 20, rates.fpr(), EPS);
        assertEquals(0.5, rates.tpr(), EPS);
    }

    @Test
    void ratesCanBeRestrictedToAGroup() {
        double[] scores = {0.9, 0.9, 0.1, 0.1};
        int[] labels = {0, 0, 0, 0};
        boolean[] group = {true, false, true, false};
        BinaryMetrics.Rates protectedRates = BinaryMetrics.rates(scores, labels, 0.5, group, true);
        assertEquals(0.5, protectedRates.fpr(), EPS);
        assertEquals(2, protectedRates.negatives());
        assertTrue(Double.isNaN(protectedRates.tpr()));
    }
}
