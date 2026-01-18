package com.github.konradcz2001.updatetracker.service;

import javafx.concurrent.Task;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ResourceBundle;

/**
 * Service responsible for handling file downloads.
 * Provides logic to resolve dynamic URLs, suggest filenames, and perform the download task.
 */
public class DownloadService {

    /**
     * Creates a background task to download a file from the specified URL to a local destination.
     * This handles the IO stream operations.
     *
     * @param urlString The direct URL to the file.
     * @param destFile  The destination file on the local disk.
     * @param resources The resource bundle for localized messages.
     * @return A JavaFX Task that performs the download.
     */
    public Task<Void> createDownloadTask(String urlString, File destFile, ResourceBundle resources) {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage(resources.getString("dialog.download.status"));
                try (InputStream in = URI.create(urlString).toURL().openStream()) {
                    Files.copy(in, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return null;
            }
        };
    }

    /**
     * Suggests a filename based on the URL or falls back to a sanitized program name.
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
     * Resolves a download URL that might contain version placeholders.
     * E.g., "https://example.com/file_v{version}.zip" -> "https://example.com/file_v1.0.zip"
     */
    public String resolveDownloadUrl(String urlTemplate, String version) {
        if (urlTemplate == null || urlTemplate.isEmpty()) {
            return null;
        }
        if (urlTemplate.contains("{version}")) {
            return urlTemplate.replace("{version}", version);
        }
        return urlTemplate;
    }
}