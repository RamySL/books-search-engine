package engine.corpus;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GutenbergTextTest {

    // fichier tel que Gutenberg le sert : BOM, fins de ligne Windows, licence en pied de page
    private static final String RAW = String.join("\r\n",
            "﻿The Project Gutenberg eBook of Test",
            "Title: Test",
            "Author: Someone",
            "",
            "*** START OF THE PROJECT GUTENBERG EBOOK TEST ***",
            "",
            "",
            "    TEST",
            "  by Someone",
            "",
            "Chapter 1. The end.",
            "",
            "*** END OF THE PROJECT GUTENBERG EBOOK TEST ***",
            "",
            "Section 1. General Terms of Use and Redistributing Project Gutenberg-tm",
            "");

    @Test
    void keepsOnlyTheTextBetweenTheMarkers() {
        assertEquals(Optional.of("    TEST\n  by Someone\n\nChapter 1. The end.\n"), GutenbergText.body(RAW));
    }

    @Test
    void recognizesOlderMarkers() {
        String raw = "header\n*** START OF THIS PROJECT GUTENBERG EBOOK TEST ***\ntext\n"
                + "*** END OF THIS PROJECT GUTENBERG EBOOK TEST ***\nlicense\n";
        assertEquals(Optional.of("text\n"), GutenbergText.body(raw));
    }

    @Test
    void missingMarkerGivesNothing() {
        assertEquals(Optional.empty(), GutenbergText.body("a text without markers"));
        assertEquals(Optional.empty(), GutenbergText.body("*** START OF THE PROJECT GUTENBERG EBOOK TEST ***\ntext"));
    }
}
