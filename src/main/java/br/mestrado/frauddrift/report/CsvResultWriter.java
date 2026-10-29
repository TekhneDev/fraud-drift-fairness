package br.mestrado.frauddrift.report;

import br.mestrado.frauddrift.metrics.MonthMetrics;
import br.mestrado.frauddrift.strategy.DriftEvent;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Grava os resultados em CSV (formato longo), prontos para gráficos em Python, R ou Power BI.
 *
 * <ul>
 *   <li>{@code results.csv}: uma linha por estratégia × seed × mês</li>
 *   <li>{@code summary.csv}: média e desvio-padrão entre seeds, por estratégia × mês</li>
 *   <li>{@code drift_events.csv}: cada alarme de drift com sua posição no fluxo</li>
 *   <li>{@code config.txt}: parâmetros usados (reprodutibilidade)</li>
 * </ul>
 */
public final class CsvResultWriter {

    private static final List<Metric> SUMMARY_METRICS = List.of(
            new Metric("auc", MonthMetrics::auc),
            new Metric("tpr_at_target_fpr", MonthMetrics::tprAtTargetFpr),
            new Metric("fpr_ratio", MonthMetrics::fprRatioAtTarget),
            new Metric("tpr_ratio", MonthMetrics::tprRatioAtTarget),
            new Metric("deployed_tpr", MonthMetrics::deployedTpr),
            new Metric("deployed_fpr", MonthMetrics::deployedFpr),
            new Metric("deployed_fpr_ratio", MonthMetrics::deployedFprRatio));

    private record Metric(String name, ToDoubleFunction<MonthMetrics> getter) {
    }

    /** Alarme associado à estratégia e seed que o produziu. */
    public record DriftRecord(String strategy, long seed, DriftEvent event) {
    }

    private CsvResultWriter() {
    }

    public static void writeAll(Path outputDir, List<ResultRow> rows, List<DriftRecord> drifts,
                                Map<String, String> config) throws IOException {
        Files.createDirectories(outputDir);
        writeResults(outputDir.resolve("results.csv"), rows);
        writeSummary(outputDir.resolve("summary.csv"), rows);
        writeDrifts(outputDir.resolve("drift_events.csv"), drifts);
        writeConfig(outputDir.resolve("config.txt"), config);
    }

    static void writeResults(Path file, List<ResultRow> rows) throws IOException {
        try (PrintWriter out = writer(file)) {
            out.println("strategy,seed,month,instances,frauds,auc,tpr_at_target_fpr,fpr_ratio,tpr_ratio,"
                    + "protected_fpr,unprotected_fpr,deployed_threshold,deployed_tpr,deployed_fpr,"
                    + "deployed_fpr_ratio,deployed_tpr_ratio,drift_alarms");
            for (ResultRow row : rows) {
                MonthMetrics m = row.metrics();
                out.println(String.join(",",
                        row.strategy(), String.valueOf(row.seed()), String.valueOf(m.month()),
                        String.valueOf(m.instances()), String.valueOf(m.frauds()),
                        fmt(m.auc()), fmt(m.tprAtTargetFpr()), fmt(m.fprRatioAtTarget()), fmt(m.tprRatioAtTarget()),
                        fmt(m.protectedFprAtTarget()), fmt(m.unprotectedFprAtTarget()),
                        fmt(m.deployedThreshold()), fmt(m.deployedTpr()), fmt(m.deployedFpr()),
                        fmt(m.deployedFprRatio()), fmt(m.deployedTprRatio()),
                        String.valueOf(row.driftAlarmsInMonth())));
            }
        }
    }

    static void writeSummary(Path file, List<ResultRow> rows) throws IOException {
        Map<String, List<MonthMetrics>> groups = new LinkedHashMap<>();
        for (ResultRow row : rows) {
            groups.computeIfAbsent(row.strategy() + "|" + row.metrics().month(), k -> new ArrayList<>())
                    .add(row.metrics());
        }
        try (PrintWriter out = writer(file)) {
            StringBuilder header = new StringBuilder("strategy,month,seeds");
            for (Metric metric : SUMMARY_METRICS) {
                header.append(',').append(metric.name()).append("_mean,").append(metric.name()).append("_std");
            }
            out.println(header);
            for (Map.Entry<String, List<MonthMetrics>> entry : groups.entrySet()) {
                String[] key = entry.getKey().split("\\|");
                StringBuilder line = new StringBuilder()
                        .append(key[0]).append(',').append(key[1]).append(',').append(entry.getValue().size());
                for (Metric metric : SUMMARY_METRICS) {
                    double[] values = entry.getValue().stream().mapToDouble(metric.getter()).toArray();
                    line.append(',').append(fmt(Stats.mean(values))).append(',').append(fmt(Stats.std(values)));
                }
                out.println(line);
            }
        }
    }

    static void writeDrifts(Path file, List<DriftRecord> drifts) throws IOException {
        try (PrintWriter out = writer(file)) {
            out.println("strategy,seed,month,stream_position");
            for (DriftRecord d : drifts) {
                out.println(d.strategy() + "," + d.seed() + "," + d.event().month() + "," + d.event().streamPosition());
            }
        }
    }

    static void writeConfig(Path file, Map<String, String> config) throws IOException {
        try (PrintWriter out = writer(file)) {
            config.forEach((k, v) -> out.println(k + " = " + v));
        }
    }

    private static PrintWriter writer(Path file) throws IOException {
        return new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8));
    }

    /** Ponto como separador decimal sempre, para o CSV abrir igual em qualquer máquina. */
    static String fmt(double value) {
        if (Double.isNaN(value)) {
            return "";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        return String.format(Locale.ROOT, "%.6f", value);
    }

    /** Estatísticas que ignoram NaN (meses sem métrica definida). */
    static final class Stats {
        private Stats() {
        }

        static double mean(double[] values) {
            double sum = 0;
            int n = 0;
            for (double v : values) {
                if (!Double.isNaN(v)) {
                    sum += v;
                    n++;
                }
            }
            return n == 0 ? Double.NaN : sum / n;
        }

        /** Desvio-padrão amostral (n - 1); NaN com menos de duas observações. */
        static double std(double[] values) {
            double mean = mean(values);
            double sum = 0;
            int n = 0;
            for (double v : values) {
                if (!Double.isNaN(v)) {
                    sum += (v - mean) * (v - mean);
                    n++;
                }
            }
            return n < 2 ? Double.NaN : Math.sqrt(sum / (n - 1));
        }
    }
}
