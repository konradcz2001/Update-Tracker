package com.github.konradcz2001.updatetracker.service;

import com.github.konradcz2001.updatetracker.TrackedProgram;
import javafx.application.Platform;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.ResourceBundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanServiceTest {

    @Mock
    private ScraperService scraperService;
    @Mock
    private ConfigService configService;
    @Mock
    private ResourceBundle resourceBundle;

    private ScanService scanService;

    // Initialize JavaFX Toolkit once for all tests to handle Platform.runLater
    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit already initialized
        }
    }

    @BeforeEach
    void setUp() {
        scanService = new ScanService(scraperService, resourceBundle, configService);
    }

    @Test
    void testPerformScan_DetectsUpdate() throws IOException, InterruptedException {
        // Given
        TrackedProgram program = new TrackedProgram("TestApp");
        program.setUrl("http://example.com");
        program.setCurrentVersion("1.0");
        program.setLastDownloadedVersion("1.0");
        program.setVersionRegex("v([0-9.]+)");

        // When scraper is called, return text containing new version
        when(scraperService.fetchText(program)).thenReturn("Latest release: v2.0");

        // Execute logic using the overload
        boolean success = scanService.checkProgramUpdate(program);

        // Wait for Platform.runLater to finish
        waitForFxEvents();

        // Then
        Assertions.assertTrue(success, "Scan should be successful");
        Assertions.assertEquals("2.0", program.getCurrentVersion(), "Version should be updated to 2.0");
        verify(scraperService, times(1)).fetchText(program);
    }

    @Test
    void testPerformScan_NoUpdate() throws IOException, InterruptedException {
        TrackedProgram program = new TrackedProgram("TestApp");
        program.setUrl("http://example.com"); // FIX: URL is required for the service to proceed
        program.setCurrentVersion("1.0");
        program.setVersionRegex("v([0-9.]+)");

        when(scraperService.fetchText(program)).thenReturn("Current: v1.0");

        boolean success = scanService.checkProgramUpdate(program);

        waitForFxEvents();

        // The method returns TRUE if the scan was successful (regex matched), even if version didn't change.
        Assertions.assertTrue(success, "Scan should be successful even if no update found");

        // Key verification: Version should remain exactly the same
        Assertions.assertEquals("1.0", program.getCurrentVersion(), "Version should remain 1.0");
    }

    private void waitForFxEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        latch.await(2, TimeUnit.SECONDS);
    }
}