package com.github.konradcz2001.updatetracker.service;

import com.github.konradcz2001.updatetracker.TrackedProgram;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;

public class ScraperService {

    public String fetchText(TrackedProgram program) throws IOException {
        Document doc = fetchDocument(program.getUrl());
        return extractTextFromDocument(doc, program.getCssSelector());
    }

    // Protected so we can mock/override it if necessary, or just test extraction separately
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