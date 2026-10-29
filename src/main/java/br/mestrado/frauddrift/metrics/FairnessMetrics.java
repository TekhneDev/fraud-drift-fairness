package br.mestrado.frauddrift.metrics;

/**
 * Métricas de justiça entre dois grupos, na forma de razão menor/maior (1 = paridade perfeita).
 *
 * <ul>
 *   <li>Razão de FPR (igualdade preditiva): legítimos de um grupo são barrados por engano com
 *       a mesma frequência que os do outro? É a métrica de justiça adotada no artigo do BAF, porque
 *       um falso positivo nega a abertura de conta a um cliente honesto.</li>
 *   <li>Razão de TPR (igualdade de oportunidade): fraudes de ambos os grupos são pegas na mesma
 *       proporção?</li>
 * </ul>
 */
public final class FairnessMetrics {

    private FairnessMetrics() {
    }

    /**
     * @return min(a, b) / max(a, b); 1 se ambos forem zero; NaN se algum for NaN
     */
    public static double parityRatio(double a, double b) {
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return Double.NaN;
        }
        double max = Math.max(a, b);
        if (max == 0) {
            return 1.0;
        }
        return Math.min(a, b) / max;
    }
}
