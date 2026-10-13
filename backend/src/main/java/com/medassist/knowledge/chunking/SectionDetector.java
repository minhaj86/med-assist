package com.medassist.knowledge.chunking;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Splits cleaned page text into paragraphs and assigns each to the most recent heading. Recognizes Markdown
 * headings, numbered headings ("2.1 Initial assessment") and short upper-case heading lines.
 */
@Component
public class SectionDetector {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.{1,200})$");
    private static final Pattern NUMBERED_HEADING = Pattern.compile("^(\\d+(?:\\.\\d+){0,3})\\.?\\s+(\\p{Lu}[^.!?]{1,100})$");
    private static final Pattern UPPERCASE_HEADING = Pattern.compile("^[\\p{Lu}][\\p{Lu}0-9 ,/&()'-]{3,80}$");

    public record PageText(Integer pageNumber, String text) {
    }

    public List<TextBlock> detect(List<PageText> pages) {
        List<TextBlock> blocks = new ArrayList<>();
        String section = null;
        for (PageText page : pages) {
            for (String paragraph : page.text().split("\\n\\s*\\n|\\n(?=#{1,6}\\s)")) {
                String trimmed = paragraph.strip();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String[] lines = trimmed.split("\\n");
                String heading = headingOf(lines[0].strip());
                if (heading != null) {
                    section = heading;
                    String rest = String.join("\n", java.util.Arrays.copyOfRange(lines, 1, lines.length)).strip();
                    if (!rest.isEmpty()) {
                        blocks.add(new TextBlock(section, page.pageNumber(), rest));
                    }
                } else {
                    blocks.add(new TextBlock(section, page.pageNumber(), trimmed));
                }
            }
        }
        return blocks;
    }

    /** True when a line looks like a heading (used by the cleaner to avoid joining it with the next line). */
    public static boolean looksLikeHeading(String line) {
        return headingOf(line) != null;
    }

    static String headingOf(String line) {
        Matcher md = MARKDOWN_HEADING.matcher(line);
        if (md.matches()) {
            return md.group(1).strip();
        }
        Matcher numbered = NUMBERED_HEADING.matcher(line);
        if (numbered.matches() && line.length() <= 100) {
            return line;
        }
        if (UPPERCASE_HEADING.matcher(line).matches() && line.split("\\s+").length <= 10) {
            return line;
        }
        return null;
    }
}
