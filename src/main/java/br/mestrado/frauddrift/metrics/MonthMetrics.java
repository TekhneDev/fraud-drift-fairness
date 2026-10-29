package br.mestrado.frauddrift.metrics;

/**
 * Métricas de um mês para uma estratégia e uma seed.
 *
 * <p>Dois pontos de operação são reportados:
 * <ul>
 *   <li><b>Oráculo</b>: limiar escolhido no próprio mês para dar FPR = alvo. É o protocolo do
 *       artigo do BAF e isola a qualidade do ranking do modelo.</li>
 *   <li><b>Implantado</b>: limiar escolhido no mês ANTERIOR e aplicado no mês atual, como
 *       aconteceria em produção. Mostra o efeito do drift sobre o próprio limiar.</li>
 * </ul>
 */
public record MonthMetrics(
        int month,
        long instances,
        long frauds,
        double auc,
        double tprAtTargetFpr,
        double fprRatioAtTarget,
        double tprRatioAtTarget,
        double deployedThreshold,
        double deployedTpr,
        double deployedFpr,
        double deployedFprRatio,
        double deployedTprRatio,
        double protectedFprAtTarget,
        double unprotectedFprAtTarget) {
}
