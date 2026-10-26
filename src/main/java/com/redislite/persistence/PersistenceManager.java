package com.redislite.persistence;

import com.redislite.model.Entry;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;

public class PersistenceManager {

    private static final String FILE_NAME = "dump.rdb";
    private final Object storeLock;
    public PersistenceManager(Object storeLock) {
        this.storeLock = storeLock;
    }

    public void createDatabaseFile() {

        File file = new File(FILE_NAME);

        if (!file.exists()) {

            try {
                file.createNewFile();
                System.out.println("Database file created successfully.");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public synchronized void saveToDisk(Map<String, Entry> store) {

        Map<String, Entry> snapshot;

        synchronized (storeLock) {
            snapshot = new java.util.HashMap<>(store);
        }
        String tempFileName = FILE_NAME + ".tmp";


        try (FileWriter writer =
                     new FileWriter(tempFileName)) {

            for (Map.Entry<String, Entry> entry : snapshot.entrySet()) {

                Entry storedEntry = entry.getValue();

                String key =
                        entry.getKey().replace("\\", "\\\\")
                                .replace(",", "\\,");

                String value =
                        storedEntry.getValue().replace("\\", "\\\\")
                                .replace(",", "\\,");

                String line =
                        key + "," +
                                value + "," +
                                storedEntry.getExpiryTime();

                writer.write(line);
                writer.write(System.lineSeparator());
            }

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        try {
            Path tempPath = Path.of(tempFileName);
            Path databasePath = Path.of(FILE_NAME);

            Files.move(
                    tempPath,
                    databasePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void loadFromDisk(Map<String, Entry> store) {

        try (BufferedReader br =
                     new BufferedReader(new FileReader(FILE_NAME))) {

            String line;

            while ((line = br.readLine()) != null) {

                String[] parts = splitRecord(line);

                if (parts.length != 3) {
                    System.out.println(
                            "Invalid database entry: " + line);
                    continue;
                }

                if (parts[0].isEmpty()) {
                    System.out.println(
                            "Invalid key: " + line);
                    continue;
                }

                String key = parts[0];
                String value = parts[1];

                Long expiryTime;

                if (parts[2].equals("null")) {

                    expiryTime = null;

                } else {

                    try {
                        expiryTime = Long.parseLong(parts[2]);

                    } catch (NumberFormatException e) {

                        System.out.println(
                                "Invalid expiry time: " + line);
                        continue;
                    }
                }

                Entry entry =
                        new Entry(value, expiryTime);

                store.put(key, entry);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private String[] splitRecord(String line) {

        java.util.List<String> parts =
                new java.util.ArrayList<>();

        StringBuilder current =
                new StringBuilder();

        boolean escaped = false;

        for (char ch : line.toCharArray()) {

            if (escaped) {
                current.append(ch);
                escaped = false;

            } else if (ch == '\\') {
                escaped = true;

            } else if (ch == ',') {
                parts.add(current.toString());
                current.setLength(0);

            } else {
                current.append(ch);
            }
        }

        if (escaped) {
            current.append('\\');
        }

        parts.add(current.toString());

        return parts.toArray(new String[0]);
    }
}