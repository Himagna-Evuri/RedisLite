package com.redislite.parser;

import com.redislite.storage.KeyValueStore;
import java.util.Set;

public class CommandHandler {

    private final KeyValueStore store;

    public CommandHandler(KeyValueStore store) {
        this.store = store;
    }
    public String handleGet(String[] parts) {

        if (parts.length != 2) {
            return "Usage: GET <key>";
        }

        String value = store.get(parts[1]);

        if (value == null) {
            return "Key not found";
        }

        return value;
    }

    public String handleSet(String[] parts) {

        if (parts.length != 3 && parts.length != 5) {
            return "Usage: SET <key> <value> [EX <seconds>]";
        }

        if (parts.length == 5 && !"EX".equals(parts[3])) {
            return "Usage: SET <key> <value> [EX <seconds>]";
        }

        if (parts.length == 3) {
            store.set(parts[1], parts[2]);
            return "OK";
        }

        try {
            long ttl = Long.parseLong(parts[4]);

            if (ttl <= 0) {
                return "TTL must be greater than 0.";
            }

            store.set(parts[1], parts[2], ttl);

            return "OK";

        } catch (NumberFormatException e) {
            return "TTL must be a valid number.";
        }
    }
    public String handleDelete(String[] parts) {

        if (parts.length != 2) {
            return "Usage: DEL <key>";
        }

        boolean deleted = store.delete(parts[1]);

        if (deleted) {
            return "Deleted successfully";
        }

        return "Key not found";
    }
    public String handleExists(String[] parts) {

        if (parts.length != 2) {
            return "Usage: EXISTS <key>";
        }

        boolean exists = store.exists(parts[1]);

        if (exists) {
            return "Exists";
        }

        return "Key not found";
    }
    public String handleTtl(String[] parts) {

        if (parts.length != 2) {
            return "Usage: TTL <key>";
        }

        long ttl = store.ttl(parts[1]);

        if (ttl == -2) {
            return "Key not found";
        }

        return String.valueOf(ttl);
    }
    public String handleKeys(String[] parts) {

        if (parts.length != 1) {
            return "Usage: KEYS";
        }

        Set<String> keys = store.keys();

        if (keys.isEmpty()) {
            return "(empty)";
        }

        StringBuilder result = new StringBuilder();

        for (String key : keys) {
            result.append(key).append(System.lineSeparator());
        }

        return result.toString().trim();
    }

    public String handleFlushDb(String[] parts) {

        if (parts.length != 1) {
            return "Usage: FLUSHDB";
        }

        store.clear();

        return "OK";
    }
}