package engine.corpus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Téléchargements depuis un miroir de Project Gutenberg : une requête à la
 * fois, avec une pause entre deux requêtes pour ménager le serveur.
 */
final class Mirror implements AutoCloseable {

    private static final Duration PAUSE = Duration.ofSeconds(2);

    /** Durée maximale d'un échange, texte compris : de quoi recevoir un gros livre au débit du miroir. */
    private static final Duration EXCHANGE_TIMEOUT = Duration.ofMinutes(5);

    private static final int ATTEMPTS = 3;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final String base;
    private final Duration pause;
    private final Duration exchangeTimeout;

    /** Fin de la dernière requête (System.nanoTime), 0 avant la première. */
    private long lastRequest;

    Mirror(URI base) {
        this(base, PAUSE, EXCHANGE_TIMEOUT);
    }

    /** Durées réglables pour les tests. */
    Mirror(URI base, Duration pause, Duration exchangeTimeout) {
        this.base = base.toString().replaceAll("/+$", "");
        this.pause = pause;
        this.exchangeTimeout = exchangeTimeout;
    }

    /** @return le fichier texte du livre, vide si le miroir ne l'a pas */
    Optional<String> book(int id) throws IOException, InterruptedException {
        // version texte en UTF-8, que Gutenberg régénère pour chaque livre
        HttpResponse<String> response = get("/cache/epub/" + id + "/pg" + id + ".txt",
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.statusCode() == 404 ? Optional.empty() : Optional.of(response.body());
    }

    /** Télécharge le catalogue compressé ; le fichier n'apparaît qu'une fois complet. */
    void catalog(Path target) throws IOException, InterruptedException {
        Path part = target.resolveSibling(target.getFileName() + ".part");
        // un nouvel essai écrase ce qu'un essai interrompu a laissé
        HttpResponse<Path> response = get("/cache/epub/feeds/pg_catalog.csv.gz", HttpResponse.BodyHandlers.ofFile(part,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(part);
            throw new IOException("Catalogue introuvable sur le miroir : " + response.uri());
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** @return la réponse 200 ou 404 ; une erreur réseau ou serveur, ou un délai dépassé, est retentée */
    private <T> HttpResponse<T> get(String path, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .header("User-Agent", "daar-search-engine")
                .build();
        IOException failure = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            // après un échec, la pause s'allonge
            respectPause(pause.multipliedBy(attempt));
            try {
                HttpResponse<T> response = exchange(request, handler);
                if (response.statusCode() == 200 || response.statusCode() == 404) {
                    return response;
                }
                failure = new IOException("HTTP " + response.statusCode() + " sur " + request.uri());
            } catch (IOException e) {
                failure = e;
            } finally {
                lastRequest = System.nanoTime();
            }
        }
        throw failure;
    }

    /**
     * Un échange complet, borné dans le temps. Le délai d'une requête HTTP ne
     * couvre que l'attente du début de la réponse : sans cette borne, une
     * connexion coupée en plein transfert ferait attendre indéfiniment.
     */
    private <T> HttpResponse<T> exchange(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        CompletableFuture<HttpResponse<T>> response = http.sendAsync(request, handler);
        try {
            return response.get(exchangeTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // abandonne l'échange et sa connexion
            response.cancel(true);
            throw new HttpTimeoutException("Délai dépassé sur " + request.uri());
        } catch (ExecutionException e) {
            throw e.getCause() instanceof IOException cause ? cause : new IOException(e.getCause());
        } catch (InterruptedException e) {
            response.cancel(true);
            throw e;
        }
    }

    private void respectPause(Duration gap) throws InterruptedException {
        long wait = lastRequest + gap.toNanos() - System.nanoTime();
        if (lastRequest != 0 && wait > 0) {
            Thread.sleep(Duration.ofNanos(wait));
        }
    }

    @Override
    public void close() {
        http.close();
    }
}
