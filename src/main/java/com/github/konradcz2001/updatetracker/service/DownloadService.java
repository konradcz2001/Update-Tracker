package com.github.konradcz2001.updatetracker.service;

import javafx.concurrent.Task;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class DownloadService {

    /**
     * Creates a background task to download a file from the specified URL to a local destination.
     * This handles the IO stream operations.
     *
     * @param urlString The direct URL to the file.
     * @param destFile  The destination file on the local disk.
     * @return A JavaFX Task that performs the download.
     */
    public Task<Void> createDownloadTask(String urlString, File destFile) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Downloading...");
                try (InputStream in = URI.create(urlString).toURL().openStream()) {
                    Files.copy(in, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return null;
            }
        };
    }

    /**
     * Analyzes the URL and program name to propose a safe and valid filename.
     * Extracts filename from URL path or falls back to sanitized program name.
     *
     * @param urlString   The URL to extract the filename from.
     * @param programName The name of the program (used as fallback).
     * @return A sanitized filename string ending with an extension if possible.
     */
    public String suggestFilename(String urlString, String programName) {
        // Try to guess filename from URL
        String proposedName = urlString.substring(urlString.lastIndexOf('/') + 1);

        // Remove query parameters if present
        if (proposedName.contains("?")) {
            proposedName = proposedName.substring(0, proposedName.indexOf("?"));
        }

        // Basic sanitization and fallback if the name is too long or lacks extension
        if (proposedName.length() > 60 || !proposedName.contains(".")) {
            proposedName = programName.replaceAll("[^a-zA-Z0-9.-]", "_") + "_installer.exe";
        }

        return proposedName;
    }

    /**
     * Generates the final download URL based on the template and version.
     * * @param urlTemplate The URL stored in the program (e.g., "https://site.com/app-{version}.exe")
     * @param version The detected version string (e.g., "1.5.0")
     * @return The runnable download link
     */
    public String resolveDownloadUrl(String urlTemplate, String version) {
        if (urlTemplate == null || urlTemplate.isEmpty()) {
            return null;
        }
        // Check if the URL contains the placeholder marker
        if (urlTemplate.contains("{version}")) {
            // Replace marker with the actual version number
            return urlTemplate.replace("{version}", version);
        }
        // If no marker, return the static URL as is
        return urlTemplate;
    }
}