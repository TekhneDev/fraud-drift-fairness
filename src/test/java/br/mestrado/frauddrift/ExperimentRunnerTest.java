package br.mestrado.frauddrift;

import br.mestrado.frauddrift.config.ExperimentConfig;
import br.mestrado.frauddrift.report.ResultRow;
import br.mestrado.frauddrift.synthetic.SyntheticBafGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Teste de ponta a ponta com dados sintéticos pequenos: garante que tudo se conecta. */
class ExperimentRunnerTest {

    @Test
    void runsAllCoreStrategiesAndWritesOutputs() throws IOException {
        Path dir = Files.createTempDirectory("fdf-e2e");
        Path csv = dir.resolve("synthetic.csv");
        SyntheticBafGenerator.write(csv, 40_000, 3);
        Path out = dir.resolve("results");
        ExperimentConfig config = ExperimentConfig.fromArgs(new String[]{
                "--data", csv.toString(), "--out", out.toString(), "--seeds", "1,2",
                "--drift-buffer", "4000"});

        PrintStream silent = new PrintStream(OutputStream.nullOutputStream());
        ExperimentRunner.Outcome outcome = new ExperimentRunner(config, silent).run();

        Set<String> strategies = outcome.rows().stream().map(ResultRow::strategy).collect(Collectors.toSet());
        assertEquals(Set.copyOf(ExperimentConfig.CORE_STRATEGIES), strategies);
        // 7 estratégias x 2 seeds x 5 meses avaliados (3 a 7)
        assertEquals(7 * 2 * 5, outcome.rows().size());
        for (String file : List.of("results.csv", "summary.csv", "drift_events.csv", "config.txt")) {
            assertTrue(Files.size(out.resolve(file)) > 0, "arquivo vazio: " + file);
        }
        double meanAuc = outcome.rows().stream().mapToDouble(r -> r.metrics().auc())
                .filter(v -> !Double.isNaN(v)).average().orElse(0);
        assertTrue(meanAuc > 0.6, "o modelo deveria aprender algo nos dados sintéticos: " + meanAuc);
    }
}
