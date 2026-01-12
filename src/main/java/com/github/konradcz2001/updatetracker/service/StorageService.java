package com.github.konradcz2001.updatetracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.konradcz2001.updatetracker.TrackedProgram;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class StorageService {
    private static final String FILE_NAME = "programs.json";
    private static final String APP_FOLDER_NAME = "UpdateTracker";

    private final ObjectMapper objectMapper;

    public StorageService() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private File getDataFile() {
        String appData = System.getenv("APPDATA");
        Path folderPath;

        if (appData != null) {
            // Windows: C:\Users\USERNAME\AppData\Roaming\UpdateTracker
            folderPath = Paths.get(appData, APP_FOLDER_NAME);
        } else {
            // Linux/Mac: /home/USERNAME/.UpdateTracker
            folderPath = Paths.get(System.getProperty("user.home"), "." + APP_FOLDER_NAME);
        }

        if (!Files.exists(folderPath)) {
            try {
                Files.createDirectories(folderPath);
            } catch (IOException e) {
                e.printStackTrace();
                return new File(FILE_NAME);
            }
        }

        return folderPath.resolve(FILE_NAME).toFile();
    }

    public void saveData(List<TrackedProgram> programs) {
        try {
            objectMapper.writeValue(getDataFile(), programs);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<TrackedProgram> loadData() {
        File file = getDataFile();
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(file, new TypeReference<List<TrackedProgram>>() {});
        } catch (IOException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}