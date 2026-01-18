package com.github.konradcz2001.updatetracker.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ScraperServiceTest {

    private final ScraperService scraperService = new ScraperService();

    @Test
    void testExtractText_WithSelector() {
        String html = """
                <html>
                <body>
                    <div id="content">
                        <span class="version">v2.0.5</span>
                        <div class="other">Ignore me</div>
                    </div>
                </body>
                </html>
                """;
        Document doc = Jsoup.parse(html);

        String result = scraperService.extractTextFromDocument(doc, ".version");
        Assertions.assertEquals("v2.0.5", result);
    }

    @Test
    void testExtractText_NoSelector() {
        String html = "<html><body>Simple text content</body></html>";
        Document doc = Jsoup.parse(html);

        String result = scraperService.extractTextFromDocument(doc, null);
        Assertions.assertEquals("Simple text content", result);
    }

    @Test
    void testExtractText_SelectorNotFound() {
        String html = "<html><body><div class='a'>Hi</div></body></html>";
        Document doc = Jsoup.parse(html);

        String result = scraperService.extractTextFromDocument(doc, "#non-existent");
        Assertions.assertEquals("", result, "Should return empty string if selector matches nothing");
    }
}