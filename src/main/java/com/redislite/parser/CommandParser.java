package com.redislite.parser;

public class CommandParser {

    public String[] parse(String command) {

        String[] parts = command.trim().split("\\s+");

        parts[0] = parts[0].toUpperCase();

        return parts;
    }
}
