package br.mestrado.frauddrift.drift;

/**
 * Detector de mudança em um fluxo de valores reais (aqui, a perda do modelo exemplo a exemplo).
 */
public interface DriftDetector {

    /**
     * Recebe o próximo valor do fluxo.
     *
     * @param value valor observado (ex.: perda de Brier em [0, 1])
     * @return verdadeiro se este valor confirmou um aumento significativo da média (degradação)
     */
    boolean update(double value);

    /** Volta ao estado inicial, como se nada tivesse sido observado. */
    void reset();

    /** Instância nova com os mesmos parâmetros. */
    DriftDetector newInstance();

    /** Nome curto para relatórios. */
    String name();
}
