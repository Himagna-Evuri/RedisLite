package com.redislite.model;

public class Entry {

    private final String value;
    private final Long expiryTime;

    public Entry(String value, Long expiryTime) {
        this.value = value;
        this.expiryTime = expiryTime;
    }

    public String getValue() {
        return value;
    }

    public Long getExpiryTime() {
        return expiryTime;
    }


}