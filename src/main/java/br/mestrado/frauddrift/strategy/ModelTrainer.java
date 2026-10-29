package br.mestrado.frauddrift.strategy;

import br.mestrado.frauddrift.data.StreamOrder;
import br.mestrado.frauddrift.model.OnlineClassifier;

/**
 * Treino em lote (várias épocas) usado no aquecimento e nos retreinos.
 */
public final class ModelTrainer {

    private ModelTrainer() {
    }

    /**
     * Cria um modelo novo e o treina com as linhas informadas.
     *
     * @param context dependências (encoder, subamostragem, semente)
     * @param rows    linhas de treino (todas com rótulo já conhecido)
     * @param epochs  passadas sobre os dados; a ordem é reembaralhada a cada época
     * @return modelo treinado
     */
    public static OnlineClassifier trainNew(StrategyContext context, int[] rows, int epochs) {
        OnlineClassifier model = context.modelFactory().get();
        int[] order = rows.clone();
        for (int epoch = 0; epoch < epochs; epoch++) {
            StreamOrder.shuffle(order, context.random());
            for (int row : order) {
                int label = context.data().label(row);
                if (context.keepForTraining(label)) {
                    model.learn(context.encoder().encode(row), label);
                }
            }
        }
        return model;
    }
}
