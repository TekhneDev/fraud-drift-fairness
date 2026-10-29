package br.mestrado.frauddrift.metrics;

import java.util.Arrays;

/**
 * Métricas de classificação binária calculadas a partir de scores contínuos.
 *
 * <p>Convenção: um exemplo é previsto como fraude quando {@code score > threshold}
 * (estritamente maior). Retornos {@code NaN} indicam métrica indefinida (ex.: mês sem fraudes).
 */
public final class BinaryMetrics {

    private BinaryMetrics() {
    }

    /** Taxas de um ponto de operação. */
    public record Rates(double tpr, double fpr, long positives, long negatives) {
    }

    /**
     * ROC AUC pelo método dos postos (Mann-Whitney), com empates recebendo posto médio.
     * Interpretação: probabilidade de uma fraude aleatória receber score maior que um
     * legítimo aleatório.
     */
    public static double auc(double[] scores, int[] labels) {
        checkSameLength(scores, labels);
        int n = scores.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(scores[a], scores[b]));
        double positiveRankSum = 0;
        long positives = 0;
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && scores[order[j + 1]] == scores[order[i]]) {
                j++;
            }
            double averageRank = (i + j) / 2.0 + 1.0; // postos começam em 1
            for (int k = i; k <= j; k++) {
                if (labels[order[k]] == 1) {
                    positiveRankSum += averageRank;
                    positives++;
                }
            }
            i = j + 1;
        }
        long negatives = n - positives;
        if (positives == 0 || negatives == 0) {
            return Double.NaN;
        }
        return (positiveRankSum - positives * (positives + 1) / 2.0) / ((double) positives * negatives);
    }

    /**
     * Limiar que produz FPR no máximo igual ao alvo: com k = floor(alvo * legítimos), o limiar é o
     * (k+1)-ésimo maior score entre os legítimos. Com empates, o FPR real pode ficar abaixo do alvo.
     *
     * @return limiar, ou NaN se não houver legítimos
     */
    public static double thresholdAtFpr(double[] scores, int[] labels, double targetFpr) {
        checkSameLength(scores, labels);
        double[] negativeScores = Arrays.stream(indices(labels.length))
                .filter(i -> labels[i] == 0)
                .mapToDouble(i -> scores[i])
                .sorted()
                .toArray();
        int negatives = negativeScores.length;
        if (negatives == 0) {
            return Double.NaN;
        }
        int allowedFalsePositives = (int) Math.floor(targetFpr * negatives);
        if (allowedFalsePositives >= negatives) {
            return Double.NEGATIVE_INFINITY;
        }
        // negativeScores está em ordem crescente; o (k+1)-ésimo maior fica em negatives-1-k
        return negativeScores[negatives - 1 - allowedFalsePositives];
    }

    /**
     * TPR e FPR para um limiar, opcionalmente restritos a um subconjunto (grupo).
     *
     * @param mask se não for null, considera só as posições em que {@code mask[i] == wanted}
     */
    public static Rates rates(double[] scores, int[] labels, double threshold, boolean[] mask, boolean wanted) {
        checkSameLength(scores, labels);
        long tp = 0;
        long fp = 0;
        long positives = 0;
        long negatives = 0;
        for (int i = 0; i < scores.length; i++) {
            if (mask != null && mask[i] != wanted) {
                continue;
            }
            boolean flagged = scores[i] > threshold;
            if (labels[i] == 1) {
                positives++;
                tp += flagged ? 1 : 0;
            } else {
                negatives++;
                fp += flagged ? 1 : 0;
            }
        }
        double tpr = positives == 0 ? Double.NaN : (double) tp / positives;
        double fpr = negatives == 0 ? Double.NaN : (double) fp / negatives;
        return new Rates(tpr, fpr, positives, negatives);
    }

    /** Atalho para todas as linhas. */
    public static Rates rates(double[] scores, int[] labels, double threshold) {
        return rates(scores, labels, threshold, null, true);
    }

    private static int[] indices(int n) {
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        return idx;
    }

    private static void checkSameLength(double[] scores, int[] labels) {
        if (scores.length != labels.length) {
            throw new IllegalArgumentException("scores e labels com tamanhos diferentes");
        }
    }
}
