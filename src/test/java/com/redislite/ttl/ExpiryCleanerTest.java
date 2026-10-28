
package com.redislite.ttl;

import com.redislite.model.Entry;
import com.redislite.persistence.PersistenceManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExpiryCleanerTest {

    @Test
    void cleanerShouldNotRemoveReplacementValue() throws IOException {
        Path directory = Files.createTempDirectory("redislite-test-");
        Path databaseFile = directory.resolve("dump.rdb");

        Object storeLock = new Object();
        Map<String, Entry> store = new ConcurrentHashMap<>();
        PersistenceManager persistenceManager =
                new PersistenceManager(storeLock, databaseFile);

        persistenceManager.createDatabaseFile();

        // Insert an already-expired entry.
        store.put(
                "user1",
                new Entry("old-value", System.currentTimeMillis() - 1000)
        );

        // Replace it with a fresh entry before cleanup runs.
        Entry newEntry = new Entry("new-value", null);
        store.put("user1", newEntry);

        ExpiryCleaner cleaner =
                new ExpiryCleaner(store, persistenceManager, storeLock);

        cleaner.removeExpiredKeys();

        assertEquals("new-value", store.get("user1").getValue());

        Files.deleteIfExists(databaseFile);
        Files.deleteIfExists(directory);
    }
    @Test
    void expiredKeyShouldBeRemovedWhenPersistenceFails() {
        Object lock = new Object();
        Map<String, Entry> store = new ConcurrentHashMap<>();

        store.put(
                "expired",
                new Entry("old-value", System.currentTimeMillis() - 1000)
        );

        PersistenceManager failingManager = new PersistenceManager(lock) {
            @Override
            public boolean saveToDisk(Map<String, Entry> entries) {
                return false;
            }
        };

        ExpiryCleaner cleaner =
                new ExpiryCleaner(store, failingManager, lock);

        cleaner.removeExpiredKeys();

        org.junit.jupiter.api.Assertions.assertFalse(
                store.containsKey("expired")
        );
    }
    @Test
    void cleanerShouldPreserveNonExpiredKeys() throws IOException {
        Path directory = Files.createTempDirectory("redislite-test-");
        Path databaseFile = directory.resolve("dump.rdb");

        Object storeLock = new Object();
        Map<String, Entry> store = new ConcurrentHashMap<>();

        PersistenceManager persistenceManager =
                new PersistenceManager(storeLock, databaseFile);

        persistenceManager.createDatabaseFile();

        Entry permanentEntry = new Entry("keep-me", null);
        Entry futureEntry = new Entry(
                "still-valid",
                System.currentTimeMillis() + 60_000
        );

        store.put("permanent", permanentEntry);
        store.put("future", futureEntry);

        ExpiryCleaner cleaner =
                new ExpiryCleaner(store, persistenceManager, storeLock);

        cleaner.removeExpiredKeys();

        assertEquals("keep-me", store.get("permanent").getValue());
        assertEquals("still-valid", store.get("future").getValue());

        Files.deleteIfExists(databaseFile);
        Files.deleteIfExists(directory);
    }
}
