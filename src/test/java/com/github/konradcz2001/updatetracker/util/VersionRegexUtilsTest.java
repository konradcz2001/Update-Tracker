package com.github.konradcz2001.updatetracker.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VersionRegexUtilsTest {

    @Test
    void testCreateRegexFromSelection_Simple() {
        String fullText = "Current version: 1.0.5 available";
        String selected = "1.0.5";

        String regex = VersionRegexUtils.createRegexFromSelection(fullText, selected);

        Assertions.assertTrue(VersionRegexUtils.testRegex(fullText, regex, selected),
                "Generated regex should match the selected text");
    }

    @Test
    void testCreateRegexFromSelection_WithSpecialChars() {
        String fullText = "Version [2.0-beta] (released)";
        String selected = "2.0-beta";

        String regex = VersionRegexUtils.createRegexFromSelection(fullText, selected);

        Assertions.assertTrue(VersionRegexUtils.testRegex(fullText, regex, selected));
    }

    @Test
    void testMultiPartRegex() {
        String fullText = "Name: AppName Version: 3.5 Build: 400";
        // Simulating user selecting "AppName 3.5" (non-contiguous selection isn't possible in standard browser string,
        // but this tests the multi-part logic if spaces are involved)
        String selected = "AppName Version: 3.5";

        String regex = VersionRegexUtils.createRegexFromSelection(fullText, selected);
        Assertions.assertTrue(VersionRegexUtils.testRegex(fullText, regex, selected));
    }

    @Test
    void testRegexExtraction() {
        String regex = "Version\\s+(.*)";
        String text = "Version 5.0.1";

        Assertions.assertTrue(VersionRegexUtils.testRegex(text, regex, "5.0.1"));
    }

    @Test
    void testRegexFailure() {
        String regex = "Version\\s+(.*)";
        String text = "Build 5.0.1";

        Assertions.assertFalse(VersionRegexUtils.testRegex(text, regex, "5.0.1"));
    }
}