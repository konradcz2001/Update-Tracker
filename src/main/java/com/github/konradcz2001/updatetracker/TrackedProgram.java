package com.github.konradcz2001.updatetracker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDate;

public class TrackedProgram {
    private final StringProperty name;
    private final StringProperty currentVersion;
    private final StringProperty lastDownloadedVersion;
    private final StringProperty lastCheckDate;

    private String url;
    private String cssSelector;
    private String versionRegex;
    private String downloadSelector = "";

    // Default constructor for Jackson
    public TrackedProgram() {
        this.name = new SimpleStringProperty("");
        this.currentVersion = new SimpleStringProperty("N/A");
        this.lastDownloadedVersion = new SimpleStringProperty("N/A");
        this.lastCheckDate = new SimpleStringProperty(LocalDate.now().toString());
        this.url = "";
        this.cssSelector = "";
        this.versionRegex = "";
    }

    public TrackedProgram(String name) {
        this.name = new SimpleStringProperty(name);
        this.currentVersion = new SimpleStringProperty("N/A");
        this.lastDownloadedVersion = new SimpleStringProperty("N/A");
        this.lastCheckDate = new SimpleStringProperty(LocalDate.now().toString());
        this.url = "";
        this.cssSelector = "";
        this.versionRegex = "";
    }

    // --- Getters & Setters for Jackson Serialization ---

    public String getName() { return name.get(); }
    public void setName(String name) { this.name.set(name); }

    public String getCurrentVersion() { return currentVersion.get(); }
    public void setCurrentVersion(String version) { this.currentVersion.set(version); }

    public String getLastDownloadedVersion() { return lastDownloadedVersion.get(); }
    public void setLastDownloadedVersion(String version) { this.lastDownloadedVersion.set(version); }

    public String getLastCheckDate() { return lastCheckDate.get(); }
    public void setLastCheckDate(String date) { this.lastCheckDate.set(date); }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getCssSelector() { return cssSelector; }
    public void setCssSelector(String cssSelector) { this.cssSelector = cssSelector; }

    public String getVersionRegex() { return versionRegex; }
    public void setVersionRegex(String versionRegex) { this.versionRegex = versionRegex; }

    public String getDownloadSelector() { return downloadSelector; }
    public void setDownloadSelector(String downloadSelector) { this.downloadSelector = downloadSelector; }

    // --- JavaFX Properties (Ignored by Jackson) ---

    @JsonIgnore public StringProperty nameProperty() { return name; }
    @JsonIgnore public StringProperty currentVersionProperty() { return currentVersion; }
    @JsonIgnore public StringProperty lastDownloadedVersionProperty() { return lastDownloadedVersion; }
    @JsonIgnore public StringProperty lastCheckDateProperty() { return lastCheckDate; }
}