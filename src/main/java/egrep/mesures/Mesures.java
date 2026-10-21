package egrep.mesures;

import egrep.Lignes;
import egrep.Recherche;
import egrep.automate.Determinisation;
import egrep.automate.Dfa;
import egrep.automate.Minimisation;
import egrep.automate.Ndfa;
import egrep.regex.RegexParser;
import egrep.regex.RegexTree;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

/**
 * Banc d'essai : compare KMP, l'automate et egrep sur les textes de
 * instances/, et écrit les résultats en CSV dans le dossier donné. À lancer
 * depuis la racine du projet, après make :
 * <pre>java -cp egrep.jar egrep.mesures.Mesures mesures/avant</pre>
 * Chaque mesure est répétée {@link #REPETITIONS} fois, après
 * {@link #ECHAUFFEMENT} tours à vide, et résumée par sa moyenne et son écart
 * type, en millisecondes.
 */
public final class Mesures {

    private static final int ECHAUFFEMENT = 3;
    private static final int REPETITIONS = 10;

    private static final Path INSTANCES = Path.of("instances");
    private static final Path BINAIRE = Path.of("egrep.jar");
    // la JVM qui exécute le banc d'essai lance aussi le clone
    private static final String JAVA = ProcessHandle.current().info().command().orElse("java");

    /** Mot rare : presque toutes les lignes sont lues jusqu'au bout. */
    private static final String MOT = "Babylon";
    private static final String FIGURE_1 = "S(a|g|r)+on";
    private static final int[] TAILLES_MO = {1, 2, 4, 8, 16, 32};
    /** Motifs de motifs.csv : trois mots plus ou moins fréquents, puis un exemple par opérateur. */
    private static final List<String> MOTIFS = List.of(
            "the", "Holmes", MOT, FIGURE_1, "th(e|is|at)", "w.s", "colou*r",
            "(a|e|i|o|u)(a|e|i|o|u)(a|e|i|o|u)");
    /** Nombre maximal de copies de (a|b) dans etats.csv : le DFA a 2^(k+1) états. */
    private static final int K_MAX = 12;

    // résultat rangé dans un champ volatile : le JIT ne peut pas supprimer un calcul dont rien ne se sert
    @SuppressWarnings("unused")
    private static volatile Object puits;

    private final Path dossier;
    // textes écrits pour les commandes, et leurs sorties
    private final Path temporaire;
    private final List<String> corpus = new ArrayList<>();
    private final Path fichierCorpus;

    /** Moyenne et écart type d'une série de mesures, en millisecondes. */
    record Stats(double moyenne, double ecart) {

        static Stats de(double[] temps) {
            double somme = 0;
            for (double t : temps) {
                somme += t;
            }
            double moyenne = somme / temps.length;
            double carres = 0;
            for (double t : temps) {
                carres += (t - moyenne) * (t - moyenne);
            }
            // écart type d'échantillon, divisé par n - 1
            return new Stats(moyenne, Math.sqrt(carres / (temps.length - 1)));
        }

        String csv() {
            return String.format(Locale.ROOT, "%.4f,%.4f", moyenne, ecart);
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            System.err.println("Usage : java -cp egrep.jar egrep.mesures.Mesures DOSSIER");
            System.exit(2);
        }
        if (!Files.exists(BINAIRE) || !Files.isDirectory(INSTANCES)) {
            System.err.println("A lancer depuis la racine du projet, apres make : egrep.jar et instances/ sont necessaires");
            System.exit(2);
        }
        Path temporaire = Files.createTempDirectory("mesures");
        try {
            Mesures mesures = new Mesures(Files.createDirectories(Path.of(args[0])), temporaire);
            mesures.machine();
            mesures.demarrage();
            mesures.taille();
            mesures.motifs();
            mesures.etats();
        } finally {
            try (Stream<Path> fichiers = Files.list(temporaire)) {
                for (Path fichier : fichiers.toList()) {
                    Files.delete(fichier);
                }
            }
            Files.delete(temporaire);
        }
    }

    /** Lit le corpus : les lignes de tous les textes de instances/, par ordre de nom. */
    private Mesures(Path dossier, Path temporaire) throws IOException {
        this.dossier = dossier;
        this.temporaire = temporaire;
        List<Path> textes;
        try (Stream<Path> fichiers = Files.list(INSTANCES)) {
            textes = fichiers.filter(f -> f.toString().endsWith(".txt")).sorted().toList();
        }
        for (Path texte : textes) {
            corpus.addAll(Lignes.lire(texte));
        }
        fichierCorpus = ecrire("corpus.txt", corpus);
    }

    /** machine.txt : les conditions de mesure. */
    private void machine() throws IOException, InterruptedException {
        List<String> infos = List.of(
                "processeur : " + processeur(),
                "coeurs : " + Runtime.getRuntime().availableProcessors(),
                "systeme : " + System.getProperty("os.name") + " " + System.getProperty("os.version"),
                "java : " + Runtime.version(),
                "egrep : " + premiereLigne(List.of("egrep", "--version")),
                "corpus : " + corpus.size() + " lignes, " + Files.size(fichierCorpus) + " octets",
                "mesures : " + ECHAUFFEMENT + " tours a vide, puis " + REPETITIONS + " mesures");
        Files.write(dossier.resolve("machine.txt"), infos);
        infos.forEach(System.out::println);
    }

    /** demarrage.csv : le coût fixe de chaque commande, sur un fichier vide. */
    private void demarrage() throws IOException, InterruptedException {
        Path vide = ecrire("vide.txt", List.of());
        Path sortie = temporaire.resolve("sortie.txt");
        List<String> lignes = new ArrayList<>();
        lignes.add(afficher("clone," + chronometrer(clone(MOT, false, vide), sortie).csv()));
        lignes.add(afficher("egrep," + chronometrer(egrep(MOT, vide), sortie).csv()));
        ecrireCsv("demarrage.csv", "commande,moyenne,ecart", lignes);
    }

    /**
     * taille.csv : temps selon la taille du texte, obtenu en répétant le corpus.
     * Dans le programme, les lignes sont déjà en mémoire ; les commandes, elles,
     * lisent un fichier de la même taille.
     */
    private void taille() throws IOException, InterruptedException {
        Recherche kmp = Recherche.pour(MOT, true);
        Recherche automate = Recherche.pour(MOT, false);
        Recherche figure1 = Recherche.pour(FIGURE_1, false);
        Path sortie = temporaire.resolve("sortie.txt");
        List<String> lignes = new ArrayList<>();
        for (int mo : TAILLES_MO) {
            List<String> texte = repeter(mo * 1_000_000L);
            if (compter(kmp, texte) != compter(automate, texte)) {
                throw new IllegalStateException("KMP et l'automate ne trouvent pas les memes lignes");
            }
            Path fichier = ecrire("taille.txt", texte);
            lignes.add(afficher(mo
                    + "," + chronometrer(() -> compter(kmp, texte)).csv()
                    + "," + chronometrer(() -> compter(automate, texte)).csv()
                    + "," + chronometrer(() -> compter(figure1, texte)).csv()
                    + "," + chronometrer(clone(MOT, false, fichier), sortie).csv()
                    + "," + chronometrer(clone(MOT, true, fichier), sortie).csv()
                    + "," + chronometrer(egrep(MOT, fichier), sortie).csv()));
        }
        ecrireCsv("taille.csv", "taille_mo,kmp_moy,kmp_ecart,automate_moy,automate_ecart,"
                + "figure1_moy,figure1_ecart,clone_kmp_moy,clone_kmp_ecart,"
                + "clone_automate_moy,clone_automate_ecart,egrep_moy,egrep_ecart", lignes);
    }

    /**
     * motifs.csv : pour chaque motif du corpus entier, la construction
     * ({@link Recherche#pour}), la recherche dans le programme, puis les
     * commandes complètes du clone et d'egrep. Un mot a une ligne pour KMP et une
     * pour l'automate. On vérifie au passage que le clone écrit la même chose
     * qu'egrep.
     */
    private void motifs() throws IOException, InterruptedException {
        Path sortieEgrep = temporaire.resolve("egrep.txt");
        Path sortieClone = temporaire.resolve("clone.txt");
        List<String> lignes = new ArrayList<>();
        for (String motif : MOTIFS) {
            Stats egrep = chronometrer(egrep(motif, fichierCorpus), sortieEgrep);
            boolean estUnMot = Recherche.pour(motif, true) instanceof Recherche.ParKmp;
            for (boolean automate : estUnMot ? new boolean[]{false, true} : new boolean[]{true}) {
                Recherche recherche = Recherche.pour(motif, !automate);
                Stats construction = chronometrer(() -> {
                    puits = Recherche.pour(motif, !automate);
                    return 0;
                });
                Stats parcours = chronometrer(() -> compter(recherche, corpus));
                Stats clone = chronometrer(clone(motif, automate, fichierCorpus), sortieClone);
                if (Files.mismatch(sortieClone, sortieEgrep) != -1) {
                    System.err.println("ATTENTION : la sortie du clone differe de celle d'egrep pour " + motif);
                }
                lignes.add(afficher(motif + "," + (automate ? "automate" : "kmp")
                        + "," + compter(recherche, corpus) + "," + construction.csv() + "," + parcours.csv()
                        + "," + clone.csv() + "," + egrep.csv()));
            }
        }
        ecrireCsv("motifs.csv", "motif,strategie,lignes,construction_moy,construction_ecart,"
                + "recherche_moy,recherche_ecart,clone_moy,clone_ecart,egrep_moy,egrep_ecart", lignes);
    }

    /**
     * etats.csv : explosion de la méthode des sous-ensembles. Pour reconnaître
     * .*a(a|b)…(a|b), avec k copies de (a|b), l'automate doit retenir lesquels
     * des k + 1 derniers caractères étaient des a : 2^(k+1) états, que la
     * minimisation ne peut pas fusionner.
     */
    private void etats() throws IOException {
        List<String> lignes = new ArrayList<>();
        for (int k = 1; k <= K_MAX; k++) {
            // comme Recherche.pour : .*R reconnaît les lignes qui contiennent R
            RegexTree facteur = new RegexTree.Concat(new RegexTree.Etoile(new RegexTree.Point()),
                    RegexParser.analyser("a" + "(a|b)".repeat(k)));
            Ndfa ndfa = Ndfa.ahoUllman(facteur);
            Dfa dfa = Determinisation.sousEnsembles(ndfa);
            Dfa minimal = Minimisation.moore(dfa);
            Stats construction = chronometrer(() ->
                    Minimisation.moore(Determinisation.sousEnsembles(Ndfa.ahoUllman(facteur))).nbEtats());
            lignes.add(afficher(k + "," + ndfa.nbEtats() + "," + dfa.nbEtats() + "," + minimal.nbEtats()
                    + "," + construction.csv()));
        }
        ecrireCsv("etats.csv", "k,etats_afn,etats_dfa,etats_min,construction_moy,construction_ecart", lignes);
    }

    /** @return le nombre de lignes qui contiennent une occurrence du motif */
    private static int compter(Recherche recherche, List<String> lignes) {
        int trouvees = 0;
        for (String ligne : lignes) {
            if (recherche.contient(ligne)) {
                trouvees++;
            }
        }
        return trouvees;
    }

    /**
     * Mesure une tâche du programme. Les tours à vide laissent au JIT le temps de
     * compiler le code. La tâche rend un nombre (lignes trouvées, états…) qui doit
     * être le même à chaque tour.
     */
    private static Stats chronometrer(IntSupplier tache) {
        int attendu = tache.getAsInt();
        for (int i = 1; i < ECHAUFFEMENT; i++) {
            verifier(tache.getAsInt(), attendu);
        }
        double[] temps = new double[REPETITIONS];
        for (int i = 0; i < REPETITIONS; i++) {
            long debut = System.nanoTime();
            int resultat = tache.getAsInt();
            temps[i] = (System.nanoTime() - debut) / 1e6;
            verifier(resultat, attendu);
        }
        return Stats.de(temps);
    }

    private static void verifier(int resultat, int attendu) {
        if (resultat != attendu) {
            throw new IllegalStateException("resultat instable : " + resultat + " au lieu de " + attendu);
        }
    }

    /**
     * Mesure une commande complète, démarrage compris, après un tour à vide qui
     * met le texte dans le cache du système. La sortie va dans un fichier :
     * vers /dev/null, GNU grep s'arrêterait à la première ligne trouvée.
     */
    private static Stats chronometrer(List<String> commande, Path sortie) throws IOException, InterruptedException {
        ProcessBuilder processus = new ProcessBuilder(commande)
                .redirectOutput(sortie.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        processus.environment().put("LC_ALL", "C.UTF-8");
        executer(processus);
        double[] temps = new double[REPETITIONS];
        for (int i = 0; i < REPETITIONS; i++) {
            long debut = System.nanoTime();
            executer(processus);
            temps[i] = (System.nanoTime() - debut) / 1e6;
        }
        return Stats.de(temps);
    }

    private static void executer(ProcessBuilder processus) throws IOException, InterruptedException {
        // comme egrep : 0 si une ligne est trouvée, 1 sinon, 2 en cas d'erreur
        if (processus.start().waitFor() > 1) {
            throw new IllegalStateException("echec de " + String.join(" ", processus.command()));
        }
    }

    /** @return la commande du clone ; automate ajoute --dfa, qui interdit KMP */
    private static List<String> clone(String motif, boolean automate, Path texte) {
        List<String> commande = new ArrayList<>(List.of(JAVA, "-jar", BINAIRE.toString()));
        if (automate) {
            commande.add("--dfa");
        }
        commande.add(motif);
        commande.add(texte.toString());
        return commande;
    }

    private static List<String> egrep(String motif, Path texte) {
        return List.of("egrep", motif, texte.toString());
    }

    /**
     * @return des copies des lignes du corpus, répétées jusqu'à faire au moins
     *         @param octets octets. Ce sont de vraies copies : des lignes partagées
     *         tiendraient dans le cache du processeur au-delà de la taille du corpus.
     */
    private List<String> repeter(long octets) {
        List<String> texte = new ArrayList<>();
        long taille = 0;
        for (int i = 0; taille < octets; i = (i + 1) % corpus.size()) {
            String ligne = new String(corpus.get(i).toCharArray());
            texte.add(ligne);
            taille += ligne.getBytes(StandardCharsets.UTF_8).length + 1;
        }
        return texte;
    }

    /** @return le fichier temporaire qui contient les lignes, chacune suivie de '\n' */
    private Path ecrire(String nom, List<String> lignes) throws IOException {
        Path fichier = temporaire.resolve(nom);
        try (BufferedWriter sortie = Files.newBufferedWriter(fichier, StandardCharsets.UTF_8)) {
            for (String ligne : lignes) {
                sortie.write(ligne);
                sortie.write('\n');
            }
        }
        return fichier;
    }

    private void ecrireCsv(String nom, String entete, List<String> lignes) throws IOException {
        List<String> contenu = new ArrayList<>();
        contenu.add(entete);
        contenu.addAll(lignes);
        Files.write(dossier.resolve(nom), contenu);
        System.out.println("-> " + dossier.resolve(nom));
    }

    /** @return la ligne, après l'avoir affichée pour suivre l'avancement */
    private static String afficher(String ligne) {
        System.out.println(ligne);
        return ligne;
    }

    /** @return le modèle de processeur lu dans /proc/cpuinfo, ou "inconnu" hors de Linux */
    private static String processeur() throws IOException {
        Path cpuinfo = Path.of("/proc/cpuinfo");
        if (!Files.isReadable(cpuinfo)) {
            return "inconnu";
        }
        try (Stream<String> lignes = Files.lines(cpuinfo)) {
            return lignes.filter(l -> l.startsWith("model name"))
                    .map(l -> l.substring(l.indexOf(':') + 1).trim())
                    .findFirst().orElse("inconnu");
        }
    }

    private static String premiereLigne(List<String> commande) throws IOException, InterruptedException {
        Process processus = new ProcessBuilder(commande).redirectErrorStream(true).start();
        String sortie = new String(processus.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        processus.waitFor();
        return sortie.lines().findFirst().orElse("inconnu");
    }
}
