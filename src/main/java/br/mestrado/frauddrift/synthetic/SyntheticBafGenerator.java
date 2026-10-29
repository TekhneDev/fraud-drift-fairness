package br.mestrado.frauddrift.synthetic;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

/**
 * Gera um CSV SINTÉTICO com as mesmas colunas do BAF (Base.csv) e um drift conhecido.
 *
 * <p>Serve apenas para validar o pipeline de ponta a ponta (testes, depuração, demonstração).
 * NÃO use estes dados como resultado de pesquisa: os números do artigo devem vir do BAF real.
 *
 * <p>Drift injetado a partir de {@code DRIFT_MONTH}: o padrão de fraude migra de
 * "baixa similaridade nome/email + alta velocidade" para "sistema operacional 'other' + limite de
 * crédito alto", e o risco relativo do grupo com 50+ anos aumenta (afeta a justiça).
 *
 * <pre>
 * java -cp target/classes br.mestrado.frauddrift.synthetic.SyntheticBafGenerator synthetic.csv 200000 42
 * </pre>
 */
public final class SyntheticBafGenerator {

    public static final int MONTHS = 8;
    public static final int DRIFT_MONTH = 5;

    static final String HEADER = String.join(",",
            "fraud_bool", "income", "name_email_similarity", "prev_address_months_count",
            "current_address_months_count", "customer_age", "days_since_request", "intended_balcon_amount",
            "payment_type", "zip_count_4w", "velocity_6h", "velocity_24h", "velocity_4w",
            "bank_branch_count_8w", "date_of_birth_distinct_emails_4w", "employment_status",
            "credit_risk_score", "email_is_free", "housing_status", "phone_home_valid", "phone_mobile_valid",
            "bank_months_count", "has_other_cards", "proposed_credit_limit", "foreign_request", "source",
            "session_length_in_minutes", "device_os", "keep_alive_session", "device_distinct_emails_8w",
            "device_fraud_count", "month");

    private static final String[] PAYMENT = {"AA", "AB", "AC", "AD", "AE"};
    private static final String[] EMPLOYMENT = {"CA", "CB", "CC", "CD", "CE", "CF", "CG"};
    private static final String[] HOUSING = {"BA", "BB", "BC", "BD", "BE", "BF", "BG"};
    private static final String[] DEVICE_OS = {"windows", "other", "linux", "macintosh", "x11"};

    private SyntheticBafGenerator() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Uso: SyntheticBafGenerator <saida.csv> [linhas=200000] [seed=42]");
            System.exit(2);
        }
        Path output = Path.of(args[0]);
        int rows = args.length > 1 ? Integer.parseInt(args[1]) : 200_000;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 42L;
        write(output, rows, seed);
        System.out.printf("CSV sintético gravado em %s (%d linhas, drift no mês %d)%n",
                output.toAbsolutePath(), rows, DRIFT_MONTH);
    }

    /**
     * @param output caminho do CSV
     * @param rows   número de linhas
     * @param seed   semente (mesma semente = mesmo arquivo)
     */
    public static void write(Path output, int rows, long seed) throws IOException {
        if (rows < MONTHS * 100) {
            throw new IllegalArgumentException("Use pelo menos " + (MONTHS * 100) + " linhas");
        }
        Random random = new Random(seed);
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
            out.println(HEADER);
            for (int i = 0; i < rows; i++) {
                int month = (int) ((long) i * MONTHS / rows);
                out.println(row(random, month));
            }
        }
    }

    private static String row(Random r, int month) {
        double income = (1 + r.nextInt(9)) / 10.0;
        double nameEmail = r.nextDouble();
        int prevAddress = r.nextDouble() < 0.7 ? -1 : 5 + r.nextInt(300);
        int currentAddress = r.nextInt(400);
        int age = 10 * (2 + Math.min(7, (int) Math.abs(r.nextGaussian() * 1.6 + 1.5)));
        double daysSince = -Math.log(1 - r.nextDouble()) * 1.0;
        double balcon = r.nextDouble() < 0.75 ? -1 : r.nextDouble() * 100;
        String payment = PAYMENT[r.nextInt(PAYMENT.length)];
        int zipCount = 100 + r.nextInt(5000);
        double velocity6h = Math.max(0, 5000 + r.nextGaussian() * 3000);
        double velocity24h = Math.max(0, 4800 + r.nextGaussian() * 1500);
        double velocity4w = Math.max(0, 4900 + r.nextGaussian() * 900);
        int branchCount = r.nextInt(200);
        int dobEmails = r.nextInt(20);
        String employment = EMPLOYMENT[r.nextInt(EMPLOYMENT.length)];
        int creditRisk = -100 + r.nextInt(400);
        int emailFree = r.nextInt(2);
        String housing = HOUSING[r.nextInt(HOUSING.length)];
        int phoneHome = r.nextDouble() < 0.4 ? 1 : 0;
        int phoneMobile = r.nextDouble() < 0.9 ? 1 : 0;
        int bankMonths = r.nextDouble() < 0.25 ? -1 : 1 + r.nextInt(32);
        int otherCards = r.nextDouble() < 0.22 ? 1 : 0;
        int creditLimit = 200 + 100 * r.nextInt(19);
        int foreign = r.nextDouble() < 0.03 ? 1 : 0;
        String source = r.nextDouble() < 0.99 ? "INTERNET" : "TELEAPP";
        double session = Math.max(0, 7 + r.nextGaussian() * 8);
        String deviceOs = DEVICE_OS[r.nextInt(DEVICE_OS.length)];
        int keepAlive = r.nextInt(2);
        int deviceEmails = r.nextDouble() < 0.97 ? 1 : (r.nextBoolean() ? 0 : 2);

        boolean senior = age >= 50;
        double logit;
        if (month < DRIFT_MONTH) {
            logit = -6.8 + 2.4 * (1 - nameEmail) + 1.3 * (velocity6h > 8000 ? 1 : 0)
                    + 0.6 * (senior ? 1 : 0) + 0.8 * emailFree;
        } else {
            logit = -6.6 + 2.3 * ("other".equals(deviceOs) ? 1 : 0) + 1.4 * (creditLimit > 1500 ? 1 : 0)
                    + 1.5 * (senior ? 1 : 0) + 0.3 * (1 - nameEmail);
        }
        int fraud = r.nextDouble() < 1.0 / (1.0 + Math.exp(-logit)) ? 1 : 0;

        return String.join(",",
                String.valueOf(fraud), fmt(income), fmt(nameEmail), String.valueOf(prevAddress),
                String.valueOf(currentAddress), String.valueOf(age), fmt(daysSince), fmt(balcon), payment,
                String.valueOf(zipCount), fmt(velocity6h), fmt(velocity24h), fmt(velocity4w),
                String.valueOf(branchCount), String.valueOf(dobEmails), employment, String.valueOf(creditRisk),
                String.valueOf(emailFree), housing, String.valueOf(phoneHome), String.valueOf(phoneMobile),
                String.valueOf(bankMonths), String.valueOf(otherCards), String.valueOf(creditLimit),
                String.valueOf(foreign), source, fmt(session), deviceOs, String.valueOf(keepAlive),
                String.valueOf(deviceEmails), "0", String.valueOf(month));
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
