package br.mestrado.frauddrift.model;

/**
 * Regressão logística treinada online com AdaGrad e regularização L2.
 *
 * <p>Por que este modelo: é transparente (cada peso tem interpretação), aprende um exemplo por
 * vez e é sensível a mudanças na distribuição, o que deixa o efeito do drift visível. AdaGrad
 * ajusta a taxa de aprendizado por feature, o que ajuda com features raras (como one-hot).
 */
public final class OnlineLogisticRegression implements OnlineClassifier {

    private static final double EPSILON = 1e-8;
    private static final double MAX_LOGIT = 35.0;

    private final int dimension;
    private final double learningRate;
    private final double l2;
    private final double[] weights;
    private final double[] squaredGradients;
    private double bias;
    private double biasSquaredGradient;

    /**
     * @param dimension    tamanho do vetor de features
     * @param learningRate taxa de aprendizado base do AdaGrad
     * @param l2           força da regularização L2
     */
    public OnlineLogisticRegression(int dimension, double learningRate, double l2) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension deve ser > 0");
        }
        this.dimension = dimension;
        this.learningRate = learningRate;
        this.l2 = l2;
        this.weights = new double[dimension];
        this.squaredGradients = new double[dimension];
    }

    @Override
    public double predictProbability(double[] features) {
        return sigmoid(logit(features));
    }

    @Override
    public void learn(double[] features, int label) {
        checkDimension(features);
        double error = predictProbability(features) - label; // derivada da log-loss em relação ao logit
        for (int i = 0; i < dimension; i++) {
            double xi = features[i];
            if (xi == 0.0 && weights[i] == 0.0) {
                continue; // nada a atualizar; acelera bastante com one-hot esparso
            }
            double gradient = error * xi + l2 * weights[i];
            squaredGradients[i] += gradient * gradient;
            weights[i] -= learningRate * gradient / (Math.sqrt(squaredGradients[i]) + EPSILON);
        }
        biasSquaredGradient += error * error;
        bias -= learningRate * error / (Math.sqrt(biasSquaredGradient) + EPSILON);
    }

    @Override
    public OnlineClassifier newUntrained() {
        return new OnlineLogisticRegression(dimension, learningRate, l2);
    }

    @Override
    public String name() {
        return "LogReg-AdaGrad";
    }

    /** Cópia dos pesos, para análise de importância das features no artigo. */
    public double[] weights() {
        return weights.clone();
    }

    private double logit(double[] features) {
        checkDimension(features);
        double z = bias;
        for (int i = 0; i < dimension; i++) {
            z += weights[i] * features[i];
        }
        return Math.max(-MAX_LOGIT, Math.min(MAX_LOGIT, z));
    }

    private void checkDimension(double[] features) {
        if (features.length != dimension) {
            throw new IllegalArgumentException("Vetor com " + features.length + " features; esperado " + dimension);
        }
    }

    private static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }
}
