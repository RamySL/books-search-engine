package engine.corpus;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

/**
 * Téléchargements depuis un miroir de Project Gutenberg : une requête à la
 * fois, avec une pause entre deux requêtes pour ménager le serveur.
 */
final class Mirror implements AutoCloseable {

    private static final Duration PAUSE = Duration.ofSeconds(2);
    private static final int ATTEMPTS = 3;

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final String base;

    /** Fin de la dernière requête (System.nanoTime), 0 avant la première. */
    private long lastRequest;

    Mirror(URI base) {
        this.base = base.toString().replaceAll("/+$", "");
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
        HttpResponse<Path> response = get("/cache/epub/feeds/pg_catalog.csv.gz",
                HttpResponse.BodyHandlers.ofFile(part));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(part);
            throw new IOException("Catalogue introuvable sur le miroir : " + response.uri());
        }
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** @return la réponse 200 ou 404 ; une erreur réseau ou serveur est retentée */
    private <T> HttpResponse<T> get(String path, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "daar-search-engine")
                .build();
        IOException failure = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            // après un échec, la pause s'allonge
            pause(PAUSE.multipliedBy(attempt));
            try {
                HttpResponse<T> response = http.send(request, handler);
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

    private void pause(Duration gap) throws InterruptedException {
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
