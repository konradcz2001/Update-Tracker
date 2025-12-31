package com.github.konradcz2001.updatetracker;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDate;

/**
 * Represents a single software entry tracked by the application.
 */
public class TrackedProgram {
    private final StringProperty name;
    private final StringProperty currentVersion; // The version currently live on website
    private final StringProperty lastDownloadedVersion; // The version user has
    private final StringProperty lastCheckDate;
    private String url; // URL to scrape
    private String cssSelector; // Selector for the version element
    private String versionRegex;

    public TrackedProgram(String name) {
        this.name = new SimpleStringProperty(name);
        this.currentVersion = new SimpleStringProperty("Checking...");
        this.lastDownloadedVersion = new SimpleStringProperty("N/A");
        this.lastCheckDate = new SimpleStringProperty(LocalDate.now().toString());
        this.url = "";
        this.cssSelector = "";
        this.versionRegex = "";
    }

    // Getters for JavaFX Properties (required for TableView)
    public StringProperty nameProperty() { return name; }
    public StringProperty currentVersionProperty() { return currentVersion; }
    public StringProperty lastDownloadedVersionProperty() { return lastDownloadedVersion; }
    public StringProperty lastCheckDateProperty() { return lastCheckDate; }

    // Standard Getters/Setters
    public String getName() { return name.get(); }
    public void setUrl(String url) { this.url = url; }
    public String getUrl() { return url; }
    public void setCssSelector(String selector) { this.cssSelector = selector; }
    public String getCssSelector() { return cssSelector; }
    public void setVersionRegex(String regex) { this.versionRegex = regex; }
    public String getVersionRegex() { return versionRegex; }
    public void setCurrentVersion(String version) { this.currentVersion.set(version); }
    public void setLastDownloadedVersion(String version) { this.lastDownloadedVersion.set(version); }
}