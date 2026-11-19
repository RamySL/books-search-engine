package engine.measures;

import engine.MeasuresProperties;
import engine.OfflineStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Étape load : test de charge de l'API d'un serveur déjà lancé, comme le
 * ferait {@code hey} ou k6, mais sans outil à installer. Pour chaque type de
 * requête et chaque niveau de concurrence c, c clients envoient des requêtes
 * en boucle, chacune dès la réponse à la précédente, pendant une durée fixe.
 * Écrit load.csv : débit (requêtes par seconde), latences médiane, p95 et p99.
 * <p>
 * Options : {@code --url=} (http://localhost:8080 par défaut), {@code --seconds=}
 * (10), {@code --levels=} (1,2,4,8,16,32). Le client et le serveur doivent de
 * préférence tourner sur deux machines, sinon ils se partagent les cœurs.
 */
@Component
class LoadTest implements OfflineStep {

    private static final Logger log = LoggerFactory.getLogger(LoadTest.class);

    /** Requêtes tirées au hasard pour chaque type ; la RegEx sur le texte est à part (sémaphore). */
    private static final List<String> KEYWORDS = List.of(
            "babylon", "whale", "love", "king queen", "sea", "napoleon", "garden", "war");
    private static final List<String> REGEXES = List.of(
            "sarg(o|a)n", "bab(y|i)lon", "whal(e|es)", "c.ty", "(k|q)ing", "love.*");

    private final MeasuresProperties measures;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    LoadTest(MeasuresProperties measures) {
        this.measures = measures;
    }

    @Override
    public String name() {
        return "load";
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String url = option(args, "url", "http://localhost:8080");
        int seconds = Integer.parseInt(option(args, "seconds", "10"));
        int[] levels = Arrays.stream(option(args, "levels", "1,2,4,8,16,32").split(","))
                .mapToInt(Integer::parseInt).toArray();

        List<String> lines = new ArrayList<>(List.of("type,clients,requests,errors,throughput_rps,p50_ms,p95_ms,p99_ms"));
        for (String type : List.of("keyword", "index", "book")) {
            for (int clients : levels) {
                lines.add(type + "," + clients + "," + measure(url, type, clients, seconds));
            }
        }
        Path file = measures.dir().resolve("load.csv");
        Files.createDirectories(measures.dir());
        Files.write(file, lines);
        log.info("Mesures écrites dans {}", file.toAbsolutePath());
    }

    private URI randomRequest(String url, String type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return URI.create(switch (type) {
            case "keyword" -> url + "/api/search?q=" + encode(KEYWORDS.get(random.nextInt(KEYWORDS.size())));
            case "index" -> url + "/api/search/regex?q=" + encode(REGEXES.get(random.nextInt(REGEXES.size())));
            // fiche d'un livre du corpus de 1 à 2000 au hasard : les 404 comptent aussi comme réponses
            default -> url + "/api/books/" + (1 + random.nextInt(2000));
        });
    }

    /** @return les colonnes de load.csv à partir de requests, pour un type et un niveau */
    private String measure(String url, String type, int clients, int seconds) throws InterruptedException {
        List<Double> latencies = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger errors = new AtomicInteger();
        long end = System.nanoTime() + seconds * 1_000_000_000L;
        try (ExecutorService pool = Executors.newFixedThreadPool(clients)) {
            for (int i = 0; i < clients; i++) {
                pool.submit(() -> {
                    while (System.nanoTime() < end) {
                        long start = System.nanoTime();
                        try {
                            int status = http.send(HttpRequest.newBuilder(randomRequest(url, type)).build(),
                                    HttpResponse.BodyHandlers.discarding()).statusCode();
                            if (status >= 500) {
                                errors.incrementAndGet();
                            }
                        } catch (IOException e) {
                            errors.incrementAndGet();
                        } catch (InterruptedException e) {
                            return;
                        }
                        latencies.add((System.nanoTime() - start) / 1e6);
                    }
                });
            }
        }
        double[] sorted = latencies.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double throughput = sorted.length / (double) seconds;
        log.info("{} avec {} clients : {} req/s, p50 {} ms, p95 {} ms, {} erreurs", type, clients,
                format(throughput), format(percentile(sorted, 50)), format(percentile(sorted, 95)), errors.get());
        return String.join(",", String.valueOf(sorted.length), String.valueOf(errors.get()), format(throughput),
                format(percentile(sorted, 50)), format(percentile(sorted, 95)), format(percentile(sorted, 99)));
    }

    private static double percentile(double[] sorted, int p) {
        if (sorted.length == 0) {
            return 0;
        }
        return sorted[Math.min(sorted.length - 1, (int) Math.ceil(p / 100.0 * sorted.length) - 1)];
    }

    private static String option(ApplicationArguments args, String name, String fallback) {
        return args.containsOption(name) ? args.getOptionValues(name).getFirst() : fallback;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
