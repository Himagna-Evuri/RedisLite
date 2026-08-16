package com.redislite;
import com.redislite.storage.KeyValueStore;
import java.util.*;
import com.redislite.parser.CommandParser;
import com.redislite.parser.CommandHandler;
public class Main {

    public static void main(String[] args) {

        KeyValueStore store = new KeyValueStore();
        CommandParser parser = new CommandParser();
        CommandHandler handler = new CommandHandler(store);

        Scanner scanner = new Scanner(System.in);

        while (true) {

            String command = scanner.nextLine();

            if (command.equalsIgnoreCase("exit")) {
                break;
            }

            String[] parts = parser.parse(command);

            switch (parts[0]) {

                case "SET":
                    System.out.println(handler.handleSet(parts));
                    break;

                case "GET":
                    System.out.println(handler.handleGet(parts));
                    break;

                case "DEL":
                    System.out.println(handler.handleDelete(parts));
                    break;

                case "EXISTS":
                    System.out.println(handler.handleExists(parts));
                    break;

                case "TTL":
                    System.out.println(handler.handleTtl(parts));
                    break;

                case "KEYS":
                    System.out.println(handler.handleKeys(parts));
                    break;

                case "FLUSHDB":
                    System.out.println(handler.handleFlushDb(parts));
                    break;

                default:
                    System.out.println("Unknown command.");
                    break;
            }
        }

        scanner.close();
        System.out.println("RedisLite Closed.");
    }
}
