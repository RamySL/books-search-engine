-- Schéma de la base du moteur de recherche. Les tables sont remplies hors
-- ligne par les modes de précalcul du serveur (corpus, index, graphe) ; l'API
-- ne fait que les lire.

-- Livres du corpus. Le degré et les centralités valent 0 tant que le graphe
-- de Jaccard n'a pas été calculé.
CREATE TABLE livre (
    id        integer PRIMARY KEY,                -- numéro du livre dans le projet Gutenberg
    titre     text NOT NULL,
    auteur    text,                               -- null si l'auteur est inconnu
    langue    text NOT NULL,
    nb_mots   integer NOT NULL,                   -- mots du texte, sans l'en-tête ni le pied de page Gutenberg
    degre     integer NOT NULL DEFAULT 0,         -- nombre de voisins dans le graphe de Jaccard
    closeness double precision NOT NULL DEFAULT 0,
    pagerank  double precision NOT NULL DEFAULT 0
);

-- Vocabulaire du corpus, numéroté par l'indexation.
CREATE TABLE terme (
    id        integer PRIMARY KEY,
    mot       text NOT NULL UNIQUE,
    nb_livres integer NOT NULL                    -- nombre de livres qui contiennent le mot
);

-- Matrice termes-documents creuse : une ligne par mot présent dans un livre,
-- avec son nombre d'occurrences. Rangée par terme, dans l'ordre de la clé
-- primaire, c'est l'index inversé (mot -> livres) ; parcourue par livre, c'est
-- l'index direct (livre -> mots), d'où part le calcul des distances.
CREATE TABLE occurrence (
    terme_id integer NOT NULL REFERENCES terme,
    livre_id integer NOT NULL REFERENCES livre,
    nombre   integer NOT NULL,
    PRIMARY KEY (terme_id, livre_id)
);

-- Arêtes du graphe de Jaccard : deux livres sont voisins si leur distance est
-- inférieure au seuil choisi. Chaque arête est rangée dans les deux sens, pour
-- lire les voisins d'un livre par la clé primaire.
CREATE TABLE voisin (
    livre_a  integer NOT NULL REFERENCES livre,
    livre_b  integer NOT NULL REFERENCES livre,
    distance double precision NOT NULL,           -- distance de Jaccard pondérée, entre 0 et 1
    PRIMARY KEY (livre_a, livre_b)
);
