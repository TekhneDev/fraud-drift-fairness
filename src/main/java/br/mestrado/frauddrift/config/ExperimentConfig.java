package br.mestrado.frauddrift.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Todos os parâmetros do experimento em um único objeto imutável.
 *
 * <p>Centralizar a configuração garante que cada execução seja descrita por um conjunto
 * explícito de valores, que é gravado junto com os resultados (reprodutibilidade).
 *
 * @param dataPath             caminho do CSV do BAF (ex.: Base.csv)
 * @param outputDir            pasta onde os CSVs de resultado serão gravados
 * @param seeds                sementes; cada uma gera uma ordem diferente dentro do mês e um sorteio diferente
 * @param warmupMonths         meses iniciais usados só para treinar o modelo inicial (não entram na avaliação)
 * @param warmupEpochs         passadas sobre os dados de aquecimento ao treinar em lote
 * @param negativeSampleRate   fração de legítimos mantida no treino (subamostragem para lidar com ~1% de fraude)
 * @param targetFpr            taxa de falso positivo do ponto de operação (BAF usa 5%)
 * @param protectedAgeThreshold idade a partir da qual o cliente pertence ao grupo protegido
 * @param labelColumn          coluna do rótulo binário
 * @param monthColumn          coluna do mês (0 a 7 no BAF)
 * @param ageColumn            coluna de idade usada para definir o grupo protegido
 * @param excludeProtected     se verdadeiro, remove a idade das features (justiça "por desconhecimento")
 * @param periodicWindowMonths janela, em meses, do retreino periódico
 * @param driftBufferSize      quantos exemplos recentes são guardados para retreinar após um drift
 * @param learningRate         taxa de aprendizado do AdaGrad
 * @param l2                   regularização L2
 * @param adwinDelta           confiança do ADWIN (menor = mais conservador)
 * @param phDelta              tolerância do Page-Hinkley
 * @param phLambda             limiar de alarme do Page-Hinkley
 * @param strategies           estratégias a executar
 * @param maxRows              limite de linhas lidas (0 = todas), útil para testes rápidos
 */
public record ExperimentConfig(
        Path dataPath,
        Path outputDir,
        List<Long> seeds,
        int warmupMonths,
        int warmupEpochs,
        double negativeSampleRate,
        double targetFpr,
        double protectedAgeThreshold,
        String labelColumn,
        String monthColumn,
        String ageColumn,
        boolean excludeProtected,
        int periodicWindowMonths,
        int driftBufferSize,
        double learningRate,
        double l2,
        double adwinDelta,
        double phDelta,
        double phLambda,
        List<String> strategies,
        int maxRows) {

    /** Estratégias implementadas no núcleo (sem dependências externas). */
    public static final List<String> CORE_STRATEGIES = List.of(
            "STATIC", "PERIODIC", "INCREMENTAL",
            "ADWIN_GLOBAL", "PH_GLOBAL", "ADWIN_PER_CLASS", "PH_PER_CLASS");

    public ExperimentConfig {
        Objects.requireNonNull(dataPath, "dataPath");
        Objects.requireNonNull(outputDir, "outputDir");
        seeds = List.copyOf(seeds);
        strategies = List.copyOf(new LinkedHashSet<>(strategies)); // sem repetições, na ordem informada
        requireThat(!seeds.isEmpty(), "informe ao menos uma seed");
        requireThat(warmupMonths >= 1, "warmupMonths deve ser >= 1");
        requireThat(warmupEpochs >= 1, "warmupEpochs deve ser >= 1");
        requireThat(negativeSampleRate > 0 && negativeSampleRate <= 1, "negativeSampleRate deve estar em (0, 1]");
        requireThat(targetFpr > 0 && targetFpr < 1, "targetFpr deve estar em (0, 1)");
        requireThat(periodicWindowMonths >= 1, "periodicWindowMonths deve ser >= 1");
        requireThat(driftBufferSize >= 100, "driftBufferSize deve ser >= 100");
        requireThat(learningRate > 0, "learningRate deve ser > 0");
        requireThat(l2 >= 0, "l2 deve ser >= 0");
        requireThat(adwinDelta > 0 && adwinDelta < 1, "adwinDelta deve estar em (0, 1)");
        requireThat(phLambda > 0, "phLambda deve ser > 0");
        requireThat(maxRows >= 0, "maxRows deve ser >= 0");
        requireThat(!strategies.isEmpty(), "informe ao menos uma estratégia");
    }

    /**
     * Lê a configuração de argumentos no formato {@code --chave valor}.
     *
     * @param args argumentos da linha de comando
     * @return configuração validada
     * @throws IllegalArgumentException se faltar {@code --data} ou algum valor for inválido
     */
    public static ExperimentConfig fromArgs(String[] args) {
        Map<String, String> options = parseOptions(args);
        String data = options.get("data");
        if (data == null) {
            throw new IllegalArgumentException("Parâmetro obrigatório ausente: --data <caminho do Base.csv>");
        }
        return new ExperimentConfig(
                Path.of(data),
                Path.of(options.getOrDefault("out", "results")),
                parseSeeds(options.getOrDefault("seeds", "1,2,3,4,5")),
                parseInt(options, "warmup-months", 3),
                parseInt(options, "warmup-epochs", 3),
                parseDouble(options, "negative-rate", 0.10),
                parseDouble(options, "target-fpr", 0.05),
                parseDouble(options, "protected-age", 50),
                options.getOrDefault("label", "fraud_bool"),
                options.getOrDefault("month", "month"),
                options.getOrDefault("age", "customer_age"),
                Boolean.parseBoolean(options.getOrDefault("exclude-protected", "false")),
                parseInt(options, "periodic-window", 3),
                parseInt(options, "drift-buffer", 20_000),
                parseDouble(options, "lr", 0.05),
                parseDouble(options, "l2", 1e-5),
                parseDouble(options, "adwin-delta", 0.002),
                parseDouble(options, "ph-delta", 0.005),
                parseDouble(options, "ph-lambda", 50),
                parseStrategies(options.getOrDefault("strategies", String.join(",", CORE_STRATEGIES))),
                parseInt(options, "max-rows", 0));
    }

    /** Representação legível para gravar junto com os resultados. */
    public Map<String, String> asMap() {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("data", dataPath.toString());
        map.put("seeds", seeds.toString());
        map.put("warmupMonths", String.valueOf(warmupMonths));
        map.put("warmupEpochs", String.valueOf(warmupEpochs));
        map.put("negativeSampleRate", String.valueOf(negativeSampleRate));
        map.put("targetFpr", String.valueOf(targetFpr));
        map.put("protectedAgeThreshold", String.valueOf(protectedAgeThreshold));
        map.put("excludeProtected", String.valueOf(excludeProtected));
        map.put("periodicWindowMonths", String.valueOf(periodicWindowMonths));
        map.put("driftBufferSize", String.valueOf(driftBufferSize));
        map.put("learningRate", String.valueOf(learningRate));
        map.put("l2", String.valueOf(l2));
        map.put("adwinDelta", String.valueOf(adwinDelta));
        map.put("phDelta", String.valueOf(phDelta));
        map.put("phLambda", String.valueOf(phLambda));
        map.put("strategies", String.valueOf(strategies));
        map.put("maxRows", String.valueOf(maxRows));
        return map;
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("Argumento inesperado: " + key + " (use --chave valor)");
            }
            if (i + 1 >= args.length) {
                throw new IllegalArgumentException("Falta valor para " + key);
            }
            options.put(key.substring(2), args[++i]);
        }
        return options;
    }

    private static List<Long> parseSeeds(String raw) {
        List<Long> seeds = new ArrayList<>();
        for (String token : raw.split(",")) {
            if (!token.isBlank()) {
                seeds.add(Long.parseLong(token.trim()));
            }
        }
        return seeds;
    }

    private static List<String> parseStrategies(String raw) {
        List<String> names = new ArrayList<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toUpperCase)
                .forEach(names::add);
        return names;
    }

    private static int parseInt(Map<String, String> options, String key, int fallback) {
        String value = options.get(key);
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inteiro inválido para --" + key + ": " + value, e);
        }
    }

    private static double parseDouble(Map<String, String> options, String key, double fallback) {
        String value = options.get(key);
        try {
            return value == null ? fallback : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor numérico inválido para --" + key + ": " + value, e);
        }
    }

    private static void requireThat(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
