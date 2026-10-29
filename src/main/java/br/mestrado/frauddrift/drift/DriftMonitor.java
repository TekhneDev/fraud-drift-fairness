package br.mestrado.frauddrift.drift;

/**
 * Decide o que o detector observa a partir de cada predição rotulada.
 *
 * <p>Separar "o que observar" (monitor) de "como testar" (detector) é o que permite comparar,
 * no artigo, monitoramento global versus monitoramento por classe com o mesmo detector.
 */
public interface DriftMonitor {

    /**
     * @param label       rótulo verdadeiro (0 ou 1)
     * @param probability probabilidade de fraude prevista ANTES de conhecer o rótulo
     * @return verdadeiro se houve drift (degradação) confirmado
     */
    boolean observe(int label, double probability);

    /** Esquece o histórico (chamado depois que o modelo é substituído). */
    void reset();

    String name();

    /** Perda de Brier: (p - y)^2, em [0, 1]; menor é melhor. */
    static double brierLoss(int label, double probability) {
        double error = probability - label;
        return error * error;
    }
}
