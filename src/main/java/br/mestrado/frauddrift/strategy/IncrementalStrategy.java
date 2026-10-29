package br.mestrado.frauddrift.strategy;

/**
 * Atualiza o modelo a cada rótulo, sem detector de drift. Representa a adaptação "passiva":
 * o modelo vai se ajustando aos poucos, mas também carrega o passado consigo.
 */
public final class IncrementalStrategy extends BaseStrategy {

    public IncrementalStrategy(StrategyContext context) {
        this("INCREMENTAL", context);
    }

    /** Permite reaproveitar esta política com outro modelo (ex.: classificadores do MOA). */
    public IncrementalStrategy(String name, StrategyContext context) {
        super(name, context);
    }

    @Override
    public void onLabel(int row, double[] features, int label, double probability, int month, long streamPosition) {
        learnOnline(features, label);
    }
}
