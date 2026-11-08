package engine.corpus;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Catalogue de Project Gutenberg (pg_catalog.csv), réduit aux textes en
 * anglais : les livres candidats et leurs métadonnées.
 */
final class Catalog {

    /** Seule langue retenue : les mots vides du tokeniseur sont anglais. */
    static final String LANGUAGE = "en";

    /** Rôle d'un contributeur, en fin de nom : [Editor], [Translator]… */
    private static final Pattern ROLE = Pattern.compile("\\s*\\[[^\\]]*\\]$");

    /** Dates en fin de nom : « , 1775-1817 », « , 428? BCE-348? BCE », « , 1957- » */
    private static final Pattern DATES = Pattern.compile(",[^,]*\\d[^,]*$");

    /** @param author null si le catalogue n'indique personne */
    record Entry(int id, String title, String author) {
    }

    private final TreeMap<Integer, Entry> entries;

    private Catalog(TreeMap<Integer, Entry> entries) {
        this.entries = entries;
    }

    static Catalog read(Path gzipFile) throws IOException {
        try (Reader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(gzipFile)), StandardCharsets.UTF_8))) {
            return parse(reader);
        }
    }

    /** Les champs entre guillemets peuvent contenir des virgules et des retours à la ligne. */
    static Catalog parse(Reader csv) throws IOException {
        CSVFormat format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get();
        TreeMap<Integer, Entry> entries = new TreeMap<>();
        try (CSVParser parser = format.parse(csv)) {
            for (CSVRecord record : parser) {
                if (record.get("Type").equals("Text") && record.get("Language").equals(LANGUAGE)) {
                    int id = Integer.parseInt(record.get("Text#"));
                    entries.put(id, new Entry(id, title(record.get("Title")), author(record.get("Authors"))));
                }
            }
        }
        return new Catalog(entries);
    }

    Optional<Entry> get(int id) {
        return Optional.ofNullable(entries.get(id));
    }

    /** @return les numéros des livres, par ordre croissant */
    List<Integer> ids() {
        return List.copyOf(entries.keySet());
    }

    /** Titre principal : la première ligne, sans le sous-titre des lignes suivantes. */
    static String title(String title) {
        return title.lines().findFirst().orElse("").strip().replaceAll("\\s+", " ");
    }

    /**
     * Auteurs, sans leurs dates. Les contributeurs (éditeur, traducteur…)
     * n'apparaissent, avec leur rôle, que si le livre n'a pas d'auteur.
     *
     * @return null si le catalogue n'indique personne
     */
    static String author(String authors) {
        List<String> main = new ArrayList<>();
        List<String> contributors = new ArrayList<>();
        for (String person : authors.split(";")) {
            String name = person.strip();
            if (name.isEmpty()) {
                continue;
            }
            Matcher role = ROLE.matcher(name);
            if (role.find()) {
                contributors.add(withoutDates(name.substring(0, role.start())) + " " + role.group().strip());
            } else {
                main.add(withoutDates(name));
            }
        }
        List<String> shown = main.isEmpty() ? contributors : main;
        return shown.isEmpty() ? null : String.join("; ", shown);
    }

    private static String withoutDates(String name) {
        return DATES.matcher(name).replaceFirst("").strip();
    }
}
