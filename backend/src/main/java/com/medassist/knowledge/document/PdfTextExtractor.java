package com.medassist.knowledge.document;

import com.medassist.common.error.DomainRuleViolationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** Extracts text per page with Apache PDFBox so chunks can cite page numbers. */
@Component
public class PdfTextExtractor implements DocumentTextExtractor {

    static final int MAX_PAGES = 1000;

    @Override
    public boolean supports(DocumentFormat format) {
        return format == DocumentFormat.PDF;
    }

    @Override
    public ExtractedDocument extract(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new DomainRuleViolationException("Encrypted PDF documents are not supported");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount > MAX_PAGES) {
                throw new DomainRuleViolationException("PDF exceeds the maximum of " + MAX_PAGES + " pages");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<ExtractedDocument.Page> pages = new ArrayList<>();
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new ExtractedDocument.Page(page, stripper.getText(document)));
            }
            return new ExtractedDocument(pages);
        } catch (IOException e) {
            throw new DomainRuleViolationException("The PDF could not be read: " + e.getClass().getSimpleName());
        }
    }
}
