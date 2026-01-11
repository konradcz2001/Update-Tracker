package com.github.konradcz2001.updatetracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.konradcz2001.updatetracker.TrackedProgram;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StorageService {
    private static final String DATA_FILE = "tracked_programs.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void saveData(List<TrackedProgram> programs) {
        try {
            // Use ArrayList copy to avoid serialization issues with ObservableList
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(DATA_FILE), new ArrayList<>(programs));
        } catch (IOException e) {
            System.err.println("Failed to save data: " + e.getMessage());
        }
    }

    public List<TrackedProgram> loadData() {
        File file = new File(DATA_FILE);
        if (file.exists()) {
            try {
                return objectMapper.readValue(file, new TypeReference<>() {});
            } catch (IOException e) {
                System.err.println("Failed to load data: " + e.getMessage());
            }
        }
        return Collections.emptyList();
    }
}