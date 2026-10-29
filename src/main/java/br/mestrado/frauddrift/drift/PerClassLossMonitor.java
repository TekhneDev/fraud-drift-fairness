package br.mestrado.frauddrift.drift;

/**
 * Monitora a perda de fraudes e de legítimos em detectores separados; sinaliza drift se
 * qualquer um dos dois confirmar degradação.
 *
 * <p>Por que: separando as classes, uma piora na detecção das fraudes (classe rara) não fica
 * diluída na média dos legítimos. O custo é que o detector de fraudes recebe poucos exemplos
 * e, portanto, demora mais para acumular evidência.
 */
public final class PerClassLossMonitor implements DriftMonitor {

    private final DriftDetector fraudDetector;
    private final DriftDetector legitimateDetector;

    /** @param prototype detector cujas cópias monitoram cada classe */
    public PerClassLossMonitor(DriftDetector prototype) {
        this.fraudDetector = prototype.newInstance();
        this.legitimateDetector = prototype.newInstance();
    }

    @Override
    public boolean observe(int label, double probability) {
        double loss = DriftMonitor.brierLoss(label, probability);
        return label == 1 ? fraudDetector.update(loss) : legitimateDetector.update(loss);
    }

    @Override
    public void reset() {
        fraudDetector.reset();
        legitimateDetector.reset();
    }

    @Override
    public String name() {
        return fraudDetector.name() + "-per-class";
    }
}
