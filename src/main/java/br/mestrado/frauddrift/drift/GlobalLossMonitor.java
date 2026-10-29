package br.mestrado.frauddrift.drift;

/**
 * Monitora a perda de todos os exemplos em um único detector.
 *
 * <p>É a abordagem mais comum na literatura, mas com ~1% de fraude a perda média é dominada
 * pelos legítimos: uma piora só na detecção de fraudes pode passar despercebida. Esta é uma
 * das hipóteses que o artigo testa.
 */
public final class GlobalLossMonitor implements DriftMonitor {

    private final DriftDetector detector;

    public GlobalLossMonitor(DriftDetector detector) {
        this.detector = detector;
    }

    @Override
    public boolean observe(int label, double probability) {
        return detector.update(DriftMonitor.brierLoss(label, probability));
    }

    @Override
    public void reset() {
        detector.reset();
    }

    @Override
    public String name() {
        return detector.name() + "-global";
    }
}
