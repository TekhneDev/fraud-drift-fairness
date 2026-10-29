package br.mestrado.frauddrift.drift;

import java.util.ArrayList;
import java.util.List;

/**
 * ADWIN (ADaptive WINdowing), de Bifet e Gavaldà (2007), na versão com histograma exponencial.
 *
 * <p>Intuição: mantém uma janela com os valores recentes. A cada verificação, testa todos os
 * pontos de corte possíveis: se a média da parte antiga e a da parte recente diferem mais do que
 * o acaso explicaria (limite estatístico controlado por {@code delta}), descarta a parte antiga.
 * Assim a janela cresce enquanto o fluxo é estável e encolhe quando ele muda.
 *
 * <p>Para não guardar cada valor, a janela é resumida em "baldes" de tamanho 1, 2, 4, 8...
 * (no máximo {@code maxBucketsPerSize} de cada tamanho), o que dá memória O(log n).
 *
 * <p>Este detector só sinaliza drift quando a média RECENTE é maior que a antiga (a perda
 * piorou). Quedas na perda também encolhem a janela, mas não disparam retreino.
 */
public final class AdwinDetector implements DriftDetector {

    private static final int MIN_SUB_WINDOW = 5;

    private final double delta;
    private final int maxBucketsPerSize;
    private final int checkEvery;

    /** Baldes do mais antigo (índice 0, maiores) para o mais recente (menores). */
    private final List<Bucket> buckets = new ArrayList<>();
    private long width;
    private double total;
    private double totalM2;
    private long sinceLastCheck;

    /** Resumo de um bloco contíguo de valores: quantidade, soma e soma dos quadrados dos desvios. */
    private record Bucket(long size, double sum, double m2) {
        double mean() {
            return sum / size;
        }

        Bucket mergedWith(Bucket other) {
            long n = size + other.size;
            double meanDiff = mean() - other.mean();
            double m2Merged = m2 + other.m2 + meanDiff * meanDiff * size * other.size / n;
            return new Bucket(n, sum + other.sum, m2Merged);
        }
    }

    /**
     * @param delta             confiança do teste (MOA usa 0.002); menor = menos alarmes falsos
     * @param maxBucketsPerSize baldes de cada tamanho antes de fundir (MOA usa 5)
     * @param checkEvery        frequência dos testes de corte, em observações (MOA usa 32)
     */
    public AdwinDetector(double delta, int maxBucketsPerSize, int checkEvery) {
        if (delta <= 0 || delta >= 1 || maxBucketsPerSize < 2 || checkEvery < 1) {
            throw new IllegalArgumentException("Parâmetros inválidos para ADWIN");
        }
        this.delta = delta;
        this.maxBucketsPerSize = maxBucketsPerSize;
        this.checkEvery = checkEvery;
    }

    public static AdwinDetector withDelta(double delta) {
        return new AdwinDetector(delta, 5, 32);
    }

    @Override
    public boolean update(double value) {
        insert(value);
        if (++sinceLastCheck < checkEvery) {
            return false;
        }
        sinceLastCheck = 0;
        return shrinkWhileChanged();
    }

    @Override
    public void reset() {
        buckets.clear();
        width = 0;
        total = 0;
        totalM2 = 0;
        sinceLastCheck = 0;
    }

    @Override
    public DriftDetector newInstance() {
        return new AdwinDetector(delta, maxBucketsPerSize, checkEvery);
    }

    @Override
    public String name() {
        return "ADWIN";
    }

    /** Tamanho atual da janela (útil em testes e diagnósticos). */
    public long width() {
        return width;
    }

    /** Média atual da janela. */
    public double mean() {
        return width == 0 ? 0 : total / width;
    }

    private void insert(double value) {
        // Welford para a variância da janela inteira.
        width++;
        double previousMean = width == 1 ? 0 : (total / (width - 1));
        total += value;
        double newMean = total / width;
        totalM2 += (value - previousMean) * (value - newMean);
        buckets.add(new Bucket(1, value, 0));
        compress();
    }

    /** Funde os dois baldes mais antigos de cada tamanho que exceder o limite. */
    private void compress() {
        long size = 1;
        while (true) {
            int first = firstIndexOfSize(size);
            if (first < 0 || countOfSize(first, size) <= maxBucketsPerSize) {
                return;
            }
            Bucket merged = buckets.get(first).mergedWith(buckets.get(first + 1));
            buckets.set(first, merged);
            buckets.remove(first + 1);
            size *= 2;
        }
    }

    private int firstIndexOfSize(long size) {
        for (int i = 0; i < buckets.size(); i++) {
            if (buckets.get(i).size() == size) {
                return i;
            }
        }
        return -1;
    }

    private int countOfSize(int firstIndex, long size) {
        int count = 0;
        for (int i = firstIndex; i < buckets.size() && buckets.get(i).size() == size; i++) {
            count++;
        }
        return count;
    }

    /**
     * Remove a parte antiga enquanto houver um corte com diferença significativa.
     *
     * @return verdadeiro se algum corte mostrou a média recente MAIOR que a antiga
     */
    private boolean shrinkWhileChanged() {
        boolean degradation = false;
        boolean cut = true;
        while (cut && buckets.size() > 1) {
            cut = false;
            long n0 = 0;
            double sum0 = 0;
            for (int i = 0; i < buckets.size() - 1; i++) {
                n0 += buckets.get(i).size();
                sum0 += buckets.get(i).sum();
                long n1 = width - n0;
                if (n0 < MIN_SUB_WINDOW || n1 < MIN_SUB_WINDOW) {
                    continue;
                }
                double mean0 = sum0 / n0;
                double mean1 = (total - sum0) / n1;
                if (Math.abs(mean0 - mean1) > cutThreshold(n0, n1)) {
                    degradation |= mean1 > mean0;
                    removeOldest();
                    cut = true;
                    break;
                }
            }
        }
        return degradation;
    }

    /** Limite de Hoeffding/Bernstein do ADWIN2, igual ao usado no MOA. */
    private double cutThreshold(long n0, long n1) {
        double variance = totalM2 / width;
        double dd = Math.log(2 * Math.log(width) / delta);
        double m = 1.0 / (n0 - MIN_SUB_WINDOW + 1) + 1.0 / (n1 - MIN_SUB_WINDOW + 1);
        return Math.sqrt(2 * m * variance * dd) + (2.0 / 3.0) * dd * m;
    }

    private void removeOldest() {
        Bucket oldest = buckets.remove(0);
        long remaining = width - oldest.size();
        if (remaining == 0) {
            reset();
            return;
        }
        double remainingSum = total - oldest.sum();
        double meanDiff = oldest.mean() - remainingSum / remaining;
        totalM2 -= oldest.m2() + meanDiff * meanDiff * oldest.size() * remaining / width;
        totalM2 = Math.max(0, totalM2); // protege contra erro de arredondamento
        total = remainingSum;
        width = remaining;
    }
}
