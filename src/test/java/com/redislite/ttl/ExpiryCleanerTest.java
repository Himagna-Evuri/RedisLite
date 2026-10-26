
package com.redislite.ttl;

import com.redislite.model.Entry;
import com.redislite.persistence.PersistenceManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class ExpiryCleanerTest {

    @Test
    void expiredKeyShouldBeRemovedWhenPersistenceFails() {
        Object lock = new Object();
        Map<String, Entry> store = new HashMap<>();

        store.put(
                "expired",
                new Entry("old-value", System.currentTimeMillis() - 1000)
        );

        PersistenceManager failingManager =
                new PersistenceManager(lock) {
                    @Override
                    public boolean saveToDisk(Map<String, Entry> entries) {
                        return false;
                    }
                };

        ExpiryCleaner cleaner =
                new ExpiryCleaner(store, failingManager, lock);

        cleaner.removeExpiredKeys();

        Assertions.assertFalse(store.containsKey("expired"));
    }
}
