package br.mestrado.frauddrift.eval;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Guarda, por mês, as predições feitas ANTES de cada rótulo ser revelado, junto com o rótulo e
 * o grupo protegido. As métricas são calculadas depois, a partir destes dados.
 */
public final class MonthlyScores {

    private final Map<Integer, MonthBuffer> byMonth = new TreeMap<>();

    /** Predição, rótulo e grupo de um mês, em arrays do mesmo tamanho. */
    public record Month(int month, double[] scores, int[] labels, boolean[] protectedGroup) {
        public int size() {
            return scores.length;
        }
    }

    public void add(int month, double score, int label, boolean protectedGroup) {
        byMonth.computeIfAbsent(month, m -> new MonthBuffer()).add(score, label, protectedGroup);
    }

    /** Meses em ordem crescente, cada um como arrays compactos. */
    public Iterable<Month> months() {
        return byMonth.entrySet().stream()
                .map(e -> e.getValue().toMonth(e.getKey()))
                .toList();
    }

    private static final class MonthBuffer {
        private double[] scores = new double[1024];
        private int[] labels = new int[1024];
        private boolean[] groups = new boolean[1024];
        private int size;

        void add(double score, int label, boolean group) {
            if (size == scores.length) {
                int capacity = size * 2;
                scores = Arrays.copyOf(scores, capacity);
                labels = Arrays.copyOf(labels, capacity);
                groups = Arrays.copyOf(groups, capacity);
            }
            scores[size] = score;
            labels[size] = label;
            groups[size] = group;
            size++;
        }

        Month toMonth(int month) {
            return new Month(month, Arrays.copyOf(scores, size), Arrays.copyOf(labels, size),
                    Arrays.copyOf(groups, size));
        }
    }
}
