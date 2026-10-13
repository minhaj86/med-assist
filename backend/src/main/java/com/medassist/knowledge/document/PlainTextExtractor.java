package com.medassist.knowledge.document;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;

/** Plain text and Markdown (Markdown structure is interpreted later by the section detector). */
@Component
public class PlainTextExtractor implements DocumentTextExtractor {

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.PLAIN_TEXT || format == DocumentFormat.MARKDOWN;
    }

    @Override
    public ExtractedDocument extract(byte[] content) {
        return new ExtractedDocument(List.of(new ExtractedDocument.Page(null, decode(content))));
    }

    static String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException e) {
            return new String(content, StandardCharsets.ISO_8859_1);
        }
    }
}
