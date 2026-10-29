package br.mestrado.frauddrift;

import br.mestrado.frauddrift.config.ExperimentConfig;

/**
 * Ponto de entrada.
 *
 * <pre>
 * java -jar target/fraud-drift-fairness-1.0.0.jar --data Base.csv --out results --seeds 1,2,3,4,5
 * </pre>
 *
 * Veja o README para todos os parâmetros.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        try {
            ExperimentConfig config = ExperimentConfig.fromArgs(args);
            new ExperimentRunner(config, System.out).run();
        } catch (IllegalArgumentException e) {
            System.err.println("Erro de configuração ou de dados: " + e.getMessage());
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Falha na execução: " + e);
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
