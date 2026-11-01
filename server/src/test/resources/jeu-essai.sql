-- Jeu d'essai : la mini-bibliothèque du cours 3 (§5.3), pour les tests et
-- pour développer l'API en attendant le vrai corpus.
--
--   D1 = Sargon of Akkad founded an empire. The empire of Sargon fell.
--   D2 = Hammurabi of Babylon wrote laws. Babylon was the city of Hammurabi.
--   D3 = The empire of Akkad and the city of Babylon.
--
-- Les tables sont celles que produirait l'indexation avec notre Tokenizer, qui
-- retire les mots vides (the, of, an, and, was) : elles diffèrent donc un peu
-- de celles du cours, qui ne retire que les mots de moins de 3 lettres.
--
--   D1 : sargon 2, empire 2, akkad 1, founded 1, fell 1          |D1| = 7
--   D2 : hammurabi 2, babylon 2, wrote 1, laws 1, city 1         |D2| = 7
--   D3 : empire 1, akkad 1, city 1, babylon 1                    |D3| = 4
--
-- ATTENTION : le script vide les tables. Il ne faut le charger que dans une
-- base de test, ou dans une base de développement sans le vrai corpus :
--   docker compose exec -T db psql -U engine -d engine < server/src/test/resources/jeu-essai.sql

TRUNCATE neighbor, occurrence, term, book;

-- Distances de Jaccard pondérées : d = 1 - Σmin / Σmax, avec Σmax = |A| + |B| - Σmin.
--   d(D1, D3) : Σmin = akkad 1 + empire 1 = 2, Σmax = 7 + 4 - 2 = 9, d = 7/9 ≈ 0,778
--   d(D2, D3) : Σmin = babylon 1 + city 1 = 2, Σmax = 9,             d = 7/9
--   d(D1, D2) : aucun mot commun,                                     d = 1
-- Avec un seuil θ = 0,8, le graphe est le chemin D1 - D3 - D2.
--
-- Centralités de ce chemin (calculées, pas inventées) :
--   closeness = (n - 1) / Σ distances : D3 = 2/2 = 1, D1 = D2 = 2/3
--   PageRank, α = 0,85 : le centre c et les feuilles f vérifient
--     c = 0,05 + 0,85 · 2f  et  f = 0,05 + 0,85 · c/2,  d'où c = 18/37 ≈ 0,486 et f = 19/74 ≈ 0,257
--   (les deux arêtes ont le même poids, le PageRank pondéré est donc le même)
-- D1 et D2 ont les mêmes centralités : avec tri=closeness ou tri=pagerank,
-- c'est le nombre d'occurrences qui les départage.
INSERT INTO book (id, title, author, language, word_count, degree, closeness, pagerank) VALUES
    (1, 'D1 : Sargon of Akkad',    'Auteur un',    'en', 11, 1, 2.0 / 3, 19.0 / 74),
    (2, 'D2 : Hammurabi of Babylon', NULL,         'en', 11, 1, 2.0 / 3, 19.0 / 74),
    (3, 'D3 : Akkad and Babylon',  'Auteur trois', 'en',  9, 2, 1,       18.0 / 37);

-- Termes numérotés par ordre alphabétique ; book_count = nombre de livres qui les contiennent.
INSERT INTO term (id, word, book_count) VALUES
    (1, 'akkad', 2),
    (2, 'babylon', 2),
    (3, 'city', 2),
    (4, 'empire', 2),
    (5, 'fell', 1),
    (6, 'founded', 1),
    (7, 'hammurabi', 1),
    (8, 'laws', 1),
    (9, 'sargon', 1),
    (10, 'wrote', 1);

INSERT INTO occurrence (term_id, book_id, frequency) VALUES
    (1, 1, 1), (4, 1, 2), (5, 1, 1), (6, 1, 1), (9, 1, 2),     -- D1
    (2, 2, 2), (3, 2, 1), (7, 2, 2), (8, 2, 1), (10, 2, 1),    -- D2
    (1, 3, 1), (2, 3, 1), (3, 3, 1), (4, 3, 1);                -- D3

-- Chaque arête est rangée dans les deux sens (voir V1__schema.sql).
INSERT INTO neighbor (book_a, book_b, distance) VALUES
    (1, 3, 7.0 / 9), (3, 1, 7.0 / 9),
    (2, 3, 7.0 / 9), (3, 2, 7.0 / 9);
