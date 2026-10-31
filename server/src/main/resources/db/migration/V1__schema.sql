-- Schéma de la base du moteur de recherche. Les tables sont remplies hors
-- ligne par les modes de précalcul du serveur (corpus, index, graph) ; l'API
-- ne fait que les lire.

-- Livres du corpus. Le degré et les centralités valent 0 tant que le graphe
-- de Jaccard n'a pas été calculé.
CREATE TABLE book (
    id         integer PRIMARY KEY,               -- numéro du livre dans le projet Gutenberg
    title      text NOT NULL,
    author     text,                              -- null si l'auteur est inconnu
    language   text NOT NULL,
    word_count integer NOT NULL,                  -- mots du texte, sans l'en-tête ni le pied de page Gutenberg
    degree     integer NOT NULL DEFAULT 0,        -- nombre de voisins dans le graphe de Jaccard
    closeness  double precision NOT NULL DEFAULT 0,
    pagerank   double precision NOT NULL DEFAULT 0
);

-- Vocabulaire du corpus, numéroté par l'indexation.
CREATE TABLE term (
    id         integer PRIMARY KEY,
    word       text NOT NULL UNIQUE,
    book_count integer NOT NULL                   -- nombre de livres qui contiennent le mot
);

-- Matrice termes-documents creuse : une ligne par mot présent dans un livre,
-- avec son nombre d'occurrences. Rangée par terme, dans l'ordre de la clé
-- primaire, c'est l'index inversé (mot -> livres) ; parcourue par livre, c'est
-- l'index direct (livre -> mots), d'où part le calcul des distances.
CREATE TABLE occurrence (
    term_id   integer NOT NULL REFERENCES term,
    book_id   integer NOT NULL REFERENCES book,
    frequency integer NOT NULL,                   -- nombre d'occurrences du mot dans le livre
    PRIMARY KEY (term_id, book_id)
);

-- Arêtes du graphe de Jaccard : deux livres sont voisins si leur distance est
-- inférieure au seuil choisi. Chaque arête est rangée dans les deux sens, pour
-- lire les voisins d'un livre par la clé primaire.
CREATE TABLE neighbor (
    book_a   integer NOT NULL REFERENCES book,
    book_b   integer NOT NULL REFERENCES book,
    distance double precision NOT NULL,           -- distance de Jaccard pondérée, entre 0 et 1
    PRIMARY KEY (book_a, book_b)
);
