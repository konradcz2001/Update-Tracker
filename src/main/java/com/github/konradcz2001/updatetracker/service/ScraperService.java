package com.github.konradcz2001.updatetracker.service;

import com.github.konradcz2001.updatetracker.TrackedProgram;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;

/**
 * Service for fetching web content using Jsoup.
 * Used for static websites where JavaScript execution is not required.
 */
public class ScraperService {

    /**
     * Connects to the URL and extracts text matching the CSS selector.
     */
    public String fetchText(TrackedProgram program) throws IOException {
        Document doc = fetchDocument(program.getUrl());
        return extractTextFromDocument(doc, program.getCssSelector());
    }

    /**
     * Fetches the DOM with standard browser-like user agent headers.
     * Protected to allow mocking in tests.
     */
    protected Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(15000)
                .get();
    }

    public String extractTextFromDocument(Document doc, String cssSelector) {
        String fullText;
        if (cssSelector != null && !cssSelector.isEmpty()) {
            Elements elements = doc.select(cssSelector);
            if (!elements.isEmpty()) {
                fullText = elements.first().text();
            } else {
                fullText = "";
            }
        } else {
            fullText = doc.body().text();
        }
        return fullText.replace('\u00A0', ' ').trim();
    }
}