package br.mestrado.frauddrift.strategy;

import br.mestrado.frauddrift.drift.DriftMonitor;

/**
 * Adaptação "ativa": atualiza o modelo a cada rótulo E vigia a perda com um detector.
 * Quando o detector confirma degradação, substitui o modelo por um novo treinado só com os
 * exemplos mais recentes (guardados em um buffer circular), descartando o passado obsoleto.
 */
public final class DriftTriggeredStrategy extends BaseStrategy {

    private final DriftMonitor monitor;
    private final int[] recentRows;
    private int recentCount;
    private int recentNext;

    /**
     * @param name    nome da estratégia nos relatórios
     * @param context dependências
     * @param monitor o que observar e com qual detector
     */
    public DriftTriggeredStrategy(String name, StrategyContext context, DriftMonitor monitor) {
        super(name, context);
        this.monitor = monitor;
        this.recentRows = new int[context.config().driftBufferSize()];
    }

    @Override
    public void initialize(int[] warmupRowsInStreamOrder) {
        super.initialize(warmupRowsInStreamOrder);
        // o buffer começa com o fim do aquecimento, para que um alarme logo no início tenha dados
        for (int row : warmupRowsInStreamOrder) {
            remember(row);
        }
    }

    @Override
    public void onLabel(int row, double[] features, int label, double probability, int month, long streamPosition) {
        remember(row);
        if (monitor.observe(label, probability)) {
            events.add(new DriftEvent(month, streamPosition));
            model = ModelTrainer.trainNew(context, recentSnapshot(), context.config().warmupEpochs());
            monitor.reset(); // o novo modelo tem outro nível de perda; o histórico antigo não vale mais
            return;
        }
        learnOnline(features, label);
    }

    private void remember(int row) {
        recentRows[recentNext] = row;
        recentNext = (recentNext + 1) % recentRows.length;
        recentCount = Math.min(recentCount + 1, recentRows.length);
    }

    private int[] recentSnapshot() {
        int[] snapshot = new int[recentCount];
        int start = (recentNext - recentCount + recentRows.length) % recentRows.length;
        for (int i = 0; i < recentCount; i++) {
            snapshot[i] = recentRows[(start + i) % recentRows.length];
        }
        return snapshot;
    }
}
