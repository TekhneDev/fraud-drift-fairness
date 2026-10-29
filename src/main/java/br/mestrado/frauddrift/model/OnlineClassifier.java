package br.mestrado.frauddrift.model;

/**
 * Contrato mínimo de um classificador que aprende exemplo a exemplo.
 *
 * <p>Manter a interface pequena permite trocar o modelo (regressão logística, ou um
 * classificador do MOA via adaptador) sem mexer nas estratégias nem na avaliação.
 */
public interface OnlineClassifier {

    /** Probabilidade estimada de fraude, em [0, 1]. */
    double predictProbability(double[] features);

    /**
     * Atualiza o modelo com um exemplo rotulado.
     *
     * @param features vetor de features
     * @param label    0 (legítimo) ou 1 (fraude)
     */
    void learn(double[] features, int label);

    /** Nova instância com os mesmos hiperparâmetros e nenhum aprendizado (usada em retreinos). */
    OnlineClassifier newUntrained();

    /** Nome curto para relatórios. */
    String name();
}
