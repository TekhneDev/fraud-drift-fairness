package br.mestrado.frauddrift.strategy;

/**
 * Linha de base: treina no aquecimento e nunca mais atualiza. Mostra o quanto o modelo
 * "envelhece" quando ninguém faz nada.
 */
public final class StaticStrategy extends BaseStrategy {

    public StaticStrategy(StrategyContext context) {
        super("STATIC", context);
    }

    @Override
    public void onLabel(int row, double[] features, int label, double probability, int month, long streamPosition) {
        // modelo congelado: ignora os rótulos que chegam
    }
}
