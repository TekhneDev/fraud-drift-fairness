package br.mestrado.frauddrift.strategy;

import br.mestrado.frauddrift.config.ExperimentConfig;
import br.mestrado.frauddrift.data.ExperimentData;
import br.mestrado.frauddrift.features.FeatureEncoder;
import br.mestrado.frauddrift.model.OnlineClassifier;

import java.util.Random;
import java.util.function.Supplier;

/**
 * Dependências compartilhadas pelas estratégias.
 *
 * @param data         dados do experimento
 * @param encoder      encoder ajustado no aquecimento
 * @param config       configuração
 * @param random       gerador com semente (subamostragem e embaralhamento de épocas)
 * @param modelFactory cria um modelo novo, sem treino
 */
public record StrategyContext(
        ExperimentData data,
        FeatureEncoder encoder,
        ExperimentConfig config,
        Random random,
        Supplier<OnlineClassifier> modelFactory) {

    /**
     * Subamostragem de legítimos: toda fraude é usada; cada legítimo entra com probabilidade
     * {@code negativeSampleRate}. Sem isso o modelo aprende a dizer "legítimo" para tudo.
     */
    public boolean keepForTraining(int label) {
        return label == 1 || random.nextDouble() < config.negativeSampleRate();
    }
}
