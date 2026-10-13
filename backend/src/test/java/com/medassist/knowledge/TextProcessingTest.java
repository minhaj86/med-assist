package com.medassist.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.medassist.common.error.DomainRuleViolationException;
import com.medassist.knowledge.chunking.ChunkingProperties;
import com.medassist.knowledge.chunking.ChunkingService;
import com.medassist.knowledge.chunking.SectionDetector;
import com.medassist.knowledge.chunking.TextBlock;
import com.medassist.knowledge.chunking.TextChunk;
import com.medassist.knowledge.chunking.TextCleaner;
import com.medassist.knowledge.document.DocumentFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextProcessingTest {

    private final TextCleaner cleaner = new TextCleaner();
    private final SectionDetector detector = new SectionDetector();

    @Test
    void cleanerJoinsWrappedLinesRepairsHyphenationAndStripsMarkdownInlineSyntax() {
        String raw = "# Heading\nThis is a hyphen-\nated word that\ncontinues here.\n\nSee [the source](https://example.org) and **bold** text.\u0007";
        String cleaned = cleaner.clean(raw, true);
        assertThat(cleaned).contains("# Heading\n")
                .contains("hyphenated word that continues here.")
                .contains("See the source and bold text.")
                .doesNotContain("https://").doesNotContain("\u0007");
    }

    @Test
    void sectionDetectorAssignsParagraphsToHeadings() {
        String text = cleaner.clean("""
                # Overview
                Intro paragraph.

                ## Investigations
                ECG and troponin are discussed.

                2.1 Imaging choices
                Imaging text here.

                RED FLAGS
                Warning text.""", true);
        List<TextBlock> blocks = detector.detect(List.of(new SectionDetector.PageText(3, text)));
        assertThat(blocks).extracting(TextBlock::section)
                .containsExactly("Overview", "Investigations", "2.1 Imaging choices", "RED FLAGS");
        assertThat(blocks).allSatisfy(b -> assertThat(b.pageNumber()).isEqualTo(3));
    }

    @Test
    void chunksRespectSizeSectionsAndOverlap() {
        ChunkingService chunker = new ChunkingService(new ChunkingProperties(300, 60, 20));
        String longParagraph = "Sentence number one is here. ".repeat(40).strip();
        List<TextChunk> chunks = chunker.chunk(List.of(
                new TextBlock("A", 1, longParagraph),
                new TextBlock("B", 2, "Short section B text that stands alone.")));
        assertThat(chunks).allSatisfy(c -> assertThat(c.content().length()).isLessThanOrEqualTo(300 + 60));
        assertThat(chunks.getLast().section()).isEqualTo("B");
        assertThat(chunks.getLast().pageNumber()).isEqualTo(2);
        assertThat(chunks.stream().filter(c -> "A".equals(c.section())).count()).isGreaterThan(2);
        TextChunk second = chunks.get(1);
        assertThat(second.content()).startsWith("Sentence number one is here.");
        assertThat(chunks).extracting(TextChunk::index).containsExactly(
                java.util.stream.IntStream.range(0, chunks.size()).boxed().toArray(Integer[]::new));
    }

    @Test
    void veryLongSentencesAreSplitAtWordBoundaries() {
        ChunkingService chunker = new ChunkingService(new ChunkingProperties(200, 0, 0));
        List<TextChunk> chunks = chunker.chunk(List.of(new TextBlock(null, null, "word ".repeat(200).strip())));
        assertThat(chunks).hasSizeGreaterThan(4).allSatisfy(c -> assertThat(c.content()).doesNotStartWith(" "));
    }

    @Test
    void detectsSupportedFormatsAndRejectsOthers() {
        assertThat(DocumentFormat.detect("guide.PDF", null)).isEqualTo(DocumentFormat.PDF);
        assertThat(DocumentFormat.detect("notes.md", "application/octet-stream")).isEqualTo(DocumentFormat.MARKDOWN);
        assertThat(DocumentFormat.detect("x.txt", null)).isEqualTo(DocumentFormat.PLAIN_TEXT);
        assertThatThrownBy(() -> DocumentFormat.detect("x.docx", "application/msword"))
                .isInstanceOf(DomainRuleViolationException.class);
    }
}
