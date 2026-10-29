package br.mestrado.frauddrift.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FairnessMetricsTest {

    @Test
    void ratioIsSymmetricAndBounded() {
        assertEquals(0.5, FairnessMetrics.parityRatio(0.02, 0.04), 1e-12);
        assertEquals(0.5, FairnessMetrics.parityRatio(0.04, 0.02), 1e-12);
    }

    @Test
    void bothZeroMeansParity() {
        assertEquals(1.0, FairnessMetrics.parityRatio(0.0, 0.0), 1e-12);
    }

    @Test
    void undefinedRatePropagatesNaN() {
        assertTrue(Double.isNaN(FairnessMetrics.parityRatio(Double.NaN, 0.1)));
    }
}
