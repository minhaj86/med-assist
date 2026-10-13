package com.medassist.knowledge.chunking;

import java.text.Normalizer;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Normalizes extracted text: Unicode normalization, control characters, hyphenated line breaks, wrapped lines,
 * whitespace. For Markdown, inline syntax is removed while heading lines are preserved for section detection.
 */
@Component
public class TextCleaner {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern HYPHEN_BREAK = Pattern.compile("(\\p{L})-\\n(\\p{Ll})");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\u00A0]+");
    private static final Pattern MANY_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern MD_IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)]\\([^)]*\\)");
    private static final Pattern MD_EMPHASIS = Pattern.compile("(\\*\\*|__|`)");
    private static final Pattern HEADING_LINE = Pattern.compile("^#{1,6}\\s+.*$");

    public String clean(String raw, boolean markdown) {
        if (raw == null) {
            return "";
        }
        String text = Normalizer.normalize(raw, Normalizer.Form.NFKC)
                .replace("\r\n", "\n").replace('\r', '\n');
        text = CONTROL.matcher(text).replaceAll(" ");
        text = HYPHEN_BREAK.matcher(text).replaceAll("$1$2");
        if (markdown) {
            text = MD_IMAGE.matcher(text).replaceAll("$1");
            text = MD_LINK.matcher(text).replaceAll("$1");
            text = MD_EMPHASIS.matcher(text).replaceAll("");
        }
        StringBuilder out = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = SPACES.matcher(lines[i]).replaceAll(" ").strip();
            out.append(line);
            if (i < lines.length - 1) {
                String next = SPACES.matcher(lines[i + 1]).replaceAll(" ").strip();
                boolean paragraphBreak = line.isEmpty() || next.isEmpty() || isStructural(line) || isStructural(next);
                out.append(paragraphBreak ? "\n" : " ");
            }
        }
        return MANY_BLANK_LINES.matcher(out.toString()).replaceAll("\n\n").strip();
    }

    /** Lines that must stay on their own line (headings, list items). */
    private static boolean isStructural(String line) {
        return HEADING_LINE.matcher(line).matches() || line.startsWith("- ") || line.startsWith("* ")
                || line.matches("^\\d+[.)]\\s.*") || SectionDetector.looksLikeHeading(line);
    }
}
