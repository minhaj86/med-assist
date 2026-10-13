package com.medassist.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.medassist.knowledge.document.ExtractedDocument;
import com.medassist.knowledge.document.PdfTextExtractor;
import com.medassist.support.TestPdfs;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTest {

    @Test
    void extractsTextPerPage() throws Exception {
        byte[] pdf = TestPdfs.twoPagePdf("SYNTHETIC PAGE ONE about chest pain", "Second page about troponin testing");
        ExtractedDocument extracted = new PdfTextExtractor().extract(pdf);
        assertThat(extracted.pages()).hasSize(2);
        assertThat(extracted.pages().get(0).pageNumber()).isEqualTo(1);
        assertThat(extracted.pages().get(0).text()).contains("SYNTHETIC PAGE ONE");
        assertThat(extracted.pages().get(1).text()).contains("troponin");
        assertThat(extracted.pageCount()).isEqualTo(2);
    }
}
