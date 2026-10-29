package br.mestrado.frauddrift.drift;

/**
 * Teste de Page-Hinkley (Page, 1954) para detectar aumento na média de um fluxo, na mesma
 * formulação do MOA ({@code PageHinkleyDM}).
 *
 * <p>Intuição: acumula quanto cada valor ficou acima da média histórica, descontada uma
 * tolerância {@code delta}. O fator {@code alpha} &lt; 1 faz o acumulado "esquecer" devagar o
 * passado. Em regime estável o acumulado oscila em torno de {@code -delta / (1 - alpha)}; se a
 * média sobe de forma persistente, ele cresce até passar de {@code lambda} e dispara o alarme.
 *
 * <p>Consequência prática para o artigo: com os valores padrão, só aumentos sustentados de
 * pelo menos {@code delta + lambda * (1 - alpha)} na média são detectáveis. Por isso
 * {@code lambda} deve passar por análise de sensibilidade.
 */
public final class PageHinkleyDetector implements DriftDetector {

    private final double delta;
    private final double lambda;
    private final double alpha;
    private final int minInstances;

    private long count;
    private double mean;
    private double cumulative;

    /**
     * @param delta        tolerância de variação aceitável
     * @param lambda       limiar de alarme (maior = menos alarmes falsos, detecção mais lenta)
     * @param alpha        fator de esquecimento do acumulado, em (0, 1]
     * @param minInstances observações mínimas antes de permitir alarme
     */
    public PageHinkleyDetector(double delta, double lambda, double alpha, int minInstances) {
        if (lambda <= 0 || alpha <= 0 || alpha > 1 || minInstances < 1) {
            throw new IllegalArgumentException("Parâmetros inválidos para Page-Hinkley");
        }
        this.delta = delta;
        this.lambda = lambda;
        this.alpha = alpha;
        this.minInstances = minInstances;
        reset();
    }

    /** Valores padrão do MOA: delta=0.005, lambda=50, alpha=0.9999, mínimo de 30 observações. */
    public static PageHinkleyDetector withDefaults(double delta, double lambda) {
        return new PageHinkleyDetector(delta, lambda, 0.9999, 30);
    }

    @Override
    public boolean update(double value) {
        count++;
        mean += (value - mean) / count;
        cumulative = alpha * cumulative + (value - mean - delta);
        if (count < minInstances) {
            return false;
        }
        if (cumulative > lambda) {
            reset(); // após o alarme, recomeça a acumular a partir do novo regime
            return true;
        }
        return false;
    }

    @Override
    public void reset() {
        count = 0;
        mean = 0;
        cumulative = 0;
    }

    @Override
    public DriftDetector newInstance() {
        return new PageHinkleyDetector(delta, lambda, alpha, minInstances);
    }

    @Override
    public String name() {
        return "PageHinkley";
    }
}
