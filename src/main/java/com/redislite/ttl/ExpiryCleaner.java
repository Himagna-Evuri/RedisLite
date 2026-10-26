package com.redislite.ttl;

import com.redislite.model.Entry;
import com.redislite.persistence.PersistenceManager;

import java.util.Iterator;
import java.util.Map;

public class ExpiryCleaner implements Runnable {

    private final Map<String, Entry> store;
    private final PersistenceManager persistenceManager;

    public ExpiryCleaner(
            Map<String, Entry> store,
            PersistenceManager persistenceManager) {

        this.store = store;
        this.persistenceManager = persistenceManager;
    }

    private void removeExpiredKeys() {

        boolean removedAny = false;

        Iterator<Map.Entry<String, Entry>> iterator =
                store.entrySet().iterator();

        while (iterator.hasNext()) {

            Map.Entry<String, Entry> current =
                    iterator.next();

            Entry entry = current.getValue();

            if (entry.getExpiryTime() != null &&
                    entry.getExpiryTime()
                            <= System.currentTimeMillis()) {

                System.out.println(
                        "Expired key removed: "
                                + current.getKey());

                boolean removed = store.remove(current.getKey(), entry);

                if (removed) {
                    removedAny = true;
                }

                removedAny = true;
            }
        }

        if (removedAny) {
            persistenceManager.saveToDisk(store);
        }
    }

    @Override
    public void run() {

        while (true) {

            removeExpiredKeys();

            try {
                Thread.sleep(1000);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
