package br.mestrado.frauddrift.strategy;

/**
 * Prática mais comum em empresas: no início de cada mês, descarta o modelo e treina um novo
 * com os últimos {@code periodicWindowMonths} meses já rotulados. Durante o mês, o modelo fica
 * congelado.
 */
public final class PeriodicRetrainStrategy extends BaseStrategy {

    private int firstEvaluationMonth = -1;

    public PeriodicRetrainStrategy(StrategyContext context) {
        super("PERIODIC", context);
    }

    @Override
    public void onMonthStart(int month) {
        if (firstEvaluationMonth < 0) {
            // primeiro mês avaliado: usa o modelo do aquecimento, igual às demais estratégias
            firstEvaluationMonth = month;
            return;
        }
        int window = context.config().periodicWindowMonths();
        int[] rows = context.data().rowsOfMonths(month - window, month - 1);
        if (rows.length > 0) {
            model = ModelTrainer.trainNew(context, rows, context.config().warmupEpochs());
        }
    }

    @Override
    public void onLabel(int row, double[] features, int label, double probability, int month, long streamPosition) {
        // congelado durante o mês; os rótulos serão usados no próximo retreino
    }
}
