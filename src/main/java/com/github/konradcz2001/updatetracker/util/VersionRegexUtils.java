package com.github.konradcz2001.updatetracker.util;

import java.util.regex.Pattern;

/**
 * Utility class for automatically generating and testing Regular Expressions.
 * Used to extract version numbers from arbitrary text content found on web pages.
 */
public class VersionRegexUtils {

    /**
     * Generates a regex that extracts the selected version string from the full text.
     * Strategies:
     * 1. Smart Self-Healing (if the version is continuous).
     * 2. Multi-Part (if the user selected multiple disconnected words).
     */
    public static String createRegexFromSelection(String fullText, String selectedVersion) {
        if (fullText.contains(selectedVersion)) {
            return createSmartSelfHealingRegex(fullText, selectedVersion);
        }
        return createMultiPartRegex(fullText, selectedVersion);
    }

    public static boolean testRegex(String fullText, String regex, String expectedValue) {
        try {
            Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(fullText);
            if (m.find()) {
                String captured;
                if (m.groupCount() >= 1) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i <= m.groupCount(); i++) {
                        String g = m.group(i);
                        if (g != null && !g.trim().isEmpty()) {
                            if (sb.length() > 0) sb.append(" ");
                            sb.append(g.trim());
                        }
                    }
                    captured = sb.toString().trim();
                } else {
                    captured = m.group(0).trim();
                }
                return captured.equalsIgnoreCase(expectedValue.trim());
            }
        } catch (Exception e) { return false; }
        return false;
    }

    /**
     * Creates a regex that anchors to the text immediately preceding the version.
     * It attempts to be "self-healing" by not hardcoding the exact version digits,
     * allowing it to match future versions (e.g., "Version 1.0" -> "Version .*").
     */
    private static String createSmartSelfHealingRegex(String fullText, String selectedVersion) {
        int index = fullText.indexOf(selectedVersion);
        if (index == -1) return "(.*)";

        String prefix = fullText.substring(0, index);
        String suffix = fullText.substring(index + selectedVersion.length());

        String regexPrefix = generateSmartPrefix(prefix);
        String regexSuffix = suffix.isEmpty() ? "(.*)" : "(.*?)" + makeSafeRegex(suffix);

        String candidateRegex = regexPrefix + regexSuffix;

        // Verify validity; if it fails, fallback to strict matching
        if (!testRegex(fullText, candidateRegex, selectedVersion)) {
            regexPrefix = makeSafeRegex(prefix);
        }
        return regexPrefix + regexSuffix;
    }

    private static String createMultiPartRegex(String fullText, String selectedVersion) {
        String[] parts = selectedVersion.split("\\s+");
        if (parts.length < 2) return "(.*)";

        StringBuilder regexBuilder = new StringBuilder();
        String firstPart = parts[0];
        int firstIndex = fullText.indexOf(firstPart);
        if (firstIndex == -1) return "(.*)";

        String prefix = fullText.substring(0, firstIndex);
        regexBuilder.append(generateSmartPrefix(prefix));

        int currentPos = firstIndex;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            regexBuilder.append("(").append(makeSafeRegex(part)).append(")");

            if (i < parts.length - 1) {
                String nextPart = parts[i + 1];
                int nextIndex = fullText.indexOf(nextPart, currentPos + part.length());
                if (nextIndex != -1) {
                    regexBuilder.append(".*?");
                    currentPos = nextIndex;
                } else {
                    regexBuilder.append(".*?");
                }
            }
        }

        String lastPart = parts[parts.length - 1];
        int lastIndex = fullText.lastIndexOf(lastPart);
        if (lastIndex != -1) {
            String suffix = fullText.substring(lastIndex + lastPart.length());
            regexBuilder.append(suffix.isEmpty() ? "(.*)" : makeSafeRegex(suffix));
        } else {
            regexBuilder.append("(.*)");
        }
        return regexBuilder.toString();
    }

    /**
     * Finds the nearest meaningful anchor text before the version.
     * Skips generic prefixes like "Current Version: ".
     */
    private static String generateSmartPrefix(String prefix) {
        int lastDigitIndex = -1;
        for (int i = prefix.length() - 1; i >= 0; i--) {
            if (Character.isDigit(prefix.charAt(i))) {
                lastDigitIndex = i;
                break;
            }
        }
        String anchor = (lastDigitIndex != -1) ? prefix.substring(lastDigitIndex + 1) : prefix;
        return ".*?" + makeSafeRegex(anchor);
    }

    private static String makeSafeRegex(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (Character.isLetterOrDigit(c) || " :.-()[]".indexOf(c) != -1) {
                sb.append(Pattern.quote(String.valueOf(c)));
            } else {
                // Replace special characters (bullets, nbsp, etc.) with a wildcard
                sb.append(".");
            }
        }
        return sb.toString();
    }
}