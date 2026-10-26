package com.redislite.storage;

import com.redislite.model.Entry;
import com.redislite.persistence.PersistenceManager;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import com.redislite.ttl.ExpiryCleaner;

public class KeyValueStore {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Object storeLock = new Object();
    private final PersistenceManager persistenceManager =
            new PersistenceManager(storeLock);

    public KeyValueStore() {

        persistenceManager.createDatabaseFile();
        persistenceManager.loadFromDisk(store);

        Thread cleanerThread =
                new Thread(
                        new ExpiryCleaner(
                                store,
                                persistenceManager,
                                storeLock));

        cleanerThread.setDaemon(true);
        cleanerThread.start();
    }

    private Entry getValidEntry(String key) {

        while (true) {

            Entry entry = store.get(key);

            if (entry == null) {
                return null;
            }

            Long expiryTime = entry.getExpiryTime();

            if (expiryTime == null ||
                    System.currentTimeMillis() < expiryTime) {
                return entry;
            }

            // Remove only if the key still maps to this entry.
            synchronized (storeLock) {

                // Check again because another thread may have changed the entry.
                Entry currentEntry = store.get(key);

                if (currentEntry == null) {
                    return null;
                }

                if (currentEntry != entry) {
                    continue;
                }

                boolean removed = store.remove(key, entry);

                if (removed) {
                    persistenceManager.saveToDisk(store);
                    return null;
                }
            }

            // Another thread changed the entry.
            // Loop again and check the latest value.
        }
    }


    public void set(String key,
                    String value,
                    long ttlSeconds) {

        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException(
                    "TTL must be greater than 0.");
        }

        Long expiryTime =
                System.currentTimeMillis()
                        + ttlSeconds * 1000L;

        Entry entry =
                new Entry(value, expiryTime);

        synchronized (storeLock) {
            store.put(key, entry);
            persistenceManager.saveToDisk(store);
        }
    }

    public void set(String key,
                    String value) {

        Entry entry =
                new Entry(value, null);

        synchronized (storeLock) {
            store.put(key, entry);
            persistenceManager.saveToDisk(store);
        }
    }

    public String get(String key) {

        Entry entry =
                getValidEntry(key);

        if (entry == null) {
            return null;
        }

        return entry.getValue();
    }
    public boolean delete(String key) {

        synchronized (storeLock) {
            Entry removed = store.remove(key);

            if (removed != null) {
                persistenceManager.saveToDisk(store);
            }

            return removed != null;
        }
    }
    public boolean exists(String key) {

        return getValidEntry(key)
                != null;
    }
    public long ttl(String key) {

        Entry entry = getValidEntry(key);

        if (entry == null) {
            return -2;
        }

        Long expiryTime = entry.getExpiryTime();

        if (expiryTime == null) {
            return -1;
        }

        long remaining =
                (expiryTime - System.currentTimeMillis()) / 1000;

        return remaining;
    }
    public Set<String> keys() {

        Set<String> keys = new HashSet<>();

        for (String key : store.keySet()) {

            if (getValidEntry(key) != null) {
                keys.add(key);
            }
        }

        return keys;
    }
    public void clear() {

        synchronized (storeLock) {
            store.clear();
            persistenceManager.saveToDisk(store);
        }
    }
    public int size() {

        int count = 0;

        for (String key : store.keySet()) {

            if (getValidEntry(key) != null) {
                count++;
            }
        }

        return count;
    }

}