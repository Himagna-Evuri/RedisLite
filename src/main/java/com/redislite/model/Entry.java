package com.redislite.model;

public class Entry {

    private String value;
    private Long expiryTime;

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

    public void setValue(String value) {
        this.value = value;
    }

    public void setExpiryTime(Long expiryTime) {
        this.expiryTime = expiryTime;
    }
}