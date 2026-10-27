package com.redislite;

import com.redislite.model.Entry;
import com.redislite.persistence.PersistenceManager;
import com.redislite.storage.KeyValueStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class KeyValueStoreTest {

    @Test
    void setAndGetShouldReturnValue() throws IOException {
        KeyValueStore store = createTestStore();

        store.set("name", "Himagna");

        Assertions.assertEquals("Himagna", store.get("name"));
    }

    @Test
    void deleteShouldRemoveKey() throws IOException {
        KeyValueStore store = createTestStore();

        store.set("name", "Himagna");

        boolean deleted = store.delete("name");

        Assertions.assertTrue(deleted);
        Assertions.assertNull(store.get("name"));
    }

    @Test
    void existsShouldReturnCorrectResult() throws IOException {
        KeyValueStore store = createTestStore();

        store.set("name", "Himagna");

        Assertions.assertTrue(store.exists("name"));

        store.delete("name");

        Assertions.assertFalse(store.exists("name"));
    }

    @Test
    void ttlShouldDecreaseAndExpire() throws InterruptedException, IOException {
        KeyValueStore store = createTestStore();

        store.set("temporary", "hello", 2);

        long ttl = store.ttl("temporary");
        Assertions.assertTrue(ttl >= 0 && ttl <= 2);

        Thread.sleep(3000);

        Assertions.assertEquals(-2, store.ttl("temporary"));
        Assertions.assertNull(store.get("temporary"));
    }

    @Test
    void keysShouldReturnOnlyActiveKeys() throws IOException {
        KeyValueStore store = createTestStore();

        store.set("name", "Himagna");
        store.set("city", "Hyderabad");
        store.set("temporary", "hello", 1);

        Assertions.assertTrue(store.keys().contains("name"));
        Assertions.assertTrue(store.keys().contains("city"));

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test interrupted", e);
        }

        Assertions.assertFalse(store.keys().contains("temporary"));
    }

    @Test
    void flushDbShouldRemoveAllKeys() throws IOException {
        KeyValueStore store = createTestStore();

        store.clear();
        store.set("name", "Himagna");
        store.set("city", "Hyderabad");
        store.set("age", "21");

        Assertions.assertEquals(3, store.size());

        store.clear();

        Assertions.assertEquals(0, store.size());
        Assertions.assertNull(store.get("name"));
        Assertions.assertNull(store.get("city"));
        Assertions.assertNull(store.get("age"));
    }

    @Test
    void dataShouldPersistAfterRestart() throws IOException {
        Path databaseFile = createTestDatabase();

        KeyValueStore store1 = new KeyValueStore(
                lock -> new PersistenceManager(lock, databaseFile),
                false
        );

        store1.set("name", "Himagna");
        store1.set("city", "Hyderabad");

        KeyValueStore store2 = new KeyValueStore(
                lock -> new PersistenceManager(lock, databaseFile),
                false
        );

        Assertions.assertEquals("Himagna", store2.get("name"));
        Assertions.assertEquals("Hyderabad", store2.get("city"));

        store2.clear();
    }

    @Test
    void expiredKeyShouldNotLoadAfterRestart() throws Exception {
        Path databaseFile = createTestDatabase();

        KeyValueStore store1 = new KeyValueStore(
                lock -> new PersistenceManager(lock, databaseFile),
                false
        );

        store1.set("expired-key", "old-value", 1);

        // Simulate a crash before the expired-key removal is saved.
        Files.writeString(
                databaseFile,
                "expired-key,old-value,"
                        + (System.currentTimeMillis() - 1000)
                        + System.lineSeparator()
        );

        KeyValueStore store2 = new KeyValueStore(
                lock -> new PersistenceManager(lock, databaseFile),
                false
        );

        Assertions.assertNull(store2.get("expired-key"));
        Assertions.assertFalse(store2.exists("expired-key"));

        store2.clear();
    }

    @Test
    void concurrentAccessShouldCompleteSuccessfully()
            throws InterruptedException, IOException {
        KeyValueStore store = createTestStore();
        store.clear();

        int threadCount = 2;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threadCount);

        Thread writer = new Thread(() -> {
            try {
                startSignal.await();
                for (int i = 0; i < 100; i++) {
                    store.set("shared", "value" + i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneSignal.countDown();
            }
        });

        Thread reader = new Thread(() -> {
            try {
                startSignal.await();
                for (int i = 0; i < 100; i++) {
                    store.get("shared");
                    store.exists("shared");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneSignal.countDown();
            }
        });

        writer.start();
        reader.start();

        startSignal.countDown();
        doneSignal.await();

        Assertions.assertEquals("value99", store.get("shared"));

        store.clear();
    }

    @Test
    void concurrentWritesShouldPersistAllKeys()
            throws InterruptedException, IOException {
        Path databaseFile = createTestDatabase();

        KeyValueStore store = new KeyValueStore(
                lock -> new PersistenceManager(lock, databaseFile),
                false
        );

        int threadCount = 4;
        int keysPerThread = 25;
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(threadCount);
        List<Thread> workers = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;

            Thread worker = new Thread(() -> {
                try {
                    startSignal.await();

                    for (int i = 0; i < keysPerThread; i++) {
                        String key = "thread" + threadId + "_key" + i;
                        String value = "value" + threadId + "_" + i;
                        store.set(key, value);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown();
                }
            });

            workers.add(worker);
            worker.start();
        }

        startSignal.countDown();
        doneSignal.await();

        Assertions.assertEquals(threadCount * keysPerThread, store.size());

        KeyValueStore recoveredStore = new KeyValueStore(
                lock -> new PersistenceManager(lock, databaseFile),
                false
        );

        for (int t = 0; t < threadCount; t++) {
            for (int i = 0; i < keysPerThread; i++) {
                String key = "thread" + t + "_key" + i;
                String expectedValue = "value" + t + "_" + i;

                Assertions.assertEquals(
                        expectedValue,
                        recoveredStore.get(key),
                        "Incorrect or missing value for key: " + key
                );
            }
        }

        recoveredStore.clear();
    }

    @Test
    void setShouldRollbackWhenPersistenceFails() throws IOException {
        Path databaseFile = createTestDatabase();

        KeyValueStore store = new KeyValueStore(
                lock -> new FailingPersistenceManager(lock, databaseFile),
                false
        );

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> store.set("name", "Himagna")
        );

        Assertions.assertNull(store.get("name"));
    }

    @Test
    void setShouldRestorePreviousValueWhenPersistenceFails() throws IOException {
        TogglePersistenceManager[] manager = new TogglePersistenceManager[1];
        Path databaseFile = createTestDatabase();

        KeyValueStore store = new KeyValueStore(lock -> {
            manager[0] = new TogglePersistenceManager(lock, databaseFile);
            return manager[0];
        }, false);

        store.set("rollback-test", "Original");
        manager[0].setFailSaves(true);

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> store.set("rollback-test", "Updated")
        );

        Assertions.assertEquals("Original", store.get("rollback-test"));
    }

    @Test
    void deleteShouldRestoreKeyWhenPersistenceFails() throws IOException {
        TogglePersistenceManager[] manager = new TogglePersistenceManager[1];
        Path databaseFile = createTestDatabase();

        KeyValueStore store = new KeyValueStore(lock -> {
            manager[0] = new TogglePersistenceManager(lock, databaseFile);
            return manager[0];
        }, false);

        store.set("delete-test", "Original");
        manager[0].setFailSaves(true);

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> store.delete("delete-test")
        );

        Assertions.assertEquals("Original", store.get("delete-test"));
    }

    @Test
    void expiredKeyShouldStayRemovedWhenPersistenceFails() throws IOException {
        TogglePersistenceManager[] manager = new TogglePersistenceManager[1];
        Path databaseFile = createTestDatabase();

        KeyValueStore store = new KeyValueStore(lock -> {
            manager[0] = new TogglePersistenceManager(lock, databaseFile);
            return manager[0];
        }, false);

        store.set("temporary", "value", 1);
        manager[0].setFailSaves(true);

        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test interrupted", e);
        }

        Assertions.assertNull(store.get("temporary"));
        Assertions.assertFalse(store.exists("temporary"));
    }

    @Test
    void clearShouldRestoreAllKeysWhenPersistenceFails() throws IOException {
        TogglePersistenceManager[] manager = new TogglePersistenceManager[1];
        Path databaseFile = createTestDatabase();

        KeyValueStore store = new KeyValueStore(lock -> {
            manager[0] = new TogglePersistenceManager(lock, databaseFile);
            return manager[0];
        }, false);

        store.set("name", "Himagna");
        store.set("city", "Hyderabad");

        manager[0].setFailSaves(true);

        Assertions.assertThrows(IllegalStateException.class, store::clear);

        Assertions.assertEquals(2, store.size());
        Assertions.assertEquals("Himagna", store.get("name"));
        Assertions.assertEquals("Hyderabad", store.get("city"));
    }

    @Test
    void loadShouldThrowWhenDatabaseFileCannotBeRead() throws Exception {
        Path directory = Files.createTempDirectory("redislite-test");
        Path databaseFile = directory.resolve("dump.rdb");

        try {
            Files.createDirectory(databaseFile);

            PersistenceManager manager =
                    new PersistenceManager(new Object(), databaseFile);

            Assertions.assertThrows(
                    IllegalStateException.class,
                    () -> manager.loadFromDisk(new java.util.HashMap<>())
            );
        } finally {
            Files.deleteIfExists(databaseFile);
            Files.deleteIfExists(directory);
        }
    }

    private Path createTestDatabase() throws IOException {
        Path tempDir = Files.createTempDirectory("redislite-test-");
        return tempDir.resolve("dump.rdb");
    }

    private KeyValueStore createTestStore() throws IOException {
        Path databaseFile = createTestDatabase();

        return new KeyValueStore(
                lock -> new PersistenceManager(lock, databaseFile),
                false
        );
    }

    private static class FailingPersistenceManager extends PersistenceManager {
        FailingPersistenceManager(Object storeLock, Path databaseFile) {
            super(storeLock, databaseFile);
        }

        @Override
        public boolean saveToDisk(Map<String, Entry> store) {
            return false;
        }
    }

    private static class TogglePersistenceManager extends PersistenceManager {
        private boolean failSaves;

        TogglePersistenceManager(Object storeLock, Path databaseFile) {
            super(storeLock, databaseFile);
        }

        void setFailSaves(boolean failSaves) {
            this.failSaves = failSaves;
        }

        @Override
        public boolean saveToDisk(Map<String, Entry> store) {
            if (failSaves) {
                return false;
            }
            return super.saveToDisk(store);
        }
    }
}
