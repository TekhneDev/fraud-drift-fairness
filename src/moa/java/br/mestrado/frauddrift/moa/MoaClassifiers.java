package br.mestrado.frauddrift.moa;

import br.mestrado.frauddrift.model.OnlineClassifier;
import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.DenseInstance;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import com.yahoo.labs.samoa.instances.InstancesHeader;
import moa.classifiers.Classifier;
import moa.classifiers.meta.AdaptiveRandomForest;
import moa.classifiers.trees.HoeffdingAdaptiveTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Adaptadores para usar classificadores do MOA como {@link OnlineClassifier}.
 *
 * <p>Compilado apenas com {@code mvn -Pmoa package}. O núcleo encontra esta classe por
 * reflexão (ver {@code StrategyFactory}), então o projeto funciona com ou sem o MOA.
 *
 * <p>Algoritmos: {@code ARF} (Adaptive Random Forest, Gomes et al., 2017, com ADWIN interno) e
 * {@code HAT} (Hoeffding Adaptive Tree, Bifet e Gavaldà, 2009).
 */
public final class MoaClassifiers {

    private MoaClassifiers() {
    }

    /** Chamado por reflexão; a assinatura não deve mudar. */
    public static OnlineClassifier create(String algorithm, int dimension, long seed) {
        return new Adapter(algorithm, dimension, seed);
    }

    private static Classifier newClassifier(String algorithm) {
        return switch (algorithm) {
            case "ARF" -> new AdaptiveRandomForest();
            case "HAT" -> new HoeffdingAdaptiveTree();
            default -> throw new IllegalArgumentException("Algoritmo MOA desconhecido: " + algorithm
                    + " (use MOA_ARF ou MOA_HAT)");
        };
    }

    private static final class Adapter implements OnlineClassifier {

        private final String algorithm;
        private final int dimension;
        private final long seed;
        private final InstancesHeader header;
        private final Classifier classifier;

        Adapter(String algorithm, int dimension, long seed) {
            this.algorithm = algorithm;
            this.dimension = dimension;
            this.seed = seed;
            this.header = buildHeader(dimension);
            this.classifier = newClassifier(algorithm);
            this.classifier.setRandomSeed((int) seed);
            this.classifier.setModelContext(header);
            this.classifier.prepareForUse();
        }

        @Override
        public double predictProbability(double[] features) {
            double[] votes = classifier.getVotesForInstance(toInstance(features, 0));
            double total = Arrays.stream(votes).sum();
            if (votes.length < 2 || total <= 0) {
                return 0.5; // modelo ainda sem evidência: score neutro
            }
            return votes[1] / total;
        }

        @Override
        public void learn(double[] features, int label) {
            classifier.trainOnInstance(toInstance(features, label));
        }

        @Override
        public OnlineClassifier newUntrained() {
            return new Adapter(algorithm, dimension, seed);
        }

        @Override
        public String name() {
            return "MOA-" + algorithm;
        }

        private Instance toInstance(double[] features, int label) {
            double[] values = Arrays.copyOf(features, dimension + 1);
            values[dimension] = label;
            Instance instance = new DenseInstance(1.0, values);
            instance.setDataset(header);
            return instance;
        }

        private static InstancesHeader buildHeader(int dimension) {
            List<Attribute> attributes = new ArrayList<>();
            for (int i = 0; i < dimension; i++) {
                attributes.add(new Attribute("f" + i));
            }
            attributes.add(new Attribute("class", List.of("0", "1")));
            Instances dataset = new Instances("baf", attributes, 0);
            dataset.setClassIndex(dimension);
            return new InstancesHeader(dataset);
        }
    }
}
