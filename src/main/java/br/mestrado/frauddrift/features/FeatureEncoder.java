package br.mestrado.frauddrift.features;

import br.mestrado.frauddrift.data.ColumnarTable;
import br.mestrado.frauddrift.data.ExperimentData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transforma uma linha da tabela em um vetor numérico para o modelo.
 *
 * <ul>
 *   <li>Numéricas: padronização (z-score) com média e desvio calculados SOMENTE nos meses de
 *       aquecimento, e limitadas a [-10, 10] para que valores extremos não desestabilizem o SGD.</li>
 *   <li>Numéricas com valores negativos no aquecimento ganham um indicador "é negativo": no BAF,
 *       -1 significa "ausente" em várias colunas, e ausência costuma ser informativa em fraude.</li>
 *   <li>Categóricas: one-hot com as categorias vistas no aquecimento. Categoria nova vira vetor
 *       zerado, o que é em si uma forma de drift que o experimento deve sentir.</li>
 * </ul>
 *
 * <p>Ajustar o encoder só no aquecimento evita vazamento de informação do futuro para o passado.
 */
public final class FeatureEncoder {

    private static final double CLIP = 10.0;
    private static final double MIN_STD = 1e-9;

    private final ColumnarTable table;
    private final List<NumericSlot> numericSlots;
    private final List<CategoricalSlot> categoricalSlots;
    private final int dimension;
    private final List<String> featureNames;

    private record NumericSlot(int column, double mean, double std, int valueIndex, int missingIndex) {
    }

    private record CategoricalSlot(int column, int[] codeToIndex) {
    }

    private FeatureEncoder(ColumnarTable table, List<NumericSlot> numericSlots,
                           List<CategoricalSlot> categoricalSlots, List<String> featureNames) {
        this.table = table;
        this.numericSlots = List.copyOf(numericSlots);
        this.categoricalSlots = List.copyOf(categoricalSlots);
        this.featureNames = Collections.unmodifiableList(new ArrayList<>(featureNames));
        this.dimension = featureNames.size();
    }

    /**
     * Ajusta o encoder usando apenas as linhas informadas (os meses de aquecimento).
     *
     * @param data        dados do experimento
     * @param warmupRows  linhas usadas para estatísticas e vocabulário
     * @return encoder ajustado
     * @throws IllegalArgumentException se não houver linhas de aquecimento
     */
    public static FeatureEncoder fit(ExperimentData data, int[] warmupRows) {
        if (warmupRows.length == 0) {
            throw new IllegalArgumentException("Sem linhas de aquecimento para ajustar o encoder");
        }
        ColumnarTable table = data.table();
        List<NumericSlot> numericSlots = new ArrayList<>();
        List<CategoricalSlot> categoricalSlots = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int column : data.featureColumns()) {
            if (table.isNumeric(column)) {
                numericSlots.add(fitNumeric(table, column, warmupRows, names));
            } else {
                categoricalSlots.add(fitCategorical(table, column, warmupRows, names));
            }
        }
        return new FeatureEncoder(table, numericSlots, categoricalSlots, names);
    }

    /** Tamanho do vetor de features. */
    public int dimension() {
        return dimension;
    }

    /** Nome de cada posição do vetor (útil para inspecionar os pesos do modelo no artigo). */
    public List<String> featureNames() {
        return featureNames;
    }

    /**
     * @param row linha da tabela
     * @return vetor de features novo (o chamador pode guardá-lo sem risco de ser sobrescrito)
     */
    public double[] encode(int row) {
        double[] x = new double[dimension];
        for (NumericSlot slot : numericSlots) {
            double raw = table.numeric(slot.column(), row);
            x[slot.valueIndex()] = clip((raw - slot.mean()) / slot.std());
            if (slot.missingIndex() >= 0) {
                x[slot.missingIndex()] = raw < 0 ? 1.0 : 0.0;
            }
        }
        for (CategoricalSlot slot : categoricalSlots) {
            int code = table.categoryCode(slot.column(), row);
            if (code < slot.codeToIndex().length && slot.codeToIndex()[code] >= 0) {
                x[slot.codeToIndex()[code]] = 1.0;
            }
        }
        return x;
    }

    private static NumericSlot fitNumeric(ColumnarTable table, int column, int[] rows, List<String> names) {
        double mean = 0;
        double m2 = 0;
        double min = Double.POSITIVE_INFINITY;
        int n = 0;
        for (int row : rows) {
            double value = table.numeric(column, row);
            n++;
            double delta = value - mean;
            mean += delta / n;
            m2 += delta * (value - mean);
            min = Math.min(min, value);
        }
        double std = Math.max(Math.sqrt(m2 / n), MIN_STD);
        int valueIndex = names.size();
        names.add(table.columnName(column));
        int missingIndex = -1;
        if (min < 0) {
            missingIndex = names.size();
            names.add(table.columnName(column) + "__negativo");
        }
        return new NumericSlot(column, mean, std, valueIndex, missingIndex);
    }

    private static CategoricalSlot fitCategorical(ColumnarTable table, int column, int[] rows, List<String> names) {
        List<String> vocabulary = table.categories(column);
        boolean[] seen = new boolean[vocabulary.size()];
        for (int row : rows) {
            seen[table.categoryCode(column, row)] = true;
        }
        int[] codeToIndex = new int[vocabulary.size()];
        for (int code = 0; code < vocabulary.size(); code++) {
            if (seen[code]) {
                codeToIndex[code] = names.size();
                names.add(table.columnName(column) + "=" + vocabulary.get(code));
            } else {
                codeToIndex[code] = -1;
            }
        }
        return new CategoricalSlot(column, codeToIndex);
    }

    private static double clip(double value) {
        return Math.max(-CLIP, Math.min(CLIP, value));
    }
}
