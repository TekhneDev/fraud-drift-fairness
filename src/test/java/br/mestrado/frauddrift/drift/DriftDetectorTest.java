package br.mestrado.frauddrift.drift;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriftDetectorTest {

    /** Fluxo de Bernoulli: taxa p0 até changeAt, depois p1. Retorna a posição do primeiro alarme (ou -1). */
    private static int firstAlarm(DriftDetector detector, double p0, double p1, int changeAt, int length, long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < length; i++) {
            double p = i < changeAt ? p0 : p1;
            if (detector.update(random.nextDouble() < p ? 1.0 : 0.0)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void adwinDetectsAnIncreaseShortlyAfterTheChange() {
        int alarm = firstAlarm(AdwinDetector.withDelta(0.002), 0.1, 0.4, 5_000, 10_000, 7);
        assertTrue(alarm >= 5_000, "alarme antes da mudança: " + alarm);
        assertTrue(alarm < 6_000, "detecção lenta demais: " + alarm);
    }

    @Test
    void adwinIgnoresImprovements() {
        // a perda CAI: a janela encolhe, mas não deve haver alarme de degradação
        assertEquals(-1, firstAlarm(AdwinDetector.withDelta(0.002), 0.4, 0.1, 5_000, 10_000, 7));
    }

    @Test
    void adwinWindowShrinksAfterChange() {
        AdwinDetector adwin = AdwinDetector.withDelta(0.002);
        Random random = new Random(3);
        for (int i = 0; i < 10_000; i++) {
            adwin.update(random.nextDouble() < (i < 5_000 ? 0.1 : 0.5) ? 1.0 : 0.0);
        }
        assertTrue(adwin.width() < 6_000, "janela deveria ter descartado o regime antigo: " + adwin.width());
        assertEquals(0.5, adwin.mean(), 0.05);
    }

    @Test
    void adwinStaysQuietOnStationaryStream() {
        int alarms = 0;
        for (long seed = 0; seed < 20; seed++) {
            if (firstAlarm(AdwinDetector.withDelta(0.002), 0.2, 0.2, 0, 20_000, seed) >= 0) {
                alarms++;
            }
        }
        assertTrue(alarms <= 2, "alarmes falsos demais em fluxo estável: " + alarms);
    }

    @Test
    void pageHinkleyDetectsAnIncrease() {
        int alarm = firstAlarm(PageHinkleyDetector.withDefaults(0.005, 20), 0.1, 0.5, 5_000, 10_000, 11);
        assertTrue(alarm >= 5_000 && alarm < 6_000, "posição do alarme: " + alarm);
    }

    @Test
    void pageHinkleyStaysQuietOnStationaryStream() {
        assertEquals(-1, firstAlarm(PageHinkleyDetector.withDefaults(0.005, 50), 0.2, 0.2, 0, 20_000, 5));
    }

    @Test
    void perClassMonitorSeesDegradationHiddenInTheAverage() {
        // fraudes (1%) passam a ser muito mal previstas; legítimos continuam ótimos
        PerClassLossMonitor perClass = new PerClassLossMonitor(AdwinDetector.withDelta(0.002));
        Random random = new Random(1);
        boolean perClassAlarm = false;
        for (int i = 0; i < 200_000; i++) {
            int label = random.nextDouble() < 0.01 ? 1 : 0;
            double p = label == 1 ? (i < 100_000 ? 0.8 : 0.3) : 0.02;
            perClassAlarm |= perClass.observe(label, p);
        }
        assertTrue(perClassAlarm, "monitor por classe deveria detectar a piora nas fraudes");
    }
}
