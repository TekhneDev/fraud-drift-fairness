package br.mestrado.frauddrift.strategy;

import br.mestrado.frauddrift.drift.AdwinDetector;
import br.mestrado.frauddrift.drift.DriftDetector;
import br.mestrado.frauddrift.drift.GlobalLossMonitor;
import br.mestrado.frauddrift.drift.PageHinkleyDetector;
import br.mestrado.frauddrift.drift.PerClassLossMonitor;
import br.mestrado.frauddrift.model.OnlineClassifier;

import java.lang.reflect.Method;
import java.util.function.Supplier;

/**
 * Cria estratégias pelo nome usado na linha de comando.
 *
 * <p>Estratégias do MOA ({@code MOA_ARF}, {@code MOA_HAT}) são carregadas por reflexão: assim o
 * núcleo compila e roda sem o MOA, e elas só existem quando o projeto é construído com
 * {@code mvn -Pmoa package}.
 */
public final class StrategyFactory {

    private static final String MOA_FACTORY = "br.mestrado.frauddrift.moa.MoaClassifiers";

    private StrategyFactory() {
    }

    /**
     * @param name    nome da estratégia (ver {@code ExperimentConfig.CORE_STRATEGIES})
     * @param context dependências compartilhadas
     * @return estratégia ainda não inicializada
     * @throws IllegalArgumentException se o nome for desconhecido ou o MOA não estiver no classpath
     */
    public static AdaptationStrategy create(String name, StrategyContext context) {
        return switch (name) {
            case "STATIC" -> new StaticStrategy(context);
            case "PERIODIC" -> new PeriodicRetrainStrategy(context);
            case "INCREMENTAL" -> new IncrementalStrategy(context);
            case "ADWIN_GLOBAL" -> new DriftTriggeredStrategy(name, context, new GlobalLossMonitor(adwin(context)));
            case "PH_GLOBAL" -> new DriftTriggeredStrategy(name, context, new GlobalLossMonitor(pageHinkley(context)));
            case "ADWIN_PER_CLASS" -> new DriftTriggeredStrategy(name, context, new PerClassLossMonitor(adwin(context)));
            case "PH_PER_CLASS" -> new DriftTriggeredStrategy(name, context, new PerClassLossMonitor(pageHinkley(context)));
            default -> {
                if (name.startsWith("MOA_")) {
                    yield moaStrategy(name, context);
                }
                throw new IllegalArgumentException("Estratégia desconhecida: " + name);
            }
        };
    }

    private static DriftDetector adwin(StrategyContext context) {
        return AdwinDetector.withDelta(context.config().adwinDelta());
    }

    private static DriftDetector pageHinkley(StrategyContext context) {
        return PageHinkleyDetector.withDefaults(context.config().phDelta(), context.config().phLambda());
    }

    /** Classificador do MOA com a política incremental (os modelos do MOA já trazem seus próprios detectores). */
    private static AdaptationStrategy moaStrategy(String name, StrategyContext context) {
        String algorithm = name.substring("MOA_".length());
        long seed = context.random().nextLong();
        int dimension = context.encoder().dimension();
        Supplier<OnlineClassifier> factory = () -> createMoaClassifier(algorithm, dimension, seed);
        StrategyContext moaContext = new StrategyContext(
                context.data(), context.encoder(), context.config(), context.random(), factory);
        return new IncrementalStrategy(name, moaContext);
    }

    private static OnlineClassifier createMoaClassifier(String algorithm, int dimension, long seed) {
        try {
            Class<?> factoryClass = Class.forName(MOA_FACTORY);
            Method create = factoryClass.getMethod("create", String.class, int.class, long.class);
            return (OnlineClassifier) create.invoke(null, algorithm, dimension, seed);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Estratégias MOA_* exigem o build com o perfil MOA: mvn -Pmoa package", e);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Falha ao criar classificador MOA '" + algorithm + "'", e);
        }
    }
}
