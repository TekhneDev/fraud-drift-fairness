package br.mestrado.frauddrift.strategy;

import java.util.List;

/**
 * Política de atualização do modelo ao longo do fluxo.
 *
 * <p>O avaliador chama, para cada exemplo e nesta ordem: {@link #score} (predição sem conhecer o
 * rótulo) e depois {@link #onLabel} (rótulo revelado). Essa ordem é o protocolo prequential
 * (testar e depois treinar) e impede que o modelo seja avaliado em um exemplo que já viu.
 */
public interface AdaptationStrategy {

    String name();

    /** Treina o modelo inicial com os meses de aquecimento (mesmo ponto de partida para todas). */
    void initialize(int[] warmupRowsInStreamOrder);

    /** Avisado quando o fluxo entra em um novo mês (usado pelo retreino periódico). */
    void onMonthStart(int month);

    /** Probabilidade de fraude para o exemplo, calculada antes de conhecer o rótulo. */
    double score(double[] features);

    /**
     * Rótulo revelado depois da predição.
     *
     * @param row            linha da tabela
     * @param features       vetor já codificado (o mesmo usado em {@link #score})
     * @param label          rótulo verdadeiro
     * @param probability    a predição feita antes do rótulo
     * @param month          mês do exemplo
     * @param streamPosition posição do exemplo no fluxo de avaliação (0, 1, 2...)
     */
    void onLabel(int row, double[] features, int label, double probability, int month, long streamPosition);

    /** Alarmes de drift que levaram a retreino, em ordem. */
    List<DriftEvent> driftEvents();
}
