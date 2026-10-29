package br.mestrado.frauddrift.strategy;

import br.mestrado.frauddrift.model.OnlineClassifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Comportamento comum: todas as estratégias partem do MESMO tipo de modelo inicial, treinado
 * nos meses de aquecimento. Assim, qualquer diferença na avaliação vem da política de
 * atualização, e não do ponto de partida.
 */
abstract class BaseStrategy implements AdaptationStrategy {

    protected final StrategyContext context;
    protected final List<DriftEvent> events = new ArrayList<>();
    protected OnlineClassifier model;
    private final String name;

    protected BaseStrategy(String name, StrategyContext context) {
        this.name = name;
        this.context = context;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void initialize(int[] warmupRowsInStreamOrder) {
        model = ModelTrainer.trainNew(context, warmupRowsInStreamOrder, context.config().warmupEpochs());
    }

    @Override
    public void onMonthStart(int month) {
        // padrão: nada a fazer
    }

    @Override
    public double score(double[] features) {
        if (model == null) {
            throw new IllegalStateException("Estratégia " + name + " usada antes de initialize()");
        }
        return model.predictProbability(features);
    }

    @Override
    public List<DriftEvent> driftEvents() {
        return List.copyOf(events);
    }

    /** Atualização online com subamostragem de legítimos. */
    protected void learnOnline(double[] features, int label) {
        if (context.keepForTraining(label)) {
            model.learn(features, label);
        }
    }
}
