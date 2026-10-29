package br.mestrado.frauddrift.eval;

import br.mestrado.frauddrift.config.ExperimentConfig;
import br.mestrado.frauddrift.data.CsvTableLoader;
import br.mestrado.frauddrift.data.ExperimentData;
import br.mestrado.frauddrift.data.StreamOrder;
import br.mestrado.frauddrift.features.FeatureEncoder;
import br.mestrado.frauddrift.strategy.AdaptationStrategy;
import br.mestrado.frauddrift.strategy.DriftEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrequentialRunnerTest {

    /** Estratégia espiã: registra a ordem das chamadas e quais linhas já tiveram rótulo revelado. */
    private static final class SpyStrategy implements AdaptationStrategy {
        final List<String> calls = new ArrayList<>();
        final Set<Integer> labelled = new HashSet<>();
        int warmupSize = -1;
        boolean labelledTwice;

        @Override
        public String name() {
            return "SPY";
        }

        @Override
        public void initialize(int[] warmupRows) {
            warmupSize = warmupRows.length;
        }

        @Override
        public void onMonthStart(int month) {
            calls.add("month:" + month);
        }

        @Override
        public double score(double[] features) {
            calls.add("score");
            return 0.5;
        }

        @Override
        public void onLabel(int row, double[] features, int label, double p, int month, long position) {
            calls.add("label");
            labelledTwice |= !labelled.add(row); // a mesma linha nunca pode ser rotulada duas vezes
        }

        @Override
        public List<DriftEvent> driftEvents() {
            return List.of();
        }
    }

    @Test
    void everyPredictionHappensBeforeItsLabelAndWarmupIsExcluded() throws IOException {
        Path file = Files.createTempFile("preq", ".csv");
        file.toFile().deleteOnExit();
        StringBuilder csv = new StringBuilder("fraud_bool,customer_age,x,month\n");
        for (int i = 0; i < 40; i++) {
            csv.append(i % 7 == 0 ? 1 : 0).append(',').append(20 + i).append(',').append(i).append(',')
                    .append(i / 10).append('\n');
        }
        Files.writeString(file, csv);
        ExperimentConfig config = ExperimentConfig.fromArgs(new String[]{
                "--data", file.toString(), "--warmup-months", "2"});
        ExperimentData data = ExperimentData.from(CsvTableLoader.load(file, 0), config);
        FeatureEncoder encoder = FeatureEncoder.fit(data, data.rowsOfMonths(0, 1));
        SpyStrategy spy = new SpyStrategy();

        PrequentialRunner.RunResult result =
                PrequentialRunner.run(spy, data, encoder, StreamOrder.chronological(data, 1), 2);

        assertEquals(20, spy.warmupSize, "aquecimento = meses 0 e 1");
        assertEquals(20, spy.labelled.size(), "só meses 2 e 3 são avaliados");
        assertFalse(spy.labelledTwice);
        // padrão esperado: month:2, (score, label) x10, month:3, (score, label) x10
        List<String> filtered = spy.calls.stream().filter(c -> !c.startsWith("month")).toList();
        for (int i = 0; i < filtered.size(); i += 2) {
            assertEquals("score", filtered.get(i));
            assertEquals("label", filtered.get(i + 1));
        }
        assertTrue(spy.calls.contains("month:2") && spy.calls.contains("month:3"));
        int months = 0;
        for (MonthlyScores.Month ignored : result.scores().months()) {
            months++;
        }
        assertEquals(2, months);
    }
}
