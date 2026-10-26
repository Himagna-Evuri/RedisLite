package com.redislite;

import com.redislite.storage.KeyValueStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import java.util.concurrent.CountDownLatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.redislite.model.Entry;
import com.redislite.persistence.PersistenceManager;

public class KeyValueStoreTest {

    @Test
    void setAndGetShouldReturnValue() {

        KeyValueStore store = new KeyValueStore();

        store.set("name", "Himagna");

        Assertions.assertEquals("Himagna", store.get("name"));
    }

    @Test
    void deleteShouldRemoveKey() {

        KeyValueStore store = new KeyValueStore();

        store.set("name", "Himagna");

        boolean deleted = store.delete("name");

        Assertions.assertTrue(deleted);
        Assertions.assertNull(store.get("name"));
    }
    @Test
    void existsShouldReturnCorrectResult() {

        KeyValueStore store = new KeyValueStore();

        store.set("name", "Himagna");

        Assertions.assertTrue(store.exists("name"));

        store.delete("name");

        Assertions.assertFalse(store.exists("name"));
    }
    @Test
    void ttlShouldDecreaseAndExpire() throws InterruptedException {

        KeyValueStore store = new KeyValueStore();

        store.set("temporary", "hello", 2);

        long ttl = store.ttl("temporary");

        Assertions.assertTrue(ttl >= 0 && ttl <= 2);

        Thread.sleep(3000);

        Assertions.assertEquals(-2, store.ttl("temporary"));
        Assertions.assertNull(store.get("temporary"));
    }
    @Test
    void keysShouldReturnOnlyActiveKeys() {

        KeyValueStore store = new KeyValueStore();

        store.set("name", "Himagna");
        store.set("city", "Hyderabad");

        store.set("temporary", "hello", 1);

        Assertions.assertTrue(store.keys().contains("name"));
        Assertions.assertTrue(store.keys().contains("city"));

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Assertions.assertFalse(store.keys().contains("temporary"));
    }
    @Test

    void flushDbShouldRemoveAllKeys() {

        KeyValueStore store = new KeyValueStore();

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
    void dataShouldPersistAfterRestart() {

        KeyValueStore store1 = new KeyValueStore();

        store1.clear();

        store1.set("name", "Himagna");
        store1.set("city", "Hyderabad");

        KeyValueStore store2 = new KeyValueStore();

        Assertions.assertEquals("Himagna", store2.get("name"));
        Assertions.assertEquals("Hyderabad", store2.get("city"));

        store2.clear();
    }

    @Test
    void concurrentAccessShouldCompleteSuccessfully()
            throws InterruptedException {

        KeyValueStore store = new KeyValueStore();
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
    void concurrentWritesShouldPersistAllKeys() throws InterruptedException {

        KeyValueStore store = new KeyValueStore();
        store.clear();

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

        Assertions.assertEquals(
                threadCount * keysPerThread,
                store.size());

        KeyValueStore recoveredStore = new KeyValueStore();

        for (int t = 0; t < threadCount; t++) {
            for (int i = 0; i < keysPerThread; i++) {

                String key = "thread" + t + "_key" + i;
                String expectedValue = "value" + t + "_" + i;

                Assertions.assertEquals(
                        expectedValue,
                        recoveredStore.get(key),
                        "Incorrect or missing value for key: " + key);
            }
        }

        recoveredStore.clear();
    }
    @Test
    void setShouldRollbackWhenPersistenceFails() {

        KeyValueStore store = new KeyValueStore(
                FailingPersistenceManager::new);

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> store.set("name", "Himagna"));

        Assertions.assertNull(store.get("name"));
    }
    @Test
    void setShouldRestorePreviousValueWhenPersistenceFails() {

        TogglePersistenceManager[] manager =
                new TogglePersistenceManager[1];

        KeyValueStore store = new KeyValueStore(lock -> {
            manager[0] = new TogglePersistenceManager(lock);
            return manager[0];
        });

        store.set("rollback-test", "Original");

        manager[0].setFailSaves(true);

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> store.set("rollback-test", "Updated"));

        Assertions.assertEquals(
                "Original",
                store.get("rollback-test"));
    }
    @Test
    void deleteShouldRestoreKeyWhenPersistenceFails() {

        TogglePersistenceManager[] manager =
                new TogglePersistenceManager[1];

        KeyValueStore store = new KeyValueStore(lock -> {
            manager[0] = new TogglePersistenceManager(lock);
            return manager[0];
        });

        store.set("delete-test", "Original");

        manager[0].setFailSaves(true);

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> store.delete("delete-test"));

        Assertions.assertEquals(
                "Original",
                store.get("delete-test"));
    }



    @Test
    void expiredKeyShouldStayRemovedWhenPersistenceFails() {
        TogglePersistenceManager[] manager =
                new TogglePersistenceManager[1];

        KeyValueStore store = new KeyValueStore(
                lock -> {
                    manager[0] = new TogglePersistenceManager(lock);
                    return manager[0];
                },
                false
        );

        store.set("expired-test", "OldValue", 1);

        manager[0].setFailSaves(true);

        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        Assertions.assertNull(store.get("expired-test"));
        Assertions.assertFalse(store.exists("expired-test"));
    }


    @Test
    void clearShouldRestoreAllKeysWhenPersistenceFails() {

        TogglePersistenceManager[] manager =
                new TogglePersistenceManager[1];

        KeyValueStore store = new KeyValueStore(lock -> {
            manager[0] = new TogglePersistenceManager(lock);
            return manager[0];
        });

        store.set("name", "Himagna");
        store.set("city", "Hyderabad");

        manager[0].setFailSaves(true);

        Assertions.assertThrows(
                IllegalStateException.class,
                store::clear);

        Assertions.assertEquals(2, store.size());
        Assertions.assertEquals("Himagna", store.get("name"));
        Assertions.assertEquals("Hyderabad", store.get("city"));
    }

    private static class FailingPersistenceManager
            extends PersistenceManager {

        public FailingPersistenceManager(Object storeLock) {
            super(storeLock);
        }

        @Override
        public boolean saveToDisk(Map<String, Entry> store) {
            return false;
        }
    }
    private static class TogglePersistenceManager
            extends PersistenceManager {

        private boolean failSaves;

        public TogglePersistenceManager(Object storeLock) {
            super(storeLock);
        }

        public void setFailSaves(boolean failSaves) {
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