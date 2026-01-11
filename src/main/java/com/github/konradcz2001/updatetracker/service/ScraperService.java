package com.github.konradcz2001.updatetracker.service;

import com.github.konradcz2001.updatetracker.TrackedProgram;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;

public class ScraperService {

    public String fetchText(TrackedProgram program) throws IOException {
        Document doc = Jsoup.connect(program.getUrl())
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(15000)
                .get();

        String fullText;
        if (program.getCssSelector() != null && !program.getCssSelector().isEmpty()) {
            Elements elements = doc.select(program.getCssSelector());
            if (!elements.isEmpty()) {
                fullText = elements.first().text().replace('\u00A0', ' ').trim();
            } else {
                // Selector didn't match anything
                fullText = "";
            }
        } else {
            fullText = doc.body().text().replace('\u00A0', ' ').trim();
        }
        return fullText;
    }
}