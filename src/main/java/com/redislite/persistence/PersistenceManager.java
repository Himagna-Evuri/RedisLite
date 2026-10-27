package com.redislite.persistence;

import com.redislite.model.Entry;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PersistenceManager {

    private static final String FILE_NAME = "dump.rdb";

    private final Object storeLock;
    private final Path databasePath;

    public PersistenceManager(Object storeLock) {
        this(storeLock, Path.of(FILE_NAME));
    }

    public PersistenceManager(Object storeLock, Path databasePath) {
        if (storeLock == null) {
            throw new IllegalArgumentException("Store lock cannot be null.");
        }
        if (databasePath == null) {
            throw new IllegalArgumentException("Database path cannot be null.");
        }

        this.storeLock = storeLock;
        this.databasePath = databasePath;
    }

    public void createDatabaseFile() {
        File file = databasePath.toFile();

        if (file.exists()) {
            if (!file.isFile()) {
                throw new IllegalStateException(
                        "Database path is not a file: " + databasePath
                );
            }
            return;
        }

        try {
            Path parent = databasePath.toAbsolutePath().getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.createFile(databasePath);
            System.out.println("Database file created successfully.");
        } catch (java.nio.file.FileAlreadyExistsException e) {
            // Another process may have created the file concurrently.
            if (!Files.isRegularFile(databasePath)) {
                throw new IllegalStateException(
                        "Database path is not a regular file: " + databasePath,
                        e
                );
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to create database file: " + databasePath,
                    e
            );
        }
    }
    public boolean saveToDisk(Map<String, Entry> store) {
        synchronized (storeLock) {
            Map<String, Entry> snapshot = new HashMap<>(store);

            Path tempPath = Path.of(databasePath.toString() + ".tmp");

            try {
                try (FileWriter writer = new FileWriter(tempPath.toFile())) {
                    for (Map.Entry<String, Entry> entry : snapshot.entrySet()) {
                        Entry storedEntry = entry.getValue();

                        String key = entry.getKey()
                                .replace("\\", "\\\\")
                                .replace(",", "\\,");

                        String value = storedEntry.getValue()
                                .replace("\\", "\\\\")
                                .replace(",", "\\,");

                        String line = key + "," + value + ","
                                + storedEntry.getExpiryTime();

                        writer.write(line);
                        writer.write(System.lineSeparator());
                    }
                }

                try {
                    Files.move(
                            tempPath,
                            databasePath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE
                    );
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(
                            tempPath,
                            databasePath,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }

                return true;
            } catch (IOException e) {
                System.err.println(
                        "Failed to save database to disk: " + databasePath
                );
                e.printStackTrace();
                return false;
            }
        }
    }
    public void loadFromDisk(Map<String, Entry> store) {
        try (BufferedReader br = new BufferedReader(
                new FileReader(databasePath.toFile()))) {

            String line;

            while ((line = br.readLine()) != null) {
                String[] parts = splitRecord(line);

                if (parts.length != 3) {
                    System.out.println("Invalid database entry: " + line);
                    continue;
                }

                if (parts[0].isEmpty()) {
                    System.out.println("Invalid key: " + line);
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
                        System.out.println("Invalid expiry time: " + line);
                        continue;
                    }
                }

                // Do not restore keys that have already expired.
                if (expiryTime != null
                        && expiryTime <= System.currentTimeMillis()) {
                    continue;
                }

                store.put(key, new Entry(value, expiryTime));
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load database from disk: " + databasePath,
                    e
            );
        }
    }

    private String[] splitRecord(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
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