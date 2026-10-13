package com.medassist.knowledge.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Section-aware chunking: paragraphs of the same section are packed up to the configured size; long paragraphs are
 * split at sentence (then word) boundaries; consecutive chunks of a section overlap by a sentence-aligned tail.
 * Chunks never span two sections, so each chunk carries exactly one section heading.
 */
@Service
public class ChunkingService {

    private final ChunkingProperties properties;

    public ChunkingService(ChunkingProperties properties) {
        this.properties = properties;
    }

    public ChunkingProperties properties() {
        return properties;
    }

    public List<TextChunk> chunk(List<TextBlock> blocks) {
        List<TextChunk> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String currentSection = null;
        Integer currentPage = null;
        boolean first = true;
        for (TextBlock block : blocks) {
            if (!first && !Objects.equals(block.section(), currentSection)) {
                flush(chunks, current, currentSection, currentPage);
                current.setLength(0);
                currentPage = null;
            }
            first = false;
            currentSection = block.section();
            for (String piece : pieces(block.text())) {
                if (current.length() > 0 && current.length() + piece.length() + 1 > properties.maxChars()) {
                    String overlap = tail(current.toString());
                    flush(chunks, current, currentSection, currentPage);
                    current.setLength(0);
                    current.append(overlap);
                    currentPage = block.pageNumber();
                }
                if (currentPage == null) {
                    currentPage = block.pageNumber();
                }
                if (current.length() > 0) {
                    current.append(current.charAt(current.length() - 1) == '\n' ? "" : "\n");
                }
                current.append(piece);
            }
        }
        flush(chunks, current, currentSection, currentPage);
        return mergeTinyChunks(chunks);
    }

    /** Splits text into pieces no longer than maxChars, preferring sentence then word boundaries. */
    List<String> pieces(String text) {
        if (text.length() <= properties.maxChars()) {
            return List.of(text);
        }
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String sentence : text.split("(?<=[.!?;])\\s+")) {
            if (sentence.length() > properties.maxChars()) {
                if (buffer.length() > 0) {
                    result.add(buffer.toString());
                    buffer.setLength(0);
                }
                result.addAll(splitWords(sentence));
                continue;
            }
            if (buffer.length() + sentence.length() + 1 > properties.maxChars()) {
                result.add(buffer.toString());
                buffer.setLength(0);
            }
            if (buffer.length() > 0) {
                buffer.append(' ');
            }
            buffer.append(sentence);
        }
        if (buffer.length() > 0) {
            result.add(buffer.toString());
        }
        return result;
    }

    private List<String> splitWords(String sentence) {
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String word : sentence.split("\\s+")) {
            if (buffer.length() + word.length() + 1 > properties.maxChars() && buffer.length() > 0) {
                result.add(buffer.toString());
                buffer.setLength(0);
            }
            if (buffer.length() > 0) {
                buffer.append(' ');
            }
            buffer.append(word.length() > properties.maxChars() ? word.substring(0, properties.maxChars()) : word);
        }
        if (buffer.length() > 0) {
            result.add(buffer.toString());
        }
        return result;
    }

    /** Sentence-aligned tail of a chunk used as overlap for the next chunk of the same section. */
    String tail(String text) {
        if (properties.overlapChars() == 0 || text.length() <= properties.overlapChars()) {
            return "";
        }
        String candidate = text.substring(text.length() - properties.overlapChars());
        int sentenceStart = candidate.indexOf(". ");
        if (sentenceStart >= 0 && sentenceStart + 2 < candidate.length()) {
            return candidate.substring(sentenceStart + 2).strip();
        }
        int space = candidate.indexOf(' ');
        return space >= 0 ? candidate.substring(space + 1).strip() : "";
    }

    private void flush(List<TextChunk> chunks, StringBuilder current, String section, Integer page) {
        String content = current.toString().strip();
        if (!content.isEmpty()) {
            chunks.add(new TextChunk(chunks.size(), section, page, content));
        }
    }

    private List<TextChunk> mergeTinyChunks(List<TextChunk> chunks) {
        List<TextChunk> merged = new ArrayList<>();
        for (TextChunk chunk : chunks) {
            if (!merged.isEmpty() && chunk.content().length() < properties.minChars()) {
                TextChunk previous = merged.getLast();
                if (Objects.equals(previous.section(), chunk.section())
                        && previous.content().length() + chunk.content().length() < properties.maxChars() * 1.25) {
                    merged.set(merged.size() - 1, new TextChunk(previous.index(), previous.section(),
                            previous.pageNumber(), previous.content() + "\n" + chunk.content()));
                    continue;
                }
            }
            merged.add(new TextChunk(merged.size(), chunk.section(), chunk.pageNumber(), chunk.content()));
        }
        return merged;
    }
}
