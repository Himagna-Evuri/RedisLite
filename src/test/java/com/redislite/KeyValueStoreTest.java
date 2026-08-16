package com.redislite;

import com.redislite.storage.KeyValueStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

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
}