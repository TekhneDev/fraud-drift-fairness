package br.mestrado.frauddrift;

import br.mestrado.frauddrift.config.ExperimentConfig;
import br.mestrado.frauddrift.data.ColumnarTable;
import br.mestrado.frauddrift.data.CsvTableLoader;
import br.mestrado.frauddrift.data.ExperimentData;
import br.mestrado.frauddrift.data.StreamOrder;
import br.mestrado.frauddrift.eval.PrequentialRunner;
import br.mestrado.frauddrift.features.FeatureEncoder;
import br.mestrado.frauddrift.metrics.MetricsCalculator;
import br.mestrado.frauddrift.metrics.MonthMetrics;
import br.mestrado.frauddrift.model.OnlineLogisticRegression;
import br.mestrado.frauddrift.report.CsvResultWriter;
import br.mestrado.frauddrift.report.ResultRow;
import br.mestrado.frauddrift.strategy.AdaptationStrategy;
import br.mestrado.frauddrift.strategy.DriftEvent;
import br.mestrado.frauddrift.strategy.StrategyContext;
import br.mestrado.frauddrift.strategy.StrategyFactory;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Orquestra o experimento completo: carrega dados, ajusta o encoder no aquecimento e, para cada
 * seed e estratégia, executa a avaliação prequential e calcula as métricas mensais.
 */
public final class ExperimentRunner {

    private final ExperimentConfig config;
    private final PrintStream log;

    public ExperimentRunner(ExperimentConfig config, PrintStream log) {
        this.config = config;
        this.log = log;
    }

    /** Resultado em memória, além dos arquivos gravados. */
    public record Outcome(List<ResultRow> rows, List<CsvResultWriter.DriftRecord> drifts) {
    }

    public Outcome run() throws IOException {
        log.printf("Carregando %s ...%n", config.dataPath());
        ColumnarTable table = CsvTableLoader.load(config.dataPath(), config.maxRows());
        ExperimentData data = ExperimentData.from(table, config);
        int[] warmupRows = data.rowsOfMonths(0, config.warmupMonths() - 1);
        FeatureEncoder encoder = FeatureEncoder.fit(data, warmupRows);
        log.printf("%d linhas, %d meses, %d features após codificação%n",
                data.rowCount(), data.monthCount(), encoder.dimension());

        List<ResultRow> rows = new ArrayList<>();
        List<CsvResultWriter.DriftRecord> drifts = new ArrayList<>();
        for (long seed : config.seeds()) {
            int[] order = StreamOrder.chronological(data, seed);
            for (String strategyName : config.strategies()) {
                runOne(strategyName, seed, data, encoder, order, rows, drifts);
            }
        }
        CsvResultWriter.writeAll(config.outputDir(), rows, drifts, runMetadata(encoder));
        log.printf("Resultados gravados em %s%n", config.outputDir().toAbsolutePath());
        return new Outcome(rows, drifts);
    }

    private void runOne(String strategyName, long seed, ExperimentData data, FeatureEncoder encoder, int[] order,
                        List<ResultRow> rows, List<CsvResultWriter.DriftRecord> drifts) {
        // semente derivada de (seed, estratégia): cada estratégia tem sorteios próprios e reprodutíveis
        Random random = new Random(seed * 1_000_003L + strategyName.hashCode());
        StrategyContext context = new StrategyContext(data, encoder, config, random,
                () -> new OnlineLogisticRegression(encoder.dimension(), config.learningRate(), config.l2()));
        AdaptationStrategy strategy = StrategyFactory.create(strategyName, context);
        PrequentialRunner.RunResult result =
                PrequentialRunner.run(strategy, data, encoder, order, config.warmupMonths());

        Map<Integer, Integer> alarmsByMonth = new LinkedHashMap<>();
        for (DriftEvent event : result.driftEvents()) {
            alarmsByMonth.merge(event.month(), 1, Integer::sum);
            drifts.add(new CsvResultWriter.DriftRecord(strategyName, seed, event));
        }
        List<MonthMetrics> metrics = MetricsCalculator.compute(result.scores(), config.targetFpr());
        for (MonthMetrics m : metrics) {
            rows.add(new ResultRow(strategyName, seed, m, alarmsByMonth.getOrDefault(m.month(), 0)));
        }
        double meanAuc = metrics.stream().mapToDouble(MonthMetrics::auc).filter(v -> !Double.isNaN(v))
                .average().orElse(Double.NaN);
        log.printf("seed=%d %-16s AUC média=%.4f alarmes=%d tempo=%d ms%n",
                seed, strategyName, meanAuc, result.driftEvents().size(), result.elapsedMillis());
    }

    private Map<String, String> runMetadata(FeatureEncoder encoder) {
        Map<String, String> meta = new LinkedHashMap<>(config.asMap());
        meta.put("javaVersion", System.getProperty("java.version"));
        meta.put("featureDimension", String.valueOf(encoder.dimension()));
        meta.put("features", String.join(";", encoder.featureNames()));
        return meta;
    }
}
